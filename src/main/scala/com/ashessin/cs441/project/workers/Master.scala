package com.ashessin.cs441.project.workers

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{Deadline, FiniteDuration, _}

/**
 * The master actor keep tracks of all available workers, logs them to logs
 */
object Master {

  val ResultsTopic = "results"

  def props(workTimeout: FiniteDuration): Props =
    Props(new Master(workTimeout))

  case class Ack(workId: String)

  private sealed trait WorkerStatus
  private case object Idle extends WorkerStatus
  private case class Busy(workId: String, deadline: Deadline) extends WorkerStatus
  private case class WorkerState(ref: ActorRef, status: WorkerStatus, staleWorkerDeadline: Deadline)

  private case object CleanupTick

}

class Master(workTimeout: FiniteDuration) extends Actor with Timers with ActorLogging {
  import Master._
  import WorkState._

  val considerWorkerDeadAfter: FiniteDuration =
    context.system.settings.config.getDuration("distributed-workers.consider-worker-dead-after").getSeconds.seconds
  def newStaleWorkerDeadline(): Deadline = considerWorkerDeadAfter.fromNow

  timers.startPeriodicTimer("cleanup", CleanupTick, workTimeout / 2)

  val mediator: ActorRef = DistributedPubSub(context.system).mediator

  // the set of available workers is not event sourced as it depends on the current set of workers
  private var workers = Map[String, WorkerState]()

  // workState is event sourced to be able to make sure work is processed even in case of crash
  private var workState = WorkState.empty

  // Forward chain of work ids for showing results
  private var forwardChain = mutable.HashMap[(String, String), ListBuffer[String]]()


  def receive: Receive = receiveCommand

  def receiveCommand: Receive = {
    case MasterWorkerProtocol.RegisterWorker(workerId) =>
      if (workers.contains(workerId)) {
        workers += (workerId -> workers(workerId).copy(ref = sender(), staleWorkerDeadline = newStaleWorkerDeadline()))
      } else {
        log.debug(f"[worker-$workerId] registered")
        val initialWorkerState = WorkerState(
        ref = sender(),
        status = Idle,
        staleWorkerDeadline = newStaleWorkerDeadline())
        workers += (workerId -> initialWorkerState)

        if (workState.hasWork)
          sender() ! MasterWorkerProtocol.WorkIsReady
      }

    // #graceful-remove
    case MasterWorkerProtocol.DeRegisterWorker(workerId) =>
      workers.get(workerId) match {
        case Some(WorkerState(_, Busy(workId, _), _)) =>
          // there was a workload assigned to the worker when it left
          log.debug(f"[worker-$workerId] de-registered (was busy)")
          val x = WorkerFailed(workId)
          workState = workState.updated(x)
          notifyWorkers()
        case Some(_) =>
          log.debug(f"[worker-$workerId] de-registered")
        case _ =>
      }
      workers -= workerId
    // #graceful-remove

    case MasterWorkerProtocol.WorkerRequestsWork(workerId) =>
      if (workState.hasWork) {
        workers.get(workerId) match {
          case Some(workerState @ WorkerState(_, Idle, _)) =>
            // Take a fresh work from queue
            val freshWork = workState.allWork.filter(_._2 == false).head._1
            if (!forwardChain.exists(_._1 == (freshWork.workId, workIdHash(freshWork.workId)))){
              log.debug(s"New freshWork: $freshWork sent to [worker-$workerId]")
              forwardChain((freshWork.workId, workIdHash(freshWork.workId))) = ListBuffer(workerId)
              sender() ! freshWork
            } else {
              log.debug(s"No new work for [worker-$workerId]")
            }
          case _ =>
        }
      }

    case MasterWorkerProtocol.WorkerForwardsWork(workerId, workId, forward) =>
      val k = (workId, workIdHash(workId))
      val v = forwardChain(k);
      workers.get(workerId) match {
        case Some(workerState @ WorkerState(_, _, _)) =>
          if (!forward) {
            // worker did not forward the workId
            workState = workState.updated(WorkStarted(workId))
            log.debug(f"Gave workId: $workId, workIdHash: ${workIdHash(workId)} to [worker-$workerId]")
            val newWorkerState = workerState.copy(
              status = Busy(workId, Deadline.now + workTimeout),
              staleWorkerDeadline = newStaleWorkerDeadline())
            workers += (workerId -> newWorkerState)
            log.info(f"Forward chain for workIdHash: ${workIdHash(workId)} is ${forwardChain(k)}")
          } else if (forward && workState.allWork.exists(_._1.workId == workId)){
            v += workerId
            log.debug(f"Forwarded workId: $workId, workIdHash: ${workIdHash(workId)} to [worker-$workerId]")
            forwardChain(k) = v
            workerState.ref ! workState.allWork.filter(_._1.workId == workId).head._1
            workState = workState.updated(WorkForwarded(workId))
          }
      }

    case MasterWorkerProtocol.WorkIsDone(workerId, workId, result, job, actorRef) =>
      // idempotent - redelivery from the worker may cause duplicates, so it needs to be
      if (workState.isDone(workId)) {
        // previous Ack was lost, confirm again that this is done
        sender() ! MasterWorkerProtocol.Ack(workId)
      } else if (!workState.isInProgress(workId)) {
        log.debug(f"workId: $workId, workIdHash: ${workIdHash(workId)} reported as done by [worker-$workerId]")
      } else {
        log.debug(f"workId: $workId, workIdHash: ${workIdHash(workId)} is done by [worker-$workerId]")
        changeWorkerToIdle(workerId, workId)
        val x = WorkCompleted(workId, result)
        workState = workState.updated(x)
        // Send results to to initiating FrontEnd
        mediator ! Send(s"/user/${actorRef.path.name}", WorkResult(workId, job, workIdHash(workId), result, sender), localAffinity = true)
        // Ack back to original sender
        sender ! MasterWorkerProtocol.Ack(workId)
      }

    case MasterWorkerProtocol.WorkFailed(workerId, workId, job, actorRef) =>
      if (workState.isInProgress(workId)) {
        log.debug(f"workId: $workId, workIdHash: ${workIdHash(workId)} failed by [worker-$workerId]")
        changeWorkerToIdle(workerId, workId)
        val x = WorkerFailed(workId)
        workState = workState.updated(x)
        notifyWorkers()
      }

    // #persisting
    case work: Work =>
      // idempotent
      if (workState.isAccepted(work.workId)) {
        sender() ! Master.Ack(work.workId)
      } else {
        log.debug(f"workId: ${work.workId} accepted")
        val x = WorkAccepted(work)
        // Ack back to original sender
        sender() ! Master.Ack(work.workId)
        workState = workState.updated(x)
        notifyWorkers()
      }
    // #persisting

    // #pruning
    case CleanupTick =>
      workers.foreach {
        case (workerId, WorkerState(_, Busy(workId, timeout), _)) if timeout.isOverdue() =>
          log.debug(f"workId: $workId, workIdHash: ${workIdHash(workId)} timed out")
          workers -= workerId
          val x = WorkerTimedOut(workId)
          workState = workState.updated(x)
          notifyWorkers()

        case (workerId, WorkerState(_, Idle, lastHeardFrom)) if lastHeardFrom.isOverdue() =>
          log.debug(f"Too long since heard from workerId $workerId, pruning")
          workers -= workerId

        case _ => // this one is a keeper!
      }
    // #pruning
  }

  def notifyWorkers(): Unit =
    if (workState.hasWork) {
      workers.foreach {
        case (_, WorkerState(ref, Idle, _)) => ref ! MasterWorkerProtocol.WorkIsReady
        case _                              => // busy
      }
    }

  def changeWorkerToIdle(workerId: String, workId: String): Unit =
    workers.get(workerId) match {
      case Some(workerState @ WorkerState(_, Busy(`workId`, _), _)) ⇒
        val newWorkerState = workerState.copy(status = Idle, staleWorkerDeadline = newStaleWorkerDeadline())
        workers += (workerId -> newWorkerState)
      case _ ⇒
        // ok, might happen after standby recovery, worker state is not persisted
    }

  def tooLongSinceHeardFrom(lastHeardFrom: Long): Boolean =
    System.currentTimeMillis() - lastHeardFrom > considerWorkerDeadAfter.toMillis

  // TODO: Refactor
  def workIdHash(workId: String): String = (workId.toArray.foldLeft(0)(_ + _.toInt) % workers.size).toString
}
package com.ashessin.cs441.project.workers

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}
import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
import akka.cluster.pubsub.DistributedPubSubMediator.Put
import akka.pattern._
import akka.util.Timeout
import com.ashessin.cs441.project.Main.conf

import scala.concurrent.duration._
import scala.io.{BufferedSource, Source}

/**
 * Dummy front-end that periodically sends a workload.
 */
object FrontEnd {

  var id = 3000
  var altWork = false

  def props(id: String, altWork: Boolean): Props = Props(new FrontEnd(id.toString, altWork))

  private case object NotOk
  private case object Tick
  private case object Retry
}

// #front-end
class FrontEnd(id: String, altWork: Boolean) extends Actor with ActorLogging with Timers {
  import FrontEnd._
  import context.dispatcher

  val mediator: ActorRef = DistributedPubSub(context.system).mediator
  mediator ! Put(self)

  val frontEndId: String = id

  val masterProxy: ActorRef = context.actorOf(
    MasterSingleton.proxyProps(context.system),
    name = "masterProxy")

  val movieFile: BufferedSource = Source.fromFile("movies.txt")
  val movieLinesIterator: Iterator[String] = movieFile.getLines.toArray.iterator
  movieFile.close()

  var workCounter = 0

  def nextWorkId(): String = {
    if (altWork)
      movieLinesIterator.next()
    else
      UUID.randomUUID().toString
  }

  override def preStart(): Unit = {
    timers.startSingleTimer("tick", Tick, 10.seconds)
  }

  def receive: Receive = idle

  def idle: Receive = {
    case Tick =>
      workCounter += 1
      val workId = nextWorkId()
      log.info(f"[front-end-$frontEndId] Produced workId: $workId with workCounter: $workCounter")
      val work = Work(workId, workCounter, self)
      context.become(busy(work))

    case WorkResult(workId, job, workIdHash, result, actorRef) =>
      log.info(f"[front-end-$frontEndId] Consumed result: $result for job: $job, workId: $workId, workIdHash: $workIdHash from [worker-${actorRef.path.name}]")

    case _: DistributedPubSubMediator.SubscribeAck =>
  }

  def busy(workInProgress: Work): Receive = {
    sendWork(workInProgress)

    {
      case Master.Ack(workId) =>
        log.info(f"[front-end-$frontEndId] Got ack for workId: $workId")
        val nextTick = ThreadLocalRandom.current.nextInt(
          conf.getInt("minRequest"),
          conf.getInt("maxRequest")).seconds
        timers.startSingleTimer(s"tick", Tick, nextTick)
        context.become(idle)

      case NotOk =>
        log.info(f"[front-end-$frontEndId] Work with workId: ${workInProgress.workId} not accepted, retry later")
        timers.startSingleTimer("retry", Retry, conf.getInt("retryRequest").seconds)

      case Retry =>
        log.info(f"[front-end-$frontEndId] Retrying workId: ${workInProgress.workId}")
        sendWork(workInProgress)
    }
  }

  def sendWork(work: Work): Unit = {
    implicit val timeout: Timeout = Timeout(conf.getInt("requestTimeout").seconds)
    (masterProxy ? work).recover {
      case _ => NotOk
    } pipeTo self
  }

}
// #front-end
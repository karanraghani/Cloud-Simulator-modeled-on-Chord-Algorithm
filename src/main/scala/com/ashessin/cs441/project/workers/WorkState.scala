package com.ashessin.cs441.project.workers

import scala.collection.immutable.Queue

object WorkState {

  def empty: WorkState = WorkState(
    pendingWork = Queue.empty,
    workInProgress = Map.empty,
    acceptedWorkIds = Set.empty,
    doneWorkIds = Set.empty)

  trait WorkDomainEvent
  // #events
  case class WorkAccepted(work: Work) extends WorkDomainEvent
  case class WorkStarted(workId: String) extends WorkDomainEvent
  case class WorkForwarded(workId: String) extends WorkDomainEvent
  case class WorkCompleted(workId: String, result: Any) extends WorkDomainEvent
  case class WorkerFailed(workId: String) extends WorkDomainEvent
  case class WorkerTimedOut(workId: String) extends WorkDomainEvent
  // #events
}

case class WorkState private (
  private val pendingWork: Queue[(Work, Boolean)],
  private val workInProgress: Map[String, Work],
  private val acceptedWorkIds: Set[String],
  private val doneWorkIds: Set[String]) {

  import WorkState._

  def allWork: Queue[(Work, Boolean)] = pendingWork
  def hasWork: Boolean = pendingWork.exists(_._2 == false)
  def isAccepted(workId: String): Boolean = acceptedWorkIds.contains(workId)
  def isInProgress(workId: String): Boolean = workInProgress.contains(workId)
  def isDone(workId: String): Boolean = doneWorkIds.contains(workId)

  def updated(event: WorkDomainEvent): WorkState = event match {
    case WorkAccepted(work) ⇒
      copy(
        pendingWork = pendingWork enqueue (work, false),
        acceptedWorkIds = acceptedWorkIds + work.workId)

    case WorkStarted(workId) ⇒
      val ((work, _), rest) = pendingWork.filter(_._1.workId==workId).dequeue
      require(workId == work.workId, s"WorkStarted expected workId $workId == ${work.workId}")
      copy(
        pendingWork = rest,
        workInProgress = workInProgress + (workId -> work))

    case WorkForwarded(workId) ⇒
      val ((work, _), rest) = pendingWork.filter(_._1.workId==workId).dequeue
      copy(
        pendingWork = rest enqueue (work, true))

    case WorkCompleted(workId, result) ⇒
      copy(
        workInProgress = workInProgress - workId,
        doneWorkIds = doneWorkIds + workId)

    case WorkerFailed(workId) ⇒
      copy(
        pendingWork = pendingWork enqueue (workInProgress(workId), false),
        workInProgress = workInProgress - workId)

    case WorkerTimedOut(workId) ⇒
      copy(
        pendingWork = pendingWork enqueue (workInProgress(workId), false),
        workInProgress = workInProgress - workId)
  }

}

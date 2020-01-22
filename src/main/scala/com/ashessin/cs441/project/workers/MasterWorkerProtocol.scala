package com.ashessin.cs441.project.workers

import akka.actor.ActorRef

object MasterWorkerProtocol {
  // Messages from Workers
  case class RegisterWorker(workerId: String)
  case class DeRegisterWorker(workerId: String)
  case class WorkerRequestsWork(workerId: String)
  case class WorkerForwardsWork(workerId: String, workId: String, forward: Boolean)
  case class WorkFailed(workerId: String, workId: String, job: Int, actorRef: ActorRef)
  case class WorkIsDone(workerId: String, workId: String, result: Any, job: Int, actorRef: ActorRef)

  // Messages to Workers
  case object WorkIsReady
  case class Ack(id: String)
}
package com.ashessin.cs441.project.workers

import akka.actor.ActorRef

case class Work(workId: String, job: Any, actorRef: ActorRef)

case class WorkResult(workId: String, job: Any, workIdHash: String, result: Any, actorRef: ActorRef)
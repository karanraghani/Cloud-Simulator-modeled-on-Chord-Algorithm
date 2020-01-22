package com.ashessin.cs441.project.workers

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, ActorRef, Props}
import com.ashessin.cs441.project.Main.conf

import scala.concurrent.duration._

/**
 * Work executor is the actor actually performing the work.
 */
object WorkExecutor {
  def props = Props(new WorkExecutor)

  case class DoWork(n: Int, actorRef: ActorRef)
  case class WorkComplete(result: String, job: Int, actorRef: ActorRef)
}

class WorkExecutor extends Actor {
  import WorkExecutor._
  import context.dispatcher

  def receive: PartialFunction[Any, Unit] = {
    case DoWork(n: Int, actorRef: ActorRef) =>
      val n2 = n * n
      val result = s"$n * $n = $n2"

      // simulate that the processing time varies
      val randomProcessingTime = ThreadLocalRandom.current.nextInt(
        conf.getInt("minProcessingTime"),
        conf.getInt("maxProcessingTime")).seconds
      context.system.scheduler.scheduleOnce(randomProcessingTime, sender(), WorkComplete(result, n, actorRef))
  }

}
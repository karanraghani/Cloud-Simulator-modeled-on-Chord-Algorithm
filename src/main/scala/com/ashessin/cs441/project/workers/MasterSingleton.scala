package com.ashessin.cs441.project.workers

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton._

import scala.concurrent.duration._

object MasterSingleton {

  private val singletonName = "master"
  private val singletonRole = "back-end"

  // #singleton
  def startSingleton(system: ActorSystem): ActorRef = {
    val workTimeout = system.settings.config.getDuration("distributed-workers.work-timeout").getSeconds.seconds

    system.actorOf(
      ClusterSingletonManager.props(
        Master.props(workTimeout),
        PoisonPill,
        ClusterSingletonManagerSettings(system).withRole(singletonRole)
      ),
      singletonName)
  }
  // #singleton

  // #proxy
  def proxyProps(system: ActorSystem): Props = ClusterSingletonProxy.props(
    settings = ClusterSingletonProxySettings(system).withRole(singletonRole),
    singletonManagerPath = s"/user/$singletonName")
  // #proxy
}

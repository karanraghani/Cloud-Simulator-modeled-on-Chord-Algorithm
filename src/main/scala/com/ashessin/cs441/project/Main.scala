package com.ashessin.cs441.project

import java.util.{Timer, TimerTask}

import akka.actor.ActorSystem
import akka.management.scaladsl.AkkaManagement
import com.ashessin.cs441.project.chord.FingerTable
import com.ashessin.cs441.project.workers.{FrontEnd, MasterSingleton, Worker}
import com.typesafe.config.{Config, ConfigFactory}

object Main {

  // note that 2551 and 2552 are expected to be seed nodes though, even if
  // the back-end starts at 2000
  val backEndPortRange: Range.Inclusive = 2000 to 2999

  val frontEndPortRange: Range.Inclusive = 3000 to 3999

  val conf: Config = ConfigFactory.load("application.conf")

  def main(args: Array[String]): Unit = {
    args.headOption match {

      case None    =>
        startClusterInSameJvm(altWork=false)
      case Some("altWork") =>
        startClusterInSameJvm(altWork=true)

      case Some(portString) if portString.matches("""\d+""") =>
        val port = portString.toInt
        if (backEndPortRange.contains(port)) startBackEnd(port)
        else if (frontEndPortRange.contains(port)) startFrontEnd(port, altWork = false)
        else startWorker(port, args.lift(1).map(_.toInt).getOrElse(1))

    }
  }

  def startClusterInSameJvm(altWork: Boolean): Unit = {
    // backend nodes
    startBackEnd(2551)

    // chord ring abstraction with some worker actors (or nodes)
    val numberOfPositions = 3
    startWorker(5001, Math.pow(2, conf.getInt("numberOfPositions")).toInt)

//    startWorker(5002, Math.pow(2, 1).toInt)
//    startWorker(5003, Math.pow(2, 1).toInt)
//    startWorker(5004, Math.pow(2, 1).toInt)
//    startWorker(5005, Math.pow(2, 1).toInt)
//    startWorker(5006, Math.pow(2, 1).toInt)
//    startWorker(5007, Math.pow(2, 1).toInt)
//    startWorker(5008, Math.pow(2, 1).toInt)
//    startWorker(5009, Math.pow(2, 1).toInt)

    // front-end nodes
    val numberOfUsers = conf.getInt("numberOfUsers")
    require(numberOfUsers < 2000, "Due to port range limitation please use lower value for numberOfUsers")
    for (i <- 3000 until 3000 + numberOfUsers) {
      startFrontEnd(i, altWork)
    }
  }

  /**
   * Start a node with the role backend on the given port
   */
  def startBackEnd(port: Int): Unit = {
    val system: ActorSystem = ActorSystem("ClusterSystem", config(port, "back-end"))
    MasterSingleton.startSingleton(system)

    // Automatically loads Cluster Http Routes
    AkkaManagement(system).start()
  }

  /**
   * Start a front end node that will submit work to the backend nodes
   */
  // #front-end
  def startFrontEnd(port: Int, altWork: Boolean): Unit = {
    val x = port - 3000 + 1
    val system = ActorSystem("ClusterSystem", config(port, "front-end"))
    system.actorOf(FrontEnd.props(x.toString, altWork), s"front-end-$x")
  }
  // #front-end

  /**
   * Start a worker node, with n actual workers that will accept and process workloads
   */
  // #worker
  def startWorker(port: Int, workers: Int): Unit = {
    val system = ActorSystem("ClusterSystem", config(port, "worker"))
    val masterProxy = system.actorOf(
      MasterSingleton.proxyProps(system),
      name = "masterProxy")

    // Initialize chord ring with worker nodes
    (0 until workers).foreach(n => {
      val fingerTable = new FingerTable(n, workers)
      system.actorOf(Worker.props(masterProxy, n.toString, fingerTable.finger, workers), s"worker-$n")
    })
  }
  // #worker

  def config(port: Int, role: String): Config =
    ConfigFactory.parseString(s"""
      akka.remote.netty.tcp.port=$port
      akka.cluster.roles=[$role]
    """).withFallback(ConfigFactory.load())

}

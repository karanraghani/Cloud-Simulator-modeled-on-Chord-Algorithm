package com.ashessin.cs441.project.workers

import com.typesafe.config.ConfigFactory
import org.scalatest.FunSuite

import scala.io.Source

class ConfigurationTest extends FunSuite {

  //test if configuration file is loaded correctly
  test("Configuration File is loaded correctly"){
    val config = ConfigFactory.load("application.conf")
    assert(config!=null)

    //test if values are loaded correctly from configuration file
    val numLines = config.getInt("numLines")
    assert( numLines == 9943)
  }

  test("Movies DataBase Check"){
    val countlines = Source.fromResource("movies.txt").getLines()

    assert(countlines!=null)
  }
}
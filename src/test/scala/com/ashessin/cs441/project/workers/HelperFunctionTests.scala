package com.ashessin.cs441.project.workers

import org.scalatest.FunSuite
import scala.collection.mutable

class HelperFunctionTests extends FunSuite {

  val workers = 8
  val currentNode = 1
  def log2(x: Int): Int = (scala.math.log(x) / scala.math.log(2)).toInt
  var ft = new mutable.HashMap[String, String]()
  (1 to log2(workers)).map(x => {
    ft += (x.toString -> ((currentNode + Math.pow(2, x - 1)) % workers).toInt.toString)
  })

  def getSuccessor(fingerTable:scala.collection.mutable.HashMap[String,String], toFind : String) : String = {
    //create hashmap with key as i and value as absolute difference
    val absDiff = fingerTable.map(x => {
      (x._1, (x._2.toInt - toFind.toInt).abs)
    })

    //get the key of the minimum difference
    val minDiffKey : String = absDiff.minBy(_._2)._1

    //Check if the to Find is less than the current node
    //if yes return the max successor
    if(toFind.toInt < absDiff.minBy(_._2)._2)
      fingerTable.maxBy(_._2)._2
    //else return the successor at min distance
    else
      fingerTable.get(minDiffKey).getOrElse("")
  }

  def md5HashString(s: String): String = {
    import java.math.BigInteger
    import java.security.MessageDigest
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(s.getBytes)
    val bigInt = new BigInteger(1,digest)
    val hashedString = bigInt.toString(16)
    hashedString
  }


  test ("Finger Table Size Check"){

    assert(log2(8) == 3)
  }
  test("Finger Table generation"){
    // check if the entry for third successor exist and is of value 5
    assert(ft("3") == "5")
  }

  test("Finding the correct successor"){
    assert(getSuccessor(ft,"8") == "5")
  }

  test("Consitent Hashing using MD5") {

    assert(md5HashString("1234567890") == "e807f1fcf82d132f9bb018ca6738a19f")
  }
}

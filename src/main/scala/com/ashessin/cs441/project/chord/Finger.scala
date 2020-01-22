package com.ashessin.cs441.project.chord

import akka.actor.ActorRef
import com.ashessin.cs441.project.workers.{Work, Worker}

import scala.collection.mutable

/*
 * https://en.wikipedia.org/wiki/Chord_(peer-to-peer)#Pseudocode
 *
 * finger[k]
 *  first node that succeeds (n+2^(k-1) mod 2^m, 1<=k<=m)
 * successor
 *  the next node from the node in question on the identifier ring
 * predecessor
 *  the previous node from the node in question on the identifier ring
 *
 * The pseudocode to find the successor node of an id is given below:

 * // ask node n to find the successor of id
 * n.find_successor(id)
 *  //Yes, that should be a closing square bracket to match the opening parenthesis.
 *  //It is a half closed interval.
 *  if (id ∈ (n, successor] )
 *    return successor;
 *  else
 *    // forward the query around the circle
 *    n0 = closest_preceding_node(id);
 *    return n0.find_successor(id);

 * // search the local table for the highest predecessor of id
 * n.closest_preceding_node(id)
 *  for i = m downto 1
 *    if (finger[i]∈(n,id))
 *      return finger[i];
 *  return n;

 */

/**
 * Creates finger table for `n`th node in a ring with a total of `positions`.
 * Each table consisting of log2(positions) entries.
 *
 * @param n         node
 * @param positions total number of nodes or available positions in a ring
 */
class FingerTable(n: Int, positions: Int) {

  val finger: mutable.TreeMap[String, (Range, String)] = create

  private def create: mutable.TreeMap[String, (Range, String)] = {
    require(0 <= n, "Node position n can't be negative")
    require(1 <= positions, "There has to be at least one position")
    val fingerTable = mutable.TreeMap[String, (Range, String)]()
    (1 to log2(positions)).foreach(k => {
      val finger = new Finger(n, k, log2(positions))
      fingerTable.put(finger.pointer, (Range(0, 1), finger.value))
    })

    val l, m = Iterator.continually(fingerTable).flatten;
    m.next()
    (1 to log2(positions)).foreach(i => {
      val pointer = i.toString
      val start = l.next()._2._2.toInt
      val end = m.next()._2._2.toInt
      if (pointer == fingerTable.last._1)
        // if its the last pointer, reduce closing successor range by 1
        if (start > end - 1)
          fingerTable(pointer) = (Range(Math.abs(end - 1), start), fingerTable(pointer)._2)
        else
          fingerTable(pointer) = (Range(start, Math.abs(end - 1)), fingerTable(pointer)._2)
      else
        if (start > end)
          fingerTable(pointer) = (Range(end, start), fingerTable(pointer)._2)
        else
          fingerTable(pointer) = (Range(start, end), fingerTable(pointer)._2)
    })
    fingerTable
  }

  private def log2(x: Int): Int = (scala.math.log(x) / scala.math.log(2)).toInt
}


/**
 * Creates the `k`th "finger" entry for node `n` of a ring with 2pow(m) positions.
 *
 * @param n node
 * @param k key (or the entry position in the corresponding Finger Table)
 * @param m identifiers (or total entries in the corresponding Finger Table)
 */
class Finger(n: Int, k: Int, m: Int) {

  val (pointer, value) = create

  private def create: (String, String) = {
    require(1 <= k && k <= m, "Chord ring requirement 1<=k<=m is not satisfied")
    k.toString -> ((n + Math.pow(2, k - 1)) % Math.pow(2, m)).toInt.toString
  }
}

/* Stub, logic in Worker nodes*/

object find_successor {
  def main(n: String, id: String, successor: String, actorRef: ActorRef): Unit = {
    val finger = actorRef.asInstanceOf[Worker].finger
    if ((n.toInt until successor.toInt) contains id.toString)
      return successor
    else{
      val n_0 = closest_preceding_node.main(n, id, actorRef)
      find_successor.main(n_0, id, successor, actorRef);
    }
  }
}

object closest_preceding_node {
  def main(n: String, id: String, actorRef: ActorRef): String = {
    val finger = actorRef.asInstanceOf[Worker].finger
    (1 to finger.size).foreach(i => {
      val pointer = i.toString
      if (Range(n.toInt, n.toInt) contains finger(pointer)._2.toInt)
        return finger(pointer)._2
    })
    n
  }
}

// TODO: Refactor this
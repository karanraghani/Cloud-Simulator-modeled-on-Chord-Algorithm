package com.ashessin.cs441.project.chord

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
 * The pseudocode to stabilize the chord ring/circle after node joins and departures is as follows:

 * // create a new Chord ring.
 * n.create()
 *   predecessor = nil;
 *   successor = n;

 * // join a Chord ring containing node n'.
 * n.join(n')
 *   predecessor = nil;
 *   successor = n'.find_successor(n);

 * // called periodically. n asks the successor
 * // about its predecessor, verifies if n's immediate
 * // successor is consistent, and tells the successor about n
 * n.stabilize()
 *   x = successor.predecessor;
 *   if (x∈(n, successor))
 *     successor = x;
 *   successor.notify(n);

 * // n' thinks it might be our predecessor.
 * n.notify(n')
 *   if (predecessor is nil or n'∈(predecessor, n))
 *     predecessor = n';

 * // called periodically. refreshes finger table entries.
 * // next stores the index of the finger to fix
 * n.fix_fingers()
 *   next = next + 1;
 *   if (next > m)
 *     next = 1;
 *   finger[next] = find_successor(n+2^{next-1});

 * // called periodically. checks whether predecessor has failed.
 * n.check_predecessor()
 *   if (predecessor has failed)
 *     predecessor = nil;

 */

/* Stub, logic in Worker nodes*/

class Stabalization {

}

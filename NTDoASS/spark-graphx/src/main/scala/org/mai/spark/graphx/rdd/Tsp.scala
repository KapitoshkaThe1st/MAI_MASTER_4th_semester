package org.mai.spark.graphx.rdd

import org.apache.spark.graphx.{Edge, EdgeTriplet, Graph, VertexId}
import org.mai.spark.graphx.rdd.TspSolver.{UNUSED, tripletType}
import scala.util.Random

object TspSolver {
  private val UNUSED: Int = -1
  private type tripletType = EdgeTriplet[Boolean, (Double, Int, Int)]
}

case class LocalVertex(id: Long)
case class LocalEdge(src: Long, dst: Long, weight: Double)

class TspSolver (topCount: Int) {

  val rand = new scala.util.Random(0)

  private def selectRandom[T](items: List[T], key: T => Double): Option[T] = {
    if (items.isEmpty) {
      Option.empty[T]
    }
    else if(items.size == 1){
      Option(items.head)
    }
    else {
      val values = items.map(key)
      val prefixSum = values.scanLeft(0.0)(_ + _)

      val sum = prefixSum.last

      prefixSum.foreach(println)

      val value = rand.nextDouble() * sum

      val index = prefixSum.lastIndexWhere(x => value >= x)
      val result = items(index)

      Option(result)
    }
  }

  private def invertEdge(edge: (Long, Long, Double)) = {
    (edge._2, edge._1, edge._3)
  }

  private def restoreOrderFirstTwo(order: Array[(Long, Long, Double)]): Unit = {
    if (order(0)._2 == order(1)._2) {
      order(1) = invertEdge(order(1))
    }
    else if (order(0)._1 == order(1)._1) {
      order(0) = invertEdge(order(0))
    }
    else if (order(0)._1 == order(1)._2) {
      order(0) = invertEdge(order(0))
      order(1) = invertEdge(order(1))
    }
  }

  private def restoreOrder(order: Array[(Long, Long, Double)]): Unit = {

    restoreOrderFirstTwo(order)

    for (i <- 1 until order.length) {
      val prev = order(i - 1)
      val cur = order(i)
      if (prev._2 != cur._1) {
        val newTup = (cur._2, cur._1, cur._3)
        order(i) = newTup
      }
    }
  }

  private def convertToAdjList(edges: List[LocalEdge]) = {
    val temp = edges.map(e => e.dst -> e) ++ edges.map(e => e.src -> e)
    temp.groupBy(e => e._1).mapValues(l => l.map(e => e._2))
  }

  def greedyLocal(vertices: List[LocalVertex], edges: List[LocalEdge], samples: Int) = {
    val (temp, bestWeight) = Random
      .shuffle(vertices)
      .take(samples)
      .map(o => {
        val order = greedyLocalIteration(o.id, vertices, edges)
        val sum = order.map(_.weight).sum

        (order, sum)
      })
      .minBy(_._2)

    val bestOrder = temp.map(t => (t.src, t.dst, t.weight)).toArray

    restoreOrder(bestOrder)
    (bestOrder.toList, bestWeight)
  }

  def greedyLocalIteration(origin: Long, vertices: List[LocalVertex], edges: List[LocalEdge]) = {
    val adj = convertToAdjList(edges)

    var edgesAreAvailable = false
    var iteration = 0

    val usedVerticesId = collection.mutable.HashSet.empty[Long]
    usedVerticesId += origin

    var nextVertexId = origin

    val order = collection.mutable.LinkedHashSet.empty[LocalEdge]

    do {
      val availableEdges = adj(nextVertexId)
        .filter(e =>
          !order.contains(e)
          && (e.src == nextVertexId && !usedVerticesId.contains(e.dst)
            || e.dst == nextVertexId && !usedVerticesId.contains(e.src))
        )

      edgesAreAvailable = availableEdges.nonEmpty

      if(edgesAreAvailable) {
        val pretendents = availableEdges.sortBy(e => e.weight).take(topCount).reverse.zipWithIndex.map(t => (t._1, t._2 + 1))
        val smallestEdge = selectRandom[(LocalEdge, Int)](pretendents, e => e._2 * e._2).get._1

        order += smallestEdge

        nextVertexId = if (smallestEdge.src == nextVertexId) smallestEdge.dst else smallestEdge.src
        usedVerticesId += nextVertexId

        iteration += 1
      }
    } while(edgesAreAvailable)

    val lastEdge = adj(nextVertexId).find(e => e.src == origin || e.dst == origin)

    lastEdge.foreach(e => {order += e})

    order.toList
  }

  def greedyGraphX[VD](g: Graph[VD, Double], samples: Int) = {
    val (temp, bestWeight) = g
      .vertices
      .takeSample(true, samples, 0)
      .map(o => greedyGraphXIteration(g, o._1)).map(g => {
      val order = g
        .triplets
        .filter(_.attr._2 >= 0)
        .sortBy(_.attr._2)
        .map(et => (et.srcId, et.dstId, et.attr._1))

      val sum = order
        .map(_._3)
        .sum

      (order, sum)
    }).minBy(_._2)

    val bestOrder = temp.collect

    restoreOrder(bestOrder)
    (bestOrder.toList, bestWeight)
  }

  def greedyGraphXIteration[VD](g: Graph[VD, Double], origin: VertexId): Graph[Boolean, (Double, Int, Int)] = {
    var g2 = g
      .mapVertices((vid, _) => vid == origin)
      .mapTriplets(et => (et.attr, UNUSED, 0))

    var nextVertexId = origin
    var edgesAreAvailable = true

    var iteration = 0
    do {
      println(s"iteration: $iteration")

      val availableEdges =
        g2.triplets
          .filter(
            et => et.attr._2 == UNUSED // ребро еще не использовано
              && (// и оно инцидентно текущей вершине
              et.srcId == nextVertexId && !et.dstAttr
                || et.dstId == nextVertexId && !et.srcAttr
              )
          )

      edgesAreAvailable = availableEdges.count > 0

      if (edgesAreAvailable) {
        // ищем среди еще неиспользованных ребер ребро с наименьшим весом
        val pretendents = availableEdges
          .top(topCount + 1)(new Ordering[tripletType]() {
            override def compare(a: tripletType, b: tripletType): Int = {
              -Ordering[Double].compare(a.attr._1, b.attr._1)
            }
          }).toList.reverse.zipWithIndex.map(t => (t._1, t._2 + 1)).map(t => (t._1, t._2 + 1))

        val smallestEdge = selectRandom[(EdgeTriplet[Boolean, (Double, Int, Int)], Int)](pretendents, e => e._2 * e._2).get._1

        // вычисляем id вершины, которая станет текущей на следующей итерации
        nextVertexId = if (smallestEdge.srcId == nextVertexId) smallestEdge.dstId else smallestEdge.srcId

        g2 = g2
          // вершины помечаются посещенными: те, что уже были посещены, остаются посещенными
          // + второй частью выражения помечается новая текущая вершина
          .mapVertices((vid, vd) => vd || vid == nextVertexId)
          // ребра помечаются использованными: те, что уже были использованы, остаются использованными
          // + второй частью выражения помечается выбранное на этом шаге ребро
          .mapTriplets(et => (et.attr._1,
              if(et.srcId == smallestEdge.srcId && et.dstId == smallestEdge.dstId)
                et.attr._3
              else
                et.attr._2,
          et.attr._3 + 1))
      }

      iteration += 1
    } while (edgesAreAvailable)

    g2 = g2
      // ребра помечаются использованными: те, что уже были использованы, остаются использованными
      // + второй частью выражения помечается выбранное на этом шаге ребро
      .mapTriplets(et => (et.attr._1,
        if (et.srcId == nextVertexId && et.dstId == origin
          || et.srcId == origin && et.dstId == nextVertexId)
          et.attr._3
        else
          et.attr._2,
        et.attr._3 + 1))

    g2
  }
}

package org.mai.spark.graphx.rdd

import org.apache.spark.graphx.{Edge, EdgeTriplet, Graph, VertexId}
import org.mai.spark.graphx.rdd.Tsp.{UNUSED, tripletType}

object Tsp {
  private val UNUSED: Int = -1
  private type tripletType = EdgeTriplet[Boolean, (Double, Int, Int)]
}

case class LocalVertex(id: Long)
case class LocalEdge(src: Long, dst: Long, weight: Double)

trait Tsp {
  def convertToAdjList(edges: List[LocalEdge]) = {
    val temp = edges.map(e => e.dst -> e) ++ edges.map(e => e.src -> e)
    temp.groupBy(e => e._1).mapValues(l => l.map(e => e._2))
  }

  def greedyLocal(origin: Long, vertices: List[LocalVertex], edges: List[LocalEdge]) = {
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
        val smallestEdge = availableEdges.minBy(e => e.weight)

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

  def greedyGraphX[VD](g: Graph[VD, Double], origin: VertexId): Graph[Boolean, (Double, Int, Int)] = {
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
        val smallestEdge = availableEdges
          .min()(new Ordering[tripletType]() {
            override def compare(a: tripletType, b: tripletType): Int = {
              Ordering[Double].compare(a.attr._1, b.attr._1)
            }
          })

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

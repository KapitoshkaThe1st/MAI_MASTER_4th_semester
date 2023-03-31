package org.mai.spark.graphx.rdd

import org.apache.spark.graphx.{Edge, Graph, VertexId}
import org.apache.spark.sql.SparkSession
import org.log4s.getLogger
import spire.math.UInt

import scala.collection.mutable.ListBuffer
import scala.io.Source

object TspTest {
  val appName: String = "TSP test"
  private val filePath = "data/qa194.tsp"
  // private val filePath = "data/usa.tsp"

  private val topCount = 3

  private[this] val logger = getLogger

  def main(args: Array[String]): Unit = {
    setLoggerLevel
//    testGraphXTSP()
    testLocalTSP()
  }

  private def readData(filePath: String): List[(Long, (Double, Double))] = {
    val lb = ListBuffer.empty[(Long, (Double, Double))]

    val bufferedSource = Source.fromFile(filePath)

    bufferedSource
      .getLines
      .dropWhile(s => s != "NODE_COORD_SECTION")
      .drop(1)
      .foreach(line => {
        println(line)

        if (line != "EOF") {
          val words = line.split(' ')

          val id = words(0).toLong
          val coordX = words(1).toDouble
          val coordY = words(2).toDouble

          lb.+=:(id, (coordX, coordY))
        }
      });

    bufferedSource.close

    lb.toList
  }

  private def euc2dDistance(x1 : Double, y1: Double, x2 : Double, y2: Double): Double = {
    val dx = x1 - x2
    val dy = y1 - y2

    Math.sqrt(dx * dx + dy * dy)
  }

  private def generateEdges(vertices: List[(Long, (Double, Double))]): List[LocalEdge] = {
    val lb = ListBuffer.empty[LocalEdge]
    for(i <- vertices.indices; j <- i+1 until vertices.size) {
      val v1 = vertices(i)
      val v2 = vertices(j)

      val (v1x, v1y) = v1._2
      val (v2x, v2y) = v2._2

      lb += LocalEdge(v1._1, v2._1, euc2dDistance(v1x, v1y, v2x, v2y))
    }

    lb.toList
  }

  private def generateGraph2(spark: SparkSession): Graph[String, Double] = {

    val (vertices, edges) = generateLocalGraph2()

    val verticesRDD = spark.sparkContext.parallelize(vertices.map(v => (v.id, v.id.toString)))
    val edgesRDD = spark.sparkContext.parallelize(edges.map(e => Edge(e.src, e.dst, e.weight)))

    val graph = Graph(verticesRDD, edgesRDD)

    graph
  }

  private def generateGraph(spark: SparkSession): Graph[String, Double] = {

    val (vertices, edges) = generateLocalGraph()

    val verticesRDD = spark.sparkContext.parallelize(vertices.map(v => (v.id, v.id.toString)))
    val edgesRDD = spark.sparkContext.parallelize(edges.map(e => Edge(e.src, e.dst, e.weight)))

    val graph = Graph(verticesRDD, edgesRDD)

    graph
  }

  private def invertEdge(edge: (VertexId, VertexId, Double)) = {
    (edge._2, edge._1, edge._3)
  }

  private def restoreOrderFirstTwo(order: Array[(VertexId, VertexId, Double)]): Unit = {
    if(order(0)._2 == order(1)._2) {
      order(1) = invertEdge(order(1))
    }
    else if(order(0)._1 == order(1)._1) {
      order(0) = invertEdge(order(0))
    }
    else if(order(0)._1 == order(1)._2) {
      order(0) = invertEdge(order(0))
      order(1) = invertEdge(order(1))
    }
  }

  private def restoreOrder(order: Array[(VertexId, VertexId, Double)]): Unit = {

    restoreOrderFirstTwo(order)

    for( i <- 1 until order.length ) {
      val prev = order(i - 1)
      val cur = order(i)
      if(prev._2 != cur._1) {
        val newTup = (cur._2, cur._1, cur._3)
        order(i) = newTup
      }
    }
  }

  private def generateLocalGraph2(): (List[LocalVertex], List[LocalEdge]) = {
    val tempVertices = readData(filePath)
    val edges = generateEdges(tempVertices)
    val vertices = tempVertices.map(v => LocalVertex(v._1))

    (vertices, edges)
  }

  private def generateLocalGraph(): (List[LocalVertex], List[LocalEdge]) = {
    val vertices = List(1L, 2L, 3L, 4L).map(LocalVertex)
    val edges = List(
      LocalEdge(1L, 2L, 10.0),
      LocalEdge(1L, 3L, 15.0),
      LocalEdge(1L, 4L, 20.0),
      LocalEdge(2L, 3L, 35.0),
      LocalEdge(2L, 4L, 25.0),
      LocalEdge(3L, 4L, 30.0)
    )

    (vertices, edges)
  }

  private def testLocalTSP(): Unit = {
    val (vertices, edges) = generateLocalGraph2()

    val algo = new TspSolver(topCount)

    val order = algo.greedyLocal(1L, vertices, edges).map(e => (e.src, e.dst, e.weight)).toArray

    restoreOrder(order)

    println("traverse order:")
    order.foreach(println)

    val totalWeight = order.map(_._3).sum
    println(s"total weight: $totalWeight")
  }

  private def testGraphXTSP(): Unit = {

    logger.info(s"Start spark job $appName")
    val spark = createSparkSession(appName)

    val graph = generateGraph2(spark)
      .mapEdges(_.attr)

    val algo = new TspSolver(topCount)

    println("start at 1")

    val g = algo.greedyGraphX(graph, 1L)
    val order = g.triplets.filter(_.attr._2 >= 0).sortBy(_.attr._2).map(et => (et.srcId, et.dstId, et.attr._1))
      .collect

    restoreOrder(order)

    println("traverse order:")
    order.foreach(println)

    val totalWeight = order.map(_._3).sum
    println(s"total weight: $totalWeight")

    logger.info(s"Sleep for 500000 millis")
    Thread.sleep(500000)
    logger.info(s"Finish spark job $appName")
  }
}

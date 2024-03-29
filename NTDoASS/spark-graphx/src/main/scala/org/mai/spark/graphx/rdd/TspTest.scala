package org.mai.spark.graphx.rdd

import org.apache.spark.graphx.{Edge, Graph, VertexId}
import org.apache.spark.sql.SparkSession
import org.log4s.getLogger

import scala.collection.mutable.ListBuffer
import scala.io.Source

object TspTest {
  val appName: String = "TSP test"
  private val filePath = "data/qa194.tsp"
  // private val filePath = "data/usa.tsp"

  private val topCount = 3
  private val samples = 4

  private[this] val logger = getLogger

  def main(args: Array[String]): Unit = {
    setLoggerLevel
    testGraphXTSP()
//    testLocalTSP()
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

//    lb.toList
    lb.zipWithIndex.filter(t => t._2 % 2 == 0).map(t => t._1).toList
  }

  private def euc2dDistance(x1 : Double, y1: Double, x2 : Double, y2: Double): Double = {
    val dx = x1 - x2
    val dy = y1 - y2

    Math.sqrt(dx * dx + dy * dy)
  }

  private def generateEdges(vertices: List[(Long, (Double, Double))]): List[LocalEdge] = {
    val lb = ListBuffer.empty[LocalEdge]
    for(i <- vertices.indices; j <- i + 1 until vertices.size) {
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

    val (order, weight) = algo.greedyLocal(vertices, edges, samples)

    println("traverse order:")
    order.foreach(println)

    println(s"total weight: $weight")
  }

  private def testGraphXTSP(): Unit = {

    logger.info(s"Start spark job $appName")
    val spark = createSparkSession(appName)

    val graph = generateGraph2(spark)
//    val graph = generateGraph(spark)
      .mapEdges(_.attr)

    val algo = new TspSolver(topCount)

    val (order, weight) = algo.greedyGraphX(graph, samples)
    println("traverse order:")
    order.foreach(println)

    println(s"total weight: $weight")

    logger.info(s"Sleep for 500000 millis")
    Thread.sleep(500000)
    logger.info(s"Finish spark job $appName")
  }
}

package org.mai.spark.graphx

import org.apache.logging.log4j.{Level, LogManager}
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.{Configuration, Configurator}
import org.apache.spark.graphx.{Edge, Graph}
import org.apache.spark.sql.SparkSession
import org.log4s.getLogger

package object rdd {

  private[this] val logger = getLogger

  def setLoggerLevel = {
    Configurator.setRootLevel(Level.OFF)
    Configurator.setAllLevels("org.mai.spark.graphx.rdd", Level.INFO)
  }

  def master: String = sys.env.getOrElse("SPARK_MASTER", "local[2]")

  def createSparkSession(appName: String) = {
    SparkSession.builder().master(master).appName(appName).getOrCreate()
  }

}

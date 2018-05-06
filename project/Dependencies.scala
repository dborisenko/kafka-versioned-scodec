import sbt._

object Dependencies {

  object Versions {
    val scala = "2.12.6"

    val scodec = "1.10.3"
    val kafka = "1.1.0"
    val scalatest = "3.0.5"
  }

  val `scodec-core` = "org.scodec" %% "scodec-core" % Versions.scodec
  val `kafka-clients` = "org.apache.kafka" % "kafka-clients" % Versions.kafka
  val scalatest = "org.scalatest" %% "scalatest" % Versions.scalatest
}

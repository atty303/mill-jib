package io.github.atty303.mill.jib.worker.api

import mill.api.Logger

import java.nio.file.Path

sealed trait Image
object Image {
  case class DockerDaemonImage(image: String) extends Image
  case class RegistryImage(image: String, username: String, password: String)
      extends Image
}

trait JibWorker {
  def build(
      logger: Logger,
      image: Image,
      tags: Seq[String],
      baseImage: String,
      mainClass: String,
      deps: Seq[Path],
      projectDeps: Seq[Path],
      jvmFlags: Seq[String],
      labels: Map[String, String]
  ): Unit
}

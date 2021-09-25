package io.github.atty303.mill.jib.worker.api

import mill.api.Logger

import java.nio.file.Path
import java.time.Instant

sealed trait Image
object Image {
  case class DockerDaemonImage(image: String) extends Image
  case class RegistryImage(image: String, username: String, password: String)
      extends Image
}

sealed trait ImageFormat
object ImageFormat {
  case object Docker extends ImageFormat
  case object OCI extends ImageFormat
}

case class Port(port: Int, protocol: Port.Protocol)
object Port {
  def tcp(port: Int): Port = Port(port, Protocol.Tcp)
  def udp(port: Int): Port = Port(port, Protocol.Udp)

  sealed trait Protocol
  object Protocol {
    case object Tcp extends Protocol
    case object Udp extends Protocol
  }
}

case class Platform(architecture: String, os: String)

case class ContainerConfig(
    entrypoint: Seq[String],
    programArguments: Seq[String],
    environment: Map[String, String],
    volumes: Set[String],
    exposedPorts: Set[Port],
    labels: Map[String, String],
    format: ImageFormat,
    creationTime: Instant,
    platforms: Set[Platform],
    user: Option[String],
    workingDirectory: Option[String]
)

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
      containerConfig: ContainerConfig
  ): Unit
}

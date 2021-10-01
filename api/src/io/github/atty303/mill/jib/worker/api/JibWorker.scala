package io.github.atty303.mill.jib.worker.api

import mill.api.Logger

import java.nio.file.{Path => NioPath}
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

case class FileEntry(
    sourceFile: os.Path,
    extractionPath: String,
    permissions: Option[String] = None,
    modificationTime: Instant = Instant.ofEpochSecond(1),
    ownership: String = ""
) {
  def effectivePermissions: String =
    permissions.getOrElse(if (sourceFile.toIO.isDirectory) "755" else "644")
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
    workingDirectory: Option[String],
    additionalLayers: Seq[(String, Seq[FileEntry])]
)

trait JibWorker {
  def build(
      logger: Logger,
      image: Image,
      tags: Seq[String],
      baseImage: String,
      mainClass: String,
      deps: Seq[NioPath],
      projectDeps: Seq[NioPath],
      jvmFlags: Seq[String],
      containerConfig: ContainerConfig
  ): Unit
}

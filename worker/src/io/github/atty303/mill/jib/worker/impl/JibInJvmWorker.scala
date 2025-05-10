package io.github.atty303.mill.jib.worker.impl

import com.google.cloud.tools.jib.api.Containerizer
import com.google.cloud.tools.jib.api.Credential
import com.google.cloud.tools.jib.api.CredentialRetriever
import com.google.cloud.tools.jib.api.DockerDaemonImage
import com.google.cloud.tools.jib.api.ImageReference
import com.google.cloud.tools.jib.api.JavaContainerBuilder
import com.google.cloud.tools.jib.api.JibContainerBuilder
import com.google.cloud.tools.jib.api.LogEvent
import com.google.cloud.tools.jib.api.LogEvent.Level
import com.google.cloud.tools.jib.api.RegistryImage
import com.google.cloud.tools.jib.api.buildplan
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer
import com.google.cloud.tools.jib.api.buildplan.FileEntry
import com.google.cloud.tools.jib.api.buildplan.FilePermissions
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory
import io.github.atty303.mill.jib.worker.api.CachePath
import io.github.atty303.mill.jib.worker.api.ContainerConfig
import io.github.atty303.mill.jib.worker.api.Credentials
import io.github.atty303.mill.jib.worker.api.Image
import io.github.atty303.mill.jib.worker.api.ImageFormat
import io.github.atty303.mill.jib.worker.api.JibWorker
import io.github.atty303.mill.jib.worker.api.Platform
import io.github.atty303.mill.jib.worker.api.Port
import mill.api.Logger

import java.nio.file.Path
import java.util.Optional
import java.util.function.Consumer
import scala.jdk.CollectionConverters._

class JibInJvmWorker extends JibWorker {
  override def build(
      logger: Logger,
      credentials: Credentials,
      image: Image,
      tags: Seq[String],
      baseImageName: String,
      mainClass: String,
      deps: Seq[Path],
      projectDeps: Seq[Path],
      jvmFlags: Seq[String],
      cc: ContainerConfig,
      baseImageLayersCachePath: CachePath,
      applicationLayersCachePath: CachePath,
  ): Unit = {
    val targetImage = image match {
      case Image.DockerDaemonImage(v) => ImageReference.parse(v)
      case Image.RegistryImage(v)     => ImageReference.parse(v)
    }
    val baseImage = imageFactory(
      ImageReference.parse(baseImageName),
      credentials.baseUsername,
      credentials.basePassword,
      logger
    )

    var cont = image match {
      case Image.DockerDaemonImage(_) =>
        Containerizer.to(DockerDaemonImage.named(targetImage))
      case Image.RegistryImage(_) =>
        Containerizer.to(
          imageFactory(
            targetImage,
            credentials.targetUsername,
            credentials.targetPassword,
            logger
          )
        )
    }
    cont = tags.foldLeft(cont)((acc, t) => acc.withAdditionalTag(t))
    val defaultCacheDir = Containerizer.DEFAULT_BASE_CACHE_DIRECTORY
      .resolve(Containerizer.DEFAULT_APPLICATION_CACHE_DIRECTORY_NAME)
    cont = baseImageLayersCachePath match {
      case CachePath.None =>
        cont
      case CachePath.Default =>
        cont.setBaseImageLayersCache(defaultCacheDir)
      case CachePath.Directory(path) =>
        cont.setBaseImageLayersCache(path.toNIO)
    }
    cont = applicationLayersCachePath match {
      case CachePath.None =>
        cont
      case CachePath.Default =>
        cont.setApplicationLayersCache(defaultCacheDir)
      case CachePath.Directory(path) =>
        cont.setApplicationLayersCache(path.toNIO)
    }
    cont = cont.addEventHandler(classOf[LogEvent], makeLogger(logger))

    var builder = JavaContainerBuilder
      .from(baseImage)
      .setMainClass(mainClass)
      .addDependencies(deps.asJava)
      .addProjectDependencies(projectDeps.asJava)
      .addJvmFlags(jvmFlags.asJava)
      .toContainerBuilder

    builder =
      if (cc.entrypoint.isEmpty) builder
      else builder.setEntrypoint(cc.entrypoint.asJava)
    builder =
      if (cc.programArguments.isEmpty) builder
      else builder.setProgramArguments(cc.programArguments.asJava)
    builder =
      if (cc.environment.isEmpty) builder
      else builder.setEnvironment(cc.environment.asJava)
    builder =
      if (cc.volumes.isEmpty) builder
      else builder.setVolumes(cc.volumes.map(AbsoluteUnixPath.get).asJava)
    builder =
      if (cc.exposedPorts.isEmpty) builder
      else builder.setExposedPorts(cc.exposedPorts.map(portAsJava).asJava)
    builder =
      if (cc.labels.isEmpty) builder
      else builder.setLabels(cc.labels.asJava)
    builder = builder.setFormat(cc.format match {
      case ImageFormat.Docker => buildplan.ImageFormat.Docker
      case ImageFormat.OCI    => buildplan.ImageFormat.OCI
    })
    builder = builder.setCreationTime(cc.creationTime)
    builder =
      if (cc.platforms.isEmpty) builder
      else builder.setPlatforms(cc.platforms.map(platformAsJava).asJava)
    builder = cc.user match {
      case None       => builder
      case Some(user) => builder.setUser(user)
    }
    builder = cc.workingDirectory match {
      case None      => builder
      case Some(dir) => builder.setWorkingDirectory(AbsoluteUnixPath.get(dir))
    }
    builder = cc.additionalLayers.foldLeft(builder) { (b, l) =>
      {
        val (name, entries) = l
        val layer = entries.foldLeft(FileEntriesLayer.builder().setName(name)) {
          (c, e) =>
            c.addEntry(
              new FileEntry(
                e.sourceFile.toNIO,
                AbsoluteUnixPath.get(e.extractionPath),
                FilePermissions.fromOctalString(e.effectivePermissions),
                e.modificationTime,
                e.ownership
              )
            )
        }
        b.addFileEntriesLayer(layer.build())
      }
    }

    containerizeWithLock(logger, builder, cont)
    logger.ticker(s"Containerized ${targetImage}")
  }

  private def makeLogger(logger: Logger): Consumer[LogEvent] = (t: LogEvent) =>
    t.getLevel match {
      case Level.ERROR     => logger.error(t.getMessage)
      case Level.WARN      => logger.error(t.getMessage)
      case Level.LIFECYCLE => logger.info(t.getMessage)
      case Level.PROGRESS  => logger.ticker(t.getMessage)
      case Level.INFO      => logger.debug(t.getMessage)
      case Level.DEBUG     => logger.debug(t.getMessage)
    }

  private def imageFactory(
      imageReference: ImageReference,
      username: Option[String],
      password: Option[String],
      logger: Logger
  ): RegistryImage = {
    val image0 = RegistryImage.named(imageReference)
    val crf =
      CredentialRetrieverFactory.forImage(imageReference, makeLogger(logger))

    image0
      .addCredentialRetriever(literalCredentials(username, password))
      .addCredentialRetriever(crf.dockerConfig())
      .addCredentialRetriever(crf.wellKnownCredentialHelpers())
      .addCredentialRetriever(crf.googleApplicationDefaultCredentials())
  }

  private def literalCredentials(
      username: Option[String],
      password: Option[String]
  ): CredentialRetriever = () => {
    val o = for {
      u <- username
      p <- password
    } yield Credential.from(u, p)
    Optional.ofNullable(o.orNull)
  }

  private def portAsJava(port: Port): buildplan.Port = port.protocol match {
    case Port.Protocol.Tcp => buildplan.Port.tcp(port.port)
    case Port.Protocol.Udp => buildplan.Port.udp(port.port)
  }

  private def platformAsJava(platform: Platform): buildplan.Platform =
    new buildplan.Platform(platform.architecture, platform.os)

  // Based on https://github.com/quarkusio/quarkus/pull/35308
  /** Wraps the containerize invocation in a synchronized block to avoid OverlappingFileLockException when running parallel jib
    * builds (e.g. mill --jobs 2 ...).
    * Each build thread uses its own augmentation CL (which is why the OverlappingFileLockException prevention in jib doesn't
    * work here), so the lock object
    * has to be loaded via the parent classloader so that all build threads lock the same object.
    */
  private def containerizeWithLock(
      logger: Logger,
      builder: JibContainerBuilder,
      containerizer: Containerizer
  ) = {
    val parentCl = getClass().getClassLoader().getParent()
    val lock =
      try {
        parentCl.loadClass("io.github.atty303.mill.jib.JibInJvmWorkerManager")
      } catch {
        case _: ClassNotFoundException => {
          logger.error(
            s"Could not load io.github.atty303.mill.jib.JibInJvmWorkerManager with parent classloader: ${parentCl}"
          )
          getClass()
        }
      }
    logger.debug(s"containerize: Trying to acquire lock [lock=${lock}, classloader=${lock.getClassLoader()}]")
    lock.synchronized {
      logger.debug(s"containerize: The lock was acquired, begin build [lock=${lock}, classloader=${lock.getClassLoader()}]")
      builder.containerize(containerizer)
      logger.debug(s"containerize: done [lock=${lock}, classloader=${lock.getClassLoader()}]")
    }
    logger.debug(s"containerize: The lock was released [lock=${lock}, classloader=${lock.getClassLoader()}]")
  }
}

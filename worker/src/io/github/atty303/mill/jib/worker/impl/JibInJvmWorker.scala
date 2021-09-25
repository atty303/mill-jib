package io.github.atty303.mill.jib.worker.impl

import com.google.cloud.tools.jib.api.LogEvent.Level
import com.google.cloud.tools.jib.api.{
  Containerizer,
  DockerDaemonImage,
  JavaContainerBuilder,
  LogEvent,
  RegistryImage
}
import io.github.atty303.mill.jib.worker.api.{Image, JibWorker}
import mill.api.Logger

import java.nio.file.Path
import scala.jdk.CollectionConverters._

class JibInJvmWorker extends JibWorker {
  override def build(
      logger: Logger,
      image: Image,
      tags: Seq[String],
      baseImage: String,
      mainClass: String,
      deps: Seq[Path],
      projectDeps: Seq[Path],
      jvmFlags: Seq[String],
      labels: Map[String, String]
  ): Unit = {
    val cont0 = image match {
      case Image.DockerDaemonImage(v) =>
        Containerizer.to(DockerDaemonImage.named(v))
      case Image.RegistryImage(v, u, p) =>
        Containerizer.to(RegistryImage.named(v).addCredential(u, p))
    }
    val cont1 = tags.foldLeft(cont0)((acc, t) => acc.withAdditionalTag(t))
    val cont2 = cont1
      .setApplicationLayersCache(Containerizer.DEFAULT_BASE_CACHE_DIRECTORY)
      .addEventHandler(
        classOf[LogEvent],
        (t: LogEvent) =>
          t.getLevel match {
            case Level.ERROR     => logger.error(t.getMessage)
            case Level.WARN      => logger.error(t.getMessage)
            case Level.LIFECYCLE => logger.info(t.getMessage)
            case Level.PROGRESS  => logger.ticker(t.getMessage)
            case Level.INFO      => logger.debug(t.getMessage)
            case Level.DEBUG     => logger.debug(t.getMessage)
          }
      )

    JavaContainerBuilder
      .from(baseImage)
      .setMainClass(mainClass)
      .addDependencies(deps.asJava)
      .addProjectDependencies(projectDeps.asJava)
      .addJvmFlags(jvmFlags.asJava)
      .toContainerBuilder
      .setLabels(labels.asJava)
      .containerize(cont2)
  }
}

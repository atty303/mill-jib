package io.github.atty303.mill.jib

import io.github.atty303.mill.jib.worker.{JibWorker, JibWorkerManager}
import mill.api.{Ctx, PathRef}

import java.net.{URL, URLClassLoader}

class JibInJvmWorkerManager(ctx: Ctx.Log) extends JibWorkerManager {
  private[this] var workerCache: Map[Seq[PathRef], (JibWorker, Int)] = Map.empty

  def get(toolsClasspath: Seq[PathRef])(implicit ctx: Ctx): JibWorker = {
    val (worker, count) = workerCache.get(toolsClasspath) match {
      case Some((w, count)) =>
        ctx.log.debug(
          s"Reusing existing JibWorker for classpath: ${toolsClasspath}"
        )
        w -> count
      case None =>
        ctx.log.debug(
          s"Creating Classloader with classpath: [${toolsClasspath}]"
        )
        val classLoader = new URLClassLoader(
          toolsClasspath.map(_.path.toNIO.toUri.toURL()).toArray[URL],
          getClass.getClassLoader
        )

        ctx.log.debug(s"Creating JibWorker for classpath: ${toolsClasspath}")
        val className =
          classOf[JibWorker].getPackage.getName + ".impl.JibInJvmWorker"
        val impl = classLoader.loadClass(className)
        val worker =
          impl.getDeclaredConstructor().newInstance().asInstanceOf[JibWorker]
        if (worker.getClass.getClassLoader != classLoader) {
          ctx.log.error(
            """Worker not loaded from worker classloader.
              |You should not add the mill-jib JAR to the mill build classpath""".stripMargin
          )
        }
        if (
          worker.getClass.getClassLoader == classOf[JibWorker].getClassLoader
        ) {
          ctx.log.error(
            "Worker classloader used to load interface and implementation"
          )
        }
        worker -> 0
    }
    workerCache += toolsClasspath -> (worker -> (1 + count))
    worker
  }
}

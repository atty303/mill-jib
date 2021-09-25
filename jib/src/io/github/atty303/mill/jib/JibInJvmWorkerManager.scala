package io.github.atty303.mill.jib

import com.linkedin.cytodynamics.matcher.GlobMatcher
import com.linkedin.cytodynamics.nucleus.{
  DelegateRelationshipBuilder,
  IsolationLevel,
  LoaderBuilder,
  OriginRestriction
}
import io.github.atty303.mill.jib.worker.api.{JibWorker, JibWorkerManager}
import mill.api.{Ctx, PathRef}

import java.net.{URI, URL, URLClassLoader}
import scala.jdk.CollectionConverters._

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
//        val classLoader = LoaderBuilder
//          .anIsolatingLoader()
//          .withClasspath(toolsClasspath.map(_.path.toNIO.toUri).toList.asJava)
//          .withParentRelationship(
//            DelegateRelationshipBuilder
//              .builder()
//              .withDelegateClassLoader(classOf[JibWorker].getClassLoader)
//              .withIsolationLevel(IsolationLevel.NONE)
//              .addDelegatePreferredResourcePredicate(
//                new GlobMatcher("io.github.atty303.mill.jib.worker.JibWorker")
//              )
//              .addWhitelistedClassPredicate(
//                new GlobMatcher("io.github.atty303.mill.jib.*")
//              )
//              .build()
//          )
//          .withOriginRestriction(OriginRestriction.allowByDefault())
//          .build()
        val classLoader = new JibClassLoader(
          toolsClasspath.map(_.path.toNIO.toUri.toURL).toArray,
          getClass.getClassLoader
        )

        ctx.log.debug(s"Creating JibWorker for classpath: ${toolsClasspath}")
        val className = "io.github.atty303.mill.jib.worker.impl.JibInJvmWorker"
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

// Need for isolating guava...
class JibClassLoader(classpath: Array[URL], parent: ClassLoader)
    extends URLClassLoader(classpath, parent) {
  override def loadClass(name: String, resolve: Boolean): Class[_] = {
    getClassLoadingLock(name).synchronized {
      if (name.startsWith("io.github.atty303.mill.jib.worker.api.")) {
        parent.loadClass(name)
      } else {
        try {
          findClass(name)
        } catch {
          case _: ClassNotFoundException =>
            parent.loadClass(name)
        }
      }
    }
  }
}

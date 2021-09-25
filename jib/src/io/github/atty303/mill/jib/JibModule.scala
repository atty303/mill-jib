package io.github.atty303.mill.jib

import io.github.atty303.mill.jib
import io.github.atty303.mill.jib.worker.{Image, JibWorker}
import mill._
import mill.define.{Command, Task}
import mill.scalalib._

trait JibModule { outer: JavaModule =>
  trait DockerConfig extends mill.Module {
    def jibWorkerModule: JibWorkerModule = JibWorkerModule

    /** Tags that should be applied to the built image
      * In the standard registry/repository:tag format
      */
    def image: T[String] = T(outer.artifactName())

    def additionalTags: T[Seq[String]] = T(Seq.empty[String])

    def labels: T[Map[String, String]] = T(Map.empty[String, String])

    def baseImage: T[String] = "gcr.io/distroless/java:latest"

    def jvmFlags: T[Seq[String]] = T(Seq.empty[String])

    def ivyJars: T[Agg[PathRef]] = T {
      resolveDeps(T.task { runIvyDeps() ++ transitiveIvyDeps() })()
    }

    def projectJars: Task[Seq[(String, PathRef)]] =
      T.traverse(transitiveModuleDeps) { m =>
        T.task(m.artifactId() -> m.jar())
      }

    def registryUsername: T[String] = ""
    def registryPassword: T[String] = ""

    def jibVersion: T[String] = "0.20.0"
    def jibToolsDeps: T[Agg[Dep]] = T {
      Agg(
        ivy"com.google.cloud.tools:jib-core:${jibVersion()}",
        ivy"${jib.Versions.millJibWorkerImplIvyDep}"
      )
    }

    def jibToolsClasspath: T[Agg[PathRef]] = T {
      resolveDeps(jibToolsDeps)
    }

    protected def jibWorkerTask: Task[JibWorker] = T.task {
      jibWorkerModule
        .jibWorkerManager()
        .get(jibToolsClasspath().iterator.to(Seq))
    }

    def renamedProjectJars: T[Seq[PathRef]] = T {
      val dest = T.ctx().dest
      projectJars().map { case (artifactId, jar) =>
        val d = dest / s"${artifactId}.jar"
        os.copy(jar.path, d)
        PathRef(d)
      }
    }

    def buildLocal(): Command[Unit] = T.command {
      jibWorkerTask().build(
        Image.DockerDaemonImage(image()),
        additionalTags(),
        baseImage(),
        outer.finalMainClass(),
        ivyJars().map(_.path.toNIO).iterator.to(Seq),
        renamedProjectJars().map(_.path.toNIO),
        jvmFlags(),
        labels()
      )
    }

    def buildPush(): Command[Unit] = T.command {
      jibWorkerTask().build(
        Image.RegistryImage(image(), registryUsername(), registryPassword()),
        additionalTags(),
        baseImage(),
        outer.finalMainClass(),
        ivyJars().map(_.path.toNIO).iterator.to(Seq),
        renamedProjectJars().map(_.path.toNIO),
        jvmFlags(),
        labels()
      )
    }
  }
}

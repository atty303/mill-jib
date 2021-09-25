import mill._
import mill.define.Target
import scalalib._
import publish._

trait MyPublishModule extends PublishModule {
  override def artifactName = "mill-" + super.artifactName()
  override def publishVersion = "0.1.0"
  override def pomSettings = PomSettings(
    description = "Dockerize java applications on mill builds",
    organization = "io.github.atty303",
    url = "https://github.com/atty303/mill-jib",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github("atty303", "mill-jib"),
    developers =
      Seq(Developer("atty303", "Koji AGAWA", "https://github.com/atty303"))
  )
}

trait MyModule extends MyPublishModule with ScalaModule {
  def scalaVersion = "2.13.6"
}

def millVersion = "0.9.9"

object api extends MyModule {
  override def artifactName = "io.github.atty303.mill.jib-api"
  override def compileIvyDeps = Agg(
    ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  )
}

object worker extends MyModule {
  override def artifactName = "io.github.atty303.mill.jib-worker"
  override def moduleDeps = Seq(api)
  override def compileIvyDeps = Agg(
    ivy"com.lihaoyi::mill-scalalib:${millVersion}",
    ivy"com.google.cloud.tools:jib-core:0.20.0"
  )
}

object jib extends MyModule {
  override def artifactName = "io.github.atty303.mill.jib"
  override def moduleDeps = Seq(api)

  override def compileIvyDeps = Agg(
    ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  )

  override def generatedSources: T[Seq[PathRef]] = T {
    super.generatedSources() ++ {
      val dest = T.ctx().dest
      val millJibWorkerImplIvyDep =
        s"${worker.pomSettings().organization}:${worker.artifactId()}:${worker.publishVersion()}"
      val body =
        s"""package io.github.atty303.mill.jib
           |
           |/**
           | * Build-time generated versions file.
           | */
           |object Versions {
           |  /** The mill-aspectj version. */
           |  val millAspectjVersion = "${publishVersion()}"
           |  /** The mill API version used to build mill-jib. */
           |  val buildTimeMillVersion = "${millVersion}"
           |  /** The ivy dependency holding the mill jib worker impl. */
           |  val millJibWorkerImplIvyDep = "${millJibWorkerImplIvyDep}"
           |}
           |""".stripMargin

      os.write(dest / "Versions.scala", body)

      Seq(PathRef(dest))
    }
  }
}

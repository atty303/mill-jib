import mill._
import scalalib._
import publish._

trait MyPublishModule extends PublishModule {
  def artifactName = "mill-" + super.artifactName()
  def publishVersion = "0.1.0"
  def pomSettings = PomSettings(
    description = "Dockerize java applications on Mill builds",
    organization = "io.github.atty303",
    url = "https://github.com/atty303/mill-jib",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github("atty303", "mill-jib"),
    developers = Seq(
      Developer("atty303", "Koji AGAWA", "https://github.com/atty303")
    )
  )
}

trait MyModule extends MyPublishModule with ScalaModule {
  def scalaVersion = "2.13.6"
}

object `jib-docker` extends MyModule {
  def moduleDeps = Seq(`jib-docker`.api)

  def millVersion = "0.9.9"

  def compileIvyDeps = Agg(
    ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  )

  object api extends MyModule {
  }

  object worker extends MyModule {
    def moduleDeps = Seq(`jib-docker`.api)
    def ivyDeps = Agg(ivy"com.google.cloud.tools:jib-core:0.20.0")
  }
}

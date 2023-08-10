package io.github.atty303.mill.jib

import io.github.atty303.mill.jib
import io.github.atty303.mill.jib.worker.api.{
  ContainerConfig,
  Credentials,
  FileEntry,
  Image,
  ImageFormat,
  JibWorker,
  Platform,
  Port
}
import mill._
import mill.define.{Command, Task}
import mill.scalalib._

import java.time.Instant

trait JibModule { outer: JavaModule =>
  trait DockerConfig extends Module {
    def jibWorkerModule: JibWorkerModule = JibWorkerModule

    /** Tags that should be applied to the built image
      * In the standard registry/repository:tag format
      */
    def image: T[String] = T(outer.artifactName())

    def additionalTags: T[Seq[String]] = T(Seq.empty[String])

    def baseImage: T[String] = T("gcr.io/distroless/java:latest")

    /** Sets the container entrypoint. This is the beginning of the command that is run when the
      * container starts. {@link #programArguments} sets additional tokens.
      *
      * <p>This is similar to
      * <a href="https://docs.docker.com/engine/reference/builder/#exec-form-entrypoint-example">
      *   `ENTRYPOINT` in Dockerfiles</a> or `command` in the
      * <a href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#container-v1-core">
      *   Kubernetes Container spec</a>.
      *
      * @return a list of the entrypoint command
      */
    def entrypoint: T[Seq[String]] = T(Seq.empty[String])

    /** Sets the container entrypoint program arguments. These are additional tokens added to the end
      * of the entrypoint command.
      *
      * <p>This is similar to <a href="https://docs.docker.com/engine/reference/builder/#cmd">
      *   `CMD` in Dockerfiles</a> or `args` in the
      * <a href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#container-v1-core">
      *   Kubernetes Container spec</a>.
      *
      * <p>For example, if the entrypoint was `myprogram --flag subcommand` and program arguments
      * were `hello world`, then the command that run when the container starts is
      * `myprogram --flag subcommand hello world`.
      *
      * @return a list of program argument tokens
      */
    def programArguments: T[Seq[String]] = T(Seq.empty[String])

    /** Sets the container environment. These environment variables are available to the program
      * launched by the container entrypoint command.
      *
      * <p>This is similar to
      * <a href="https://docs.docker.com/engine/reference/builder/#env">`ENV` in Dockerfiles</a> or `env` in the
      * <a href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#container-v1-core">
      *   Kubernetes Container spec</a>.
      *
      * @return a map of environment variable names to values
      */
    def environment: T[Map[String, String]] = T(Map.empty[String, String])

    /** Sets the directories that may hold externally mounted volumes.
      *
      * <p>This is similar to <a href="https://docs.docker.com/engine/reference/builder/#volume">
      *   `VOLUME` in Dockerfiles</a>.
      *
      * @return the directory paths on the container filesystem to set as volumes
      */
    def volumes: T[Set[String]] = T(Set.empty[String])

    /** Sets the ports to expose from the container. Ports exposed will allow ingress traffic.
      *
      * <p>Use {@link Port#tcp} to expose a port for TCP traffic and {@link Port#udp} to expose a port
      * for UDP traffic.
      *
      * <p>This is similar to <a href="https://docs.docker.com/engine/reference/builder/#expose">
      *   `EXPOSE` in Dockerfiles</a> or `ports` in the
      * <a href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.11/#container-v1-core">
      *   Kubernetes Container spec</a>.
      *
      * @return the ports to expose
      */
    def exposedPorts: Task[Set[Port]] = T.task(Set.empty[Port])

    /** Sets the labels for the container.
      *
      * <p>This is similar to <a href="https://docs.docker.com/engine/reference/builder/#label">
      *   `LABEL` in Dockerfiles</a>.
      *
      * @return a map of label keys to values
      */
    def labels: T[Map[String, String]] = T(Map.empty[String, String])

    /** Sets the format to build the container image as. Use {@link ImageFormat#Docker} for Docker V2.2
      * or {@link ImageFormat#OCI} for OCI.
      *
      * @return the {@link ImageFormat}
      */
    def imageFormat: Task[ImageFormat] = T.task(ImageFormat.Docker)

    /** Sets the container image creation time. The default is {@link Instant#EPOCH}.
      *
      * @return the container image creation time
      */
    def creationTime: Task[Instant] = T.task(Instant.EPOCH)

    /** Sets a desired platform (properties including OS and architecture) list. If the base image
      * reference is a Docker manifest list or an OCI image index, an image builder may select the base
      * images matching the given platforms. If the base image reference is an image manifest, an image
      * builder may ignore the given platforms and use the platform of the base image or may decide to
      * raise on error.
      *
      * <p>Note that a new container builder starts with "amd64/linux" as the default platform.
      *
      * @return list of platforms to select base images in case of a manifest list
      */
    def platforms: Task[Set[Platform]] = T.task(Set.empty[Platform])

    /** Sets the user and group to run the container as. `user` can be a username or UID along
      * with an optional groupname or GID.
      *
      * <p>The following are valid formats for `user`
      *
      * <ul>
      *   <li>`user`
      *   <li>`uid`
      *   <li>`:group`
      *   <li>`:gid`
      *   <li>`user:group`
      *   <li>`uid:gid`
      *   <li>`uid:group`
      *   <li>`user:gid`
      * </ul>
      *
      * @return the user to run the container as
      */
    def user: T[Option[String]] = T(None)

    /** Sets the working directory in the container.
      *
      * @return the working directory
      */
    def workingDirectory: T[Option[String]] = T(None)

    def additionalLayer: T[Map[os.Path, String]] =
      T.input(Map.empty[os.Path, String])
    def additionalLayerFileEntries: Task[Seq[FileEntry]] = T.task {
      additionalLayer().toSeq.map { case (path, target) =>
        FileEntry(path, target)
      }
    }

    def containerConfig: Task[ContainerConfig] = T.task {
      ContainerConfig(
        entrypoint(),
        programArguments(),
        environment(),
        volumes(),
        exposedPorts(),
        labels(),
        imageFormat(),
        creationTime(),
        platforms(),
        user(),
        workingDirectory(),
        Seq(("extra", additionalLayerFileEntries()))
      )
    }

    def jvmFlags: T[Seq[String]] = T(Seq.empty[String])

    def projectJars: Task[Seq[(String, PathRef)]] =
      T.traverse(transitiveModuleDeps) { m =>
        T.task(m.artifactId() -> m.jar())
      }

    def baseUsername: T[Option[String]] =
      T.input(T.env.get("JIB_BASE_IMAGE_USERNAME"))
    def basePassword: T[Option[String]] =
      T.input(T.env.get("JIB_BASE_IMAGE_PASSWORD"))
    def targetUsername: T[Option[String]] =
      T.input(T.env.get("JIB_TARGET_IMAGE_USERNAME"))
    def targetPassword: T[Option[String]] =
      T.input(T.env.get("JIB_TARGET_IMAGE_PASSWORD"))

    def credentials: Task[Credentials] = T.task {
      Credentials(
        baseUsername(),
        basePassword(),
        targetUsername(),
        targetPassword()
      )
    }

    def jibVersion: T[String] = "0.24.0"
    def jibToolsDeps: T[Agg[Dep]] = T {
      Agg(
        ivy"com.google.cloud.tools:jib-core:${jibVersion()}",
        ivy"${jib.Versions.millJibWorkerImplIvyDep}"
      )
    }

    def jibToolsClasspath: T[Agg[PathRef]] = T {
      resolveDeps(jibToolsDeps.map(_.map(_.bindDep("", "", ""))))
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

    def buildDocker(): Command[Unit] = T.command {
      jibWorkerTask().build(
        T.ctx.log,
        credentials(),
        Image.DockerDaemonImage(image()),
        additionalTags(),
        baseImage(),
        outer.finalMainClass(),
        resolvedRunIvyDeps().map(_.path.toNIO).iterator.to(Seq),
        renamedProjectJars().map(_.path.toNIO),
        jvmFlags(),
        containerConfig()
      )
    }

    def buildImage(): Command[Unit] = T.command {
      jibWorkerTask().build(
        T.ctx.log,
        credentials(),
        Image.RegistryImage(image()),
        additionalTags(),
        baseImage(),
        outer.finalMainClass(),
        resolvedRunIvyDeps().map(_.path.toNIO).iterator.to(Seq),
        renamedProjectJars().map(_.path.toNIO),
        jvmFlags(),
        containerConfig()
      )
    }
  }
}

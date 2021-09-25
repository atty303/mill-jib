package io.github.atty303.mill.jib

import io.github.atty303.mill.jib.worker.api.JibWorkerManager
import mill.T
import mill.define.{Discover, ExternalModule, Module, Worker}

trait JibWorkerModule extends Module {
  def jibWorkerManager: Worker[JibWorkerManager] = T.worker {
    new JibInJvmWorkerManager(T.ctx())
  }
}

object JibWorkerModule extends ExternalModule with JibWorkerModule {
  lazy val millDiscover = Discover[this.type]
}

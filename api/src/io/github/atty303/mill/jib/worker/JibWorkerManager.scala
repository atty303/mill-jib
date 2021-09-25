package io.github.atty303.mill.jib.worker

import mill.api.{Ctx, PathRef}

trait JibWorkerManager {
  def get(toolClasspath: Seq[PathRef])(implicit ctx: Ctx): JibWorker
}

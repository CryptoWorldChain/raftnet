package org.brewchain.raftnet.tasks

import org.fc.brewchain.p22p.node.Network
import org.fc.brewchain.p22p.utils.LogHelper
import org.brewchain.raftnet.pbgens.Raftnet.PSJoin
import onight.tfw.outils.serialize.UUIDGenerator
import onight.tfw.async.CallBack
import onight.tfw.otransio.api.beans.FramePacket
import org.brewchain.raftnet.pbgens.Raftnet.PRetJoin
import org.brewchain.raftnet.utils.RConfig
import org.brewchain.raftnet.pbgens.Raftnet.PRaftNode
import org.brewchain.raftnet.pbgens.Raftnet.PSSyncEntries
import org.brewchain.raftnet.pbgens.Raftnet.PRetSyncEntries

import scala.collection.JavaConversions._
import org.brewchain.raftnet.pbgens.Raftnet.PLogEntry
import org.brewchain.raftnet.Daos
import org.brewchain.bcapi.gens.Oentity.OValue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
object LogSync extends LogHelper {
  
  
  def trySyncLogs(maxCommitIdx: Long, fastNodeID: String)(implicit network: Network): Unit = {
    val cn = RSM.instance.cur_rnode;
    //
    log.debug("get quorum Reply:RN= " + RSM.raftFollowNetByUID.size + ",DN=" + network.directNodes.size)
    //request log.
    val pagecount =
      ((maxCommitIdx - cn.getCommitIndex) / RConfig.SYNCLOG_PAGE_SIZE).asInstanceOf[Int]
    +(if ((maxCommitIdx - cn.getCommitIndex) % RConfig.SYNCLOG_PAGE_SIZE == 0) 1 else 0)

    //        val cdlcount = Math.min(RConfig.SYNCLOG_MAX_RUNNER, pagecount)
    var cc = cn.getCommitIndex + 1;
    val runCounter = new AtomicLong(0);
    while (cc < maxCommitIdx) {
      val runner = RTask_SyncLog(startIdx = cc, endIdx = Math.min(cc + RConfig.SYNCLOG_PAGE_SIZE - 1, maxCommitIdx),
        network = network, fastNodeID, runCounter)
      cc += RConfig.SYNCLOG_PAGE_SIZE
      runCounter.incrementAndGet();
      while (runCounter.get >= RConfig.SYNCLOG_MAX_RUNNER) {
        //wait... for next runner
        try {
          log.debug("waiting for runner:cur=" + runCounter.get)
          this.synchronized(this.wait(RConfig.SYNCLOG_WAITSEC_NEXTRUN))
        } catch {
          case t: InterruptedException =>
          case e: Throwable =>
        }
      }
      Scheduler.runOnce(runner);
    }

    //
  }
}
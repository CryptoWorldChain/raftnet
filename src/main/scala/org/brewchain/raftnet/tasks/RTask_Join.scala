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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

//获取其他节点的term和logidx，commitidx
object RTask_Join extends LogHelper {
  def runOnce(implicit network: Network) = {
    Thread.currentThread().setName("RTask_Join");
    val join = PSJoin.newBuilder().setRn(RSM.curRN()).build();
    val msgid = UUIDGenerator.generate();
    val cn = RSM.instance.cur_rnode;
    var fastNode: PRaftNode = null;
    var minCost: Long = Long.MaxValue;
    var maxCommitIdx: Long = 0;
    network.directNodes.map { n =>
      val start = System.currentTimeMillis();
      network.sendMessage("JINRAF", join, n, new CallBack[FramePacket] {
        def onSuccess(fp: FramePacket) = {
          log.debug("send JINPZP success:to " + n.uri + ",body=" + fp.getBody)
          val end = System.currentTimeMillis();
          val retjoin = PRetJoin.newBuilder().mergeFrom(fp.getBody);
          if (retjoin.getRetCode() == 0) { //same message
            if (retjoin.getRn.getCommitIndex > maxCommitIdx) {
              fastNode = retjoin.getRn;
            } else if (retjoin.getRn.getCommitIndex >= maxCommitIdx) {
              if (end - start < minCost) { //set the fast node
                minCost = end - start
                fastNode = retjoin.getRn;
              }
            }
            RSM.raftFollowNetByUID.put(retjoin.getRn.getBcuid, retjoin.getRn);
          }
          log.debug("get nodes:count=" + retjoin.getNodesCount);
        }
        def onFailed(e: java.lang.Exception, fp: FramePacket) {
          log.debug("send JINPZP ERROR " + n.uri + ",e=" + e.getMessage, e)
        }
      })
    }
    if (fastNode != null && RSM.raftFollowNetByUID.size >= network.directNodes.size * RConfig.VOTE_QUORUM_RATIO / 100
      && cn.getCommitIndex < maxCommitIdx) {
      LogSync.trySyncLogs(maxCommitIdx, fastNode.getBcuid);
    }
  }

}

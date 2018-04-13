package org.brewchain.raftnet.tasks

import org.fc.brewchain.p22p.node.Network
import org.fc.brewchain.p22p.utils.LogHelper
import org.brewchain.raftnet.pbgens.Raftnet.RaftState
import org.brewchain.raftnet.Daos
import org.brewchain.raftnet.pbgens.Raftnet.PRaftNode
import org.brewchain.raftnet.pbgens.Raftnet.PRaftNodeOrBuilder
import org.brewchain.bcapi.gens.Oentity.OValue
import org.apache.commons.lang3.StringUtils
import org.fc.brewchain.p22p.node.Node
import org.brewchain.raftnet.utils.RConfig
import scala.collection.mutable.Map

//投票决定当前的节点
case class RaftStateManager(network: Network) extends SRunner with LogHelper {
  def getName() = "RSM"
  val RAFT_NODE_DB_KEY = "CURRENT_RAFT_KEY";
  var cur_rnode: PRaftNode.Builder = PRaftNode.newBuilder()
  var imPRnode: PRaftNode = cur_rnode.build()

  def updateLastApplidId(lastApplied: Long): Boolean = {
    this.synchronized({
      if (lastApplied > cur_rnode.getLastApplied) {
        cur_rnode.setLastApplied(lastApplied)
        true
      } else {
        false
      }
    })
  }
  def loadNodeFromDB(): PRaftNode.Builder = {
    val ov = Daos.raftdb.get(RAFT_NODE_DB_KEY).get
    val root_node = network.root();

    if (ov == null) {
      cur_rnode.setAddress(root_node.v_address).setBcuid(root_node.bcuid)
      Daos.raftdb.put(RAFT_NODE_DB_KEY,
        OValue.newBuilder().setExtdata(cur_rnode.build().toByteString()).build())
    } else {
      cur_rnode.mergeFrom(ov.getExtdata)
      if (!StringUtils.equals(cur_rnode.getAddress, root_node.v_address)
        || !StringUtils.equals(cur_rnode.getBcuid, root_node.bcuid)) {
        log.warn("load from raftnode info not equals with pzp node:" + cur_rnode + ",root=" + root_node)
      } else {
        log.info("load from db:OK" + cur_rnode)
      }
    }
    imPRnode = cur_rnode.build();
    cur_rnode
  }
  def runOnce() = {
    Thread.currentThread().setName("RaftStateManager");
    implicit val _net = network
    MDCSetBCUID(network);
    try {
      //      RaftStateManager.rsm = this;
      cur_rnode.getState match {
        case RaftState.RS_INIT =>
          //tell other I will join
          //  network.wallMessage("", body, messageId)
          if (RSM.raftFollowNetByUID.size == 0) {
            RTask_Join.runOnce
          } else if (RSM.raftFollowNetByUID.size >= network.directNodes.size * RConfig.VOTE_QUORUM_RATIO / 100) {
            //
            log.debug("get quorum Reply:RN= " + RSM.raftFollowNetByUID.size + ",DN=" + network.directNodes.size)
            val (maxterm, maxapply, maxcommitIdx) = RSM.raftFollowNetByUID.values.foldLeft((0L, 0L, 0L))((A, n) =>
              (Math.max(A._1, n.getCurTerm), Math.max(A._1, n.getLastApplied), Math.max(A._1, n.getCommitIndex)))
            if (cur_rnode.getCommitIndex < maxcommitIdx) {
              //request log.

            }
          } else {

            log.debug("not get quorum number:RN= " + RSM.raftFollowNetByUID.size + ",DN=" + network.directNodes.size)
          }

        case RaftState.RS_FOLLOWER =>
        //time out to elect candidate

        case RaftState.RS_CANDIDATE =>
        //check vote result

        case RaftState.RS_LEADER =>
        //time out to become follower

        case _ =>
          log.warn("unknow State:" + cur_rnode.getState);

      }

    } catch {
      case e: Throwable =>
        log.debug("JoinNetwork :Error", e);
    } finally {
      log.debug("JoinNetwork :[END]")
    }
  }
}

object RSM {
  var instance: RaftStateManager = RaftStateManager(null);
  def raftNet(): Network = instance.network;
  def curRN(): PRaftNode = instance.imPRnode
  val raftFollowNetByUID: Map[String, PRaftNode] = Map.empty[String, PRaftNode];
  def isReady(): Boolean = {
    instance.network != null &&
      instance.cur_rnode.getStateValue > RaftState.RS_INIT_VALUE
  }

}
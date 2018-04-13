package org.brewchain.raftnet.action

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import lombok.extern.slf4j.Slf4j
import onight.oapi.scala.commons.LService
import onight.oapi.scala.commons.PBUtils
import onight.oapi.scala.traits.OLog
import onight.osgi.annotation.NActorProvider
import onight.tfw.async.CompleteHandler
import onight.tfw.otransio.api.PacketHelper
import onight.tfw.otransio.api.beans.FramePacket
import org.fc.brewchain.bcapi.exception.FBSException
import org.apache.commons.lang3.StringUtils
import java.util.HashSet
import onight.tfw.outils.serialize.UUIDGenerator
import scala.collection.JavaConversions._
import org.apache.commons.codec.binary.Base64
import java.net.URL
import org.brewchain.bcapi.utils.PacketIMHelper._
import org.brewchain.raftnet.pbgens.Raftnet.PSJoin
import org.brewchain.raftnet.PSMRaftNet
import org.fc.brewchain.p22p.utils.LogHelper
import org.fc.brewchain.p22p.action.PMNodeHelper
import org.brewchain.raftnet.pbgens.Raftnet.PRetJoin
import org.brewchain.raftnet.pbgens.Raftnet.PCommand
import org.brewchain.raftnet.pbgens.Raftnet.PSRequestVote
import org.brewchain.raftnet.pbgens.Raftnet.PRetRequestVote
import org.brewchain.raftnet.pbgens.Raftnet.PSAppendEntries
import org.brewchain.raftnet.pbgens.Raftnet.PRetAppendEntries

@NActorProvider
@Slf4j
object PRaftAppendEntries extends PSMRaftNet[PSAppendEntries] {
  override def service = PRaftAppendEntriesService
}

//
// http://localhost:8000/fbs/xdn/pbget.do?bd=
object PRaftAppendEntriesService extends LogHelper with PBUtils with LService[PSAppendEntries] with PMNodeHelper {
  override def onPBPacket(pack: FramePacket, pbo: PSAppendEntries, handler: CompleteHandler) = {
    log.debug("JoinService::" + pack.getFrom())
    var ret = PRetAppendEntries.newBuilder();
    val network = networkByID("raft")
    if (network == null) {
//      ret.setRetCode(-1).setRetMessage("unknow network:Raft")
      handler.onFinished(PacketHelper.toPBReturn(pack, ret.build()))
    } else {
      try {
        MDCSetBCUID(network)

      } catch {
        case e: FBSException => {
          ret.clear()
        }
        case t: Throwable => {
          log.error("error:", t);
          ret.clear()
        }
      } finally {
        handler.onFinished(PacketHelper.toPBReturn(pack, ret.build()))
      }
    }
  }
  //  override def getCmds(): Array[String] = Array(PWCommand.LST.name())
  override def cmd: String = PCommand.LOG.name();
}

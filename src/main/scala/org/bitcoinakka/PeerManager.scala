package org.bitcoinakka

import java.net.InetSocketAddress

import scala.concurrent.duration._
import akka.actor._
import akka.io.{IO, Tcp}
import akka.io.Tcp.{Connected, ConnectionClosed}
import org.slf4j.LoggerFactory
import BitcoinMessage._

class Peer(connection: ActorRef, local: InetSocketAddress, remote: InetSocketAddress) extends FSM[Peer.State, Peer.Data] with ActorLogging {
  import Peer._

  val messageHandler = context.actorOf(Props(new MessageHandlerActor(connection)))

  val myHeight = 0

  startWith(Initial, HandshakeData(None, false))

  setTimer("handshake", StateTimeout, timeout, false)
  messageHandler ! Version(BitcoinMessage.version, local, remote, 0L, "Bitcoin-akka", myHeight, 1.toByte)

  when(Initial) {
    case Event(v: Version, d: HandshakeData) =>
      messageHandler ! Verack
      checkHandshakeFinished(d copy (height = Some(v.height)))

    case Event(Verack, d: HandshakeData) =>
      checkHandshakeFinished(d copy (ackReceived = true))
  }

  when(Ready) {
    case Event(gh: GetHeaders, _) =>
      log.info(s"Peer received request for headers ${hashToString(gh.hashes.head)}")
      messageHandler ! gh
      setTimer("getheaders", StateTimeout, timeout, false)
      stay using ReplyToData(sender)

    case Event(gb: GetBlocks, _) =>
      val gbMessage = GetBlockData(gb.hsd.map(_.blockHeader.hash))
      messageHandler ! gbMessage
      log.info(s"Peer received request to download ${gb.hsd.map(hsd => hashToString(hsd.blockHeader.hash))}")
      setTimer("getblocks", StateTimeout, timeout, false)
      stay using ReplyToData(sender)

    case Event(headers: Headers, ReplyToData(s)) =>
      log.info(s"Headers received (${headers.blockHeaders.length})")
      cancelTimer("getheaders")
      s ! headers
      stay

    case Event(block: Block, ReplyToData(s)) =>
      log.info(s"Block received ${hashToString(block.header.hash)}")
      s ! block
      setTimer("getblocks", StateTimeout, timeout, false)
      stay

    case Event(GetBlocksFinished, _) =>
      cancelTimer("getblocks")
      stay
  }

  whenUnhandled {
    case Event(bm: BitcoinMessage, _) =>
      log.info(s"Received ${bm}")
      stay

    case Event(_: ConnectionClosed, _) =>
      log.info("Peer disconnected")
      context stop self
      stay

    case Event(StateTimeout, _) =>
      log.info("Peer timeout")
      context stop self
      stay
  }

  onTransition {
    case Initial -> Ready =>
      log.info("Handshake done")
      cancelTimer("handshake")
      context.parent ! Handshaked(nextStateData.asInstanceOf[HandshakeData].height.get)
  }

  initialize()

  private def checkHandshakeFinished(d: HandshakeData) = d match {
    case HandshakeData(Some(height), true) =>
      goto(Ready)
    case _ =>
      stay using d
  }
}

object Peer {
  val timeout = 5.second

  case class GetBlocks(hsd: List[HeaderSyncData])
  case class Handshaked(height: Int)
  case object GetBlocksFinished

  trait State
  object Initial extends State
  object Ready extends State

  trait Data
  case class HandshakeData(height: Option[Int], ackReceived: Boolean) extends Data
  case class ReplyToData(replyTo: ActorRef) extends Data
}

class PeerManager extends Actor with ActorLogging {
  import PeerManager._
  import context.system

  def receive = {
    case ConnectToPeer(peerAddress) =>
      IO(Tcp) ! Tcp.Connect(peerAddress)

    case Connected(remote, local) =>
      log.info(s"Connected to ${remote}")
      val connection = sender
      val peer = context.actorOf(Props(new Peer(connection, local, remote)))
  }
}

object PeerManager extends App {
  case class ConnectToPeer(peerAddress: InetSocketAddress)

  val log = LoggerFactory.getLogger(getClass)
  implicit val system = ActorSystem()

  log.info("Hello, welcome to Bitcoin-akka")

  val peerManager = system.actorOf(Props(new PeerManager), "peermanager")
  peerManager ! PeerManager.ConnectToPeer(new InetSocketAddress("localhost", 9333))
}

class MessageHandlerActor(connection: ActorRef) extends Actor with MessageHandler with ActorLogging {
  connection ! Tcp.Register(self)
  def receive = {
    case Tcp.Received(data) => frame(data).flatMap(parse).foreach(context.parent ! _)
    case bm: BitcoinMessage => connection ! Tcp.Write(bm.toMessage())
    case other => context.parent ! other
  }

  private def parse(mh: MessageHeader): Option[BitcoinMessage] = {
    mh.command match {
      case "version" => Some(Version.parse(mh.payload))
      case "verack" => Some(Verack)
      case _ => None
    }
  }
}
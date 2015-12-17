import java.io.{FileInputStream, BufferedInputStream}
import java.security.MessageDigest
import javax.crypto.{KeyGenerator, SecretKey}
import javax.crypto.spec.SecretKeySpec

import ServerActor.DataManager
import akka.routing.BroadcastPool
import akka.util.Timeout
import com.sun.org.apache.xml.internal.security.utils.Base64
import shapeless.~>
import spray.http._

import scala.collection.parallel.mutable
import scala.concurrent.{Future, Await}
import scala.io.StdIn
import scala.util.{Random, Success, Failure}
import scala.concurrent.duration._
import akka.actor._
import akka.pattern.ask
import akka.event.Logging
import akka.io.IO
import spray.can.Http
import spray.client.pipelining._
import spray.util._
import DataCenter._
import crypto._
import protocol.defaults._

object Main extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("simple-spray-client")
  val log = Logging(system, getClass)

  if (args.length == 2) {
    val master = system actorOf(Props(new Master(args(0).toInt, args(1).toInt)), name = "Master")
    master ! MasterCase.RegisterUser()
    def shutdown(): Unit = {
      IO(Http).ask(Http.CloseAll)(1.second).await
      system.shutdown()
    }
  }
  else {
    val master = system actorOf(Props(new Master(0, 0)), name = "Master")
    master ! MasterCase.NextOperation()
  }
}

object MasterCase {
  case class RegisterUser()
  case class FinishRegisterUser(userId: String)
  case class AddFriends(num: Int)
  case class FinishAddFriends()
  case class FindStranger()
  case class UploadFile(userId: String, filename: String)
  case class FinishUploadFile()
  case class DownloadFile(userId: String, fileId: String)
  case class FinishGetFile()
  case class ShareFile(ownerId: String, fileId: String, inviteeId: String)
  case class FinishShareFile()
  case class ProcessOperation(args: Array[String])
  case class NextOperation()
}

object SimulatorCase {
  case class RegisterUser()
  case class GetUserProfile(userId: String)
  case class FindStranger(strangerId: String, hopNum: Int)
  case class FoundStranger(success: Boolean, hopNum: Int)
  case class FindStrangerByFriends(friendListJson: AnyRef, strangerId: String, hopNum: Int)
  case class UploadFile(userId: String, filename: String)
  case class GetFile(fileId: String)
  case class FetchFile(url: String, enAESKey: String)
  case class PreShareFile(fileId: String, inviteeId: String)
  case class ShareFile(fileId: String, inviteedId: String, enAESKey: String)
}

object DataStore {
  val userIdList = collection.mutable.MutableList[String]()
  val simulatorMap = collection.mutable.Map[String, ActorPath]()
  val rsaMap = collection.mutable.Map[String, RSA]()
  var usersNum = 0
}

class Master(val simulatorNum: Int, val friendNum: Int) extends Actor {
  var finishedNum = 0
  val log = Logging(context.system, this)
  var foundStrangerNum = 0
  var notFoundStrangerNum = 0
  var totalHopNum: BigInt = 0
  val simulators = collection.mutable.Map[String, ActorRef]()

  def getActorRef(userId: String) : ActorRef = {
    val actorRefOption = this.simulators.get(userId)
    actorRefOption match {
      case None =>
        val actorRef = context.actorOf(Props(new EncryptSimulator(userId)), "enSimulator-" + userId)
        this.simulators += (userId -> actorRef)
        return actorRef
      case Some(actorRef) =>
        return actorRef
    }
  }

  def processOperation(args: Array[String]) = {
    if (args.length >= 3) {
      val userId = args(0).toString
      val method = args(1).toString
      val file = args(2).toString
      if (method == "upload") {
        self ! MasterCase.UploadFile(userId, file)
      }
      else if (method == "download") {
        self ! MasterCase.DownloadFile(userId, file)
      }
      else if (method == "share" && args.length >= 4) {
        val inviteeId = args(3)
        self ! MasterCase.ShareFile(userId, file, inviteeId)
      }
    }
    else {
      println("Invalid arguments. Command could be `<userId> <upload/download> <file><inviteeId>`")
      self ! MasterCase.NextOperation()
    }
  }

  def receive = {
    case MasterCase.RegisterUser() =>
      println("=================================")
      println("Begin register users")
      println("=================================")
      for (i <- 0 until simulatorNum) {
        val simulator = context.actorOf(Props(new Simulator()), name = "simulator-" + i)
        simulator ! SimulatorCase.RegisterUser()
      }
    case MasterCase.FinishRegisterUser(userId: String) =>
      DataStore.userIdList += userId
      DataStore.simulatorMap += (userId -> sender().path)
      finishedNum += 1
      if (finishedNum == simulatorNum) {
        println("=================================")
        println(s"End register $simulatorNum users")
        println("=================================")
        finishedNum = 0
        self ! MasterCase.AddFriends(friendNum)
      }
    case MasterCase.AddFriends(num: Int) =>
      println("=================================")
      println("Begin add friends")
      println("=================================")
      for (i <- 0 until simulatorNum) {
        val simulator = context.actorSelection("simulator-" + i)
        simulator ! MasterCase.AddFriends(num)
      }
    case MasterCase.FinishAddFriends() =>
      finishedNum += 1
      if (finishedNum == simulatorNum) {
        println("=================================")
        println(s"End add $friendNum friends for each simulator")
        println("=================================")
        finishedNum = 0
        self ! MasterCase.FindStranger()
      }
    case MasterCase.FindStranger() =>
      println("=================================")
      println("Begin find strangers")
      println("=================================")
      for (i <- 0 until simulatorNum) {
        val simulator = context.actorSelection("simulator-" + i)
        val userId = DataStore.userIdList(util.Random.nextInt(DataStore.userIdList.size))
        simulator ! SimulatorCase.FindStranger(userId, 0)
      }
    case SimulatorCase.FoundStranger(success: Boolean, hopNum: Int) =>
      if (success == true) {
        foundStrangerNum += 1
        totalHopNum += hopNum
      }
      else {
        notFoundStrangerNum += 1
      }
      if (foundStrangerNum + notFoundStrangerNum == simulatorNum) {
        println("=================================")
        println(s"$foundStrangerNum simulators have found strangers")
        println(s"$notFoundStrangerNum simulators have not found strangers")
        println(s"The average friends to find a stanger is " + totalHopNum / foundStrangerNum)
        println("=================================")
        shutdown()
      }
    // test case for encryption
    case MasterCase.UploadFile(userId: String, filename: String) =>
      this.getActorRef(userId) ! SimulatorCase.UploadFile(userId, filename)
    case MasterCase.FinishUploadFile() =>
      self ! MasterCase.NextOperation()
    case MasterCase.DownloadFile(userId: String, fileId: String) =>
      this.getActorRef(userId) ! SimulatorCase.GetFile(fileId)
    case MasterCase.FinishGetFile() =>
      self ! MasterCase.NextOperation()
    case MasterCase.ShareFile(ownerId: String, fileId: String, inviteeId: String) =>
      this.getActorRef(ownerId) ! SimulatorCase.PreShareFile(fileId, inviteeId)
    case MasterCase.FinishShareFile() =>
      self ! MasterCase.NextOperation()
    case MasterCase.ProcessOperation(args: Array[String]) =>
      println("Receive process operation message.")
      this.processOperation(args)
    case MasterCase.NextOperation() =>
      val line = StdIn.readLine("Please enter your operation:  ", null)
      self ! MasterCase.ProcessOperation(line.split(" "))
    case _ =>
  }
  def shutdown() = {
    context.system.shutdown()
  }
}

class Simulator() extends Actor {
  var masterRef : ActorRef = null
  val email = java.util.UUID.randomUUID().toString + "@ufl.edu"
  var userId = ""
  val log = Logging(context.system, this)
  val hostAddress = "http://127.0.0.1:8080"
  var requireFriendsNum = 0
  var friendsNum = 0

  implicit val system = context.system
  import system.dispatcher
  import DataCenter.FacebookJsonProtocol._
  import spray.httpx.SprayJsonSupport._

  def registerUser(): Unit = {
    val pipeline = sendReceive ~> unmarshal[GoogleApiResult[String]]
    val responseFuture = pipeline {
      Post("http://127.0.0.1:8080/user/register", new AddUserJson(email))
    }
    responseFuture onComplete {
      case Success(GoogleApiResult(status, results)) =>
        if (status == "Failed")
          log.info(s"Register user [$email] failed")
        else {
          userId = results(0)
          log.info(s"Register user [$email] with userId [$userId]")
        }
        masterRef ! MasterCase.FinishRegisterUser(userId)
      case Success(somethingUnexpected) =>
        log.warning("Register user failed '{}'.", somethingUnexpected)
        masterRef ! MasterCase.FinishRegisterUser("")
      case Failure(error) =>
        log.error(error, "Couldn't get elevation")
        masterRef ! MasterCase.FinishRegisterUser("")
    }
  }

  def getUserProfile(userId: String): Unit = {
    val pipeline = sendReceive ~> unmarshal[GoogleApiResult[UserProfileJson]]
    val responseFuture = pipeline {
      Get("http://127.0.0.1:8080/user/" + userId)
    }
    responseFuture onComplete {
      case Success(GoogleApiResult(status: String, results: List[UserProfileJson])) =>
        log.info("The UserProfile is: {} m", List(0))
      case Success(somethingUnexpected) =>
        //log.warning("The Google API call was successful but returned something unexpected: '{}'.", somethingUnexpected)
      case Failure(error) =>
        //log.error(error, "Couldn't get elevation")
    }
  }

  def addFriends(num: Int) = {
    requireFriendsNum = num
    for (i <- 0 until num) {
      val userId = DataStore.userIdList(util.Random.nextInt(DataStore.userIdList.size))
      this.addFriend(userId)
    }
  }

  def addFriend(aUserId: String) = {
    val pipeline = sendReceive ~> unmarshal[GoogleApiResult[String]]
    val responseFuture = pipeline {
      Post("http://127.0.0.1:8080/friends", new AddFriendsJson(userId, aUserId))
    }
    responseFuture onComplete {
      case Success(GoogleApiResult(status: String, results: List[String])) =>
        if (status == "Succeed")
          log.info(s"Simulator[$userId] add friend succeed")
        else
          log.info(s"Simulator[$userId] add friend failed")
        friendsNum += 1
        DataStore.usersNum += 1
        log.info(s"Simulator[$userId] has add $friendsNum friends")
        log.info("Total finished add friends request num is ", DataStore.usersNum)
        if (friendsNum == requireFriendsNum) {
          masterRef ! MasterCase.FinishAddFriends()
        }
      case Success(somethingUnexpected) =>
        //log.warning(s"Simulator[$userId] add friend failed '{}'.", somethingUnexpected)
        friendsNum += 1
        if (friendsNum == requireFriendsNum) {
          masterRef ! MasterCase.FinishAddFriends()
        }
      case Failure(error) =>
        //log.error(error, "Couldn't get elevation")
        friendsNum += 1
        if (friendsNum == requireFriendsNum) {
          masterRef ! MasterCase.FinishAddFriends()
        }
    }
  }

  def getFriends(strangerId: String, hopNum: Int) = {
    val pipeline = sendReceive ~> unmarshal[GoogleApiResult[FriendListJson]]
    val responseFuture = pipeline {
      Get("http://127.0.0.1:8080/user/friendlist/" + userId)
    }
    val timeout = new Timeout(Duration.create(30, "seconds"))
    responseFuture onComplete {
      case Success(GoogleApiResult(status: String, results: List[FriendListJson])) =>
        //log.info("The FriendList is ", List(0))
        self ! SimulatorCase.FindStrangerByFriends(Some(results(0)), strangerId, hopNum)
      case Success(somethingUnexpected) =>
        //log.warning("The Google API call was successful but returned something unexpected: '{}'.", somethingUnexpected)
        self ! SimulatorCase.FindStranger(strangerId, hopNum)
      case Failure(error) =>
        //log.error(error, "Couldn't get elevation")
        self ! SimulatorCase.FindStranger(strangerId, hopNum)
    }
  }

  def findStranger(strangerId: String, hopNum: Int) = {
    if (userId == strangerId) {
      log.info(s"Has found stranger through $hopNum friends")
      masterRef ! SimulatorCase.FoundStranger(true, hopNum)
    }
    else {
      this.getFriends(strangerId, hopNum)
    }
  }

  def findStrangerByFriends(friendListJson: AnyRef, strangerId: String, hopNum: Int) = {
    friendListJson match {
      case None => masterRef !  SimulatorCase.FoundStranger(false, hopNum)
      case Some(FriendListJson(friendMap: Map[String, String])) =>
        log.info(s"Simulator[$userId] get friendlist $friendMap")
        friendMap.keys.foreach ((key: String) => {
          val simulatorPath = DataStore.simulatorMap.get(key)
          simulatorPath match {
            case None => masterRef !  SimulatorCase.FoundStranger(false, hopNum)
            case Some(simulatorPath) =>
              val simulator = context.actorSelection(simulatorPath.toString)
              if (hopNum > 10) masterRef !  SimulatorCase.FoundStranger(false, hopNum)
              else simulator ! SimulatorCase.FindStranger(strangerId, hopNum + 1)
          }
        })
    }
  }

  def shutdown(): Unit = {
    IO(Http).ask(Http.CloseAll)(1.second).await
    context.stop(self)
  }

  def receive = {
    case SimulatorCase.RegisterUser() =>
      masterRef = sender()
      this.registerUser()
    case SimulatorCase.GetUserProfile(userId: String) =>
      this.getUserProfile(userId)
    case MasterCase.AddFriends(num: Int) =>
      this.addFriends(num)
    case SimulatorCase.FindStranger(strangerId: String, hopNum: Int) =>
      log.info(s"Simulator[$userId] receive message to find [$strangerId]")
      this.findStranger(strangerId, hopNum)
    case SimulatorCase.FindStrangerByFriends(friendListJson: AnyRef, strangerId: String, hopNum: Int) =>
      this.findStrangerByFriends(friendListJson, strangerId, hopNum)
    case _ =>
  }
}

class EncryptSimulator(val userId: String) extends Actor {
  val log = Logging(context.system, this)
  var masterRef: ActorRef = null
  implicit val system = context.system
  val rsa = new RSA(12)
  DataStore.rsaMap += (this.userId -> this.rsa)

  import system.dispatcher
  import DataCenter.FacebookJsonProtocol._
  import spray.httpx.SprayJsonSupport._

  def uploadFile(userId: String, filename: String) = {
    val bis = new BufferedInputStream(new FileInputStream(filename))
    val bArray = Stream.continually(bis.read).takeWhile(-1 !=).map(_.toByte).toArray
    val secretKey = KeyGenerator.getInstance("AES").generateKey()
    val AESKey = Base64.encode(secretKey.getEncoded)
    println("Original AES is " + AESKey)
    val enAESKey = rsa.encrypt(AESKey.getBytes)
    var enAESKeyString = ""
    for (value <- enAESKey) {
      enAESKeyString += value + " "
    }
//    println("enAES is " + enAESKey)
//    println("enAESString is " + enAESKeyString)
//    val toBigIntArray = enAESKeyString.split(" ").map(x => BigInt(x))
//    var temp = ""
//    for (value <- toBigIntArray) {
//      temp += value + " "
//    }
//    println("temp is " + temp)
//    println("toBigIntArray is " + toBigIntArray)
//    val deAESKey = rsa.decrypt(toBigIntArray)
//    println("deAES is " + new String(deAESKey))
    val httpData = HttpData(AES.encrypt(bArray, AESKey))
    val httpEntity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, httpData).asInstanceOf[HttpEntity.NonEmpty]
    val formFile = FormFile("mytxt", httpEntity)
    val formData = MultipartFormData(Seq(
      BodyPart(formFile, "img")
    ))
    val pipeline = addHeader("userId", userId) ~> addHeader("encrypt", "1") ~> addHeader("AES", enAESKeyString) ~>
      sendReceive ~> unmarshal[GoogleApiResult[String]]
    val responseFuture = pipeline {
      Post("http://127.0.0.1:8080/uploadfile", formData)
    }
    responseFuture onComplete {
      case Success(GoogleApiResult(status: String, results: List[String])) =>
        if (status == "Succeed")
          log.info(s"Simulator[$userId] upload file [$filename] succeed")
        else
          log.info(s"Simulator[$userId] upload file [$filename] failed")
        this.masterRef ! MasterCase.FinishUploadFile()
      case Success(somethingUnexpected) =>
        //log.warning(s"Simulator[$userId] add friend failed '{}'.", somethingUnexpected)
        log.info(s"Simulator[$userId] upload file [$filename] failed")
        this.masterRef ! MasterCase.FinishUploadFile()
      case Failure(error) =>
        //log.error(error, "Couldn't get elevation")
        log.info(s"Simulator[$userId] upload file [$filename] failed")
        this.masterRef ! MasterCase.FinishUploadFile()
    }
  }

  def getFile(fileId: String) = {
    val pipeline = addHeader("userId", userId) ~> sendReceive ~> unmarshal[GoogleApiResult[FileJson]]
    val responseFuture = pipeline {
      Get("http://127.0.0.1:8080/getfile/" + fileId)
    }
    responseFuture onComplete {
      case Success(GoogleApiResult(status: String, results: List[FileJson])) =>
        if (status == "Succeed")
          log.info(s"Simulator[$userId] get file [$fileId] succeed")
          if (results.length > 0) {
            results(0) match {
              case (fileJson: FileJson) =>
                println("File url is " + fileJson.fileUrl)
                self ! SimulatorCase.FetchFile(fileJson.fileUrl, fileJson.encryptedAES)
            }
          }
        else {
            log.info(s"Simulator[$userId] get file [$fileId] failed")
            masterRef ! MasterCase.FinishGetFile()
          }
      case Success(somethingUnexpected) =>
        log.info(s"Simulator[$userId] get file [$fileId] failed")
        masterRef ! MasterCase.FinishGetFile()
      case Failure(error) =>
        log.info(s"Simulator[$userId] get file [$fileId] failed")
        masterRef ! MasterCase.FinishGetFile()
    }
  }

  def fetchFile(url: String, enAESKey: String) = {
    val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
    val responseFuture: Future[HttpResponse] = pipeline(Get("http://127.0.0.1:8080" + url))
    responseFuture onComplete {
      case Success(HttpResponse(_, entity: HttpEntity, _, _)) =>
        println(s"Simulator[$userId] fetch file [$url] succeed")
        val eArray = entity.data.toByteArray
        val AESKey = new String(rsa.decrypt(enAESKey.split(" ").map(x => BigInt(x))))
        val dArray = AES.decrypt(eArray, AESKey)
        println("Decrypted AES key is: " + AESKey)
        println("The content of the file is: \n" + new String(dArray))
        masterRef ! MasterCase.FinishGetFile()
      case _ =>
        println(s"Simulator[$userId] fetch file [$url] failed")
        masterRef ! MasterCase.FinishGetFile()
    }
  }

  def preShareFile(fileId: String, inviteeId: String) = {
    val pipeline = addHeader("userId", userId) ~> sendReceive ~> unmarshal[GoogleApiResult[FileJson]]
    val responseFuture = pipeline {
      Get("http://127.0.0.1:8080/getfile/" + fileId)
    }
    responseFuture onComplete {
      case Success(GoogleApiResult(status: String, results: List[FileJson])) =>
        if (status == "Succeed")
          log.info(s"Simulator[$userId] get file [$fileId] succeed")
        if (results.length > 0) {
          results(0) match {
            case (fileJson: FileJson) =>
              println("File enAES is " + fileJson.encryptedAES)
              val AESKey = new String(rsa.decrypt(fileJson.encryptedAES.split(" ").map(x => BigInt(x))))
              println("Decrypted AESKey is " + AESKey)
              val inviteeRsaOption: Option[RSA] = DataStore.rsaMap.get(inviteeId)
              inviteeRsaOption match {
                case None =>
                  masterRef ! MasterCase.FinishGetFile()
                case Some(inviteeRsa) =>
                  val enAESKey = inviteeRsa.encrypt(AESKey.getBytes)
                  var enAESKeyString = ""
                  for (value <- enAESKey) {
                    enAESKeyString += value + " "
                  }
                  println("Invitee enAES is " + enAESKeyString)
                  self ! SimulatorCase.ShareFile(fileId, inviteeId, enAESKeyString)
              }
          }
        }
        else {
          log.info(s"Simulator[$userId] get file [$fileId] failed")
          masterRef ! MasterCase.FinishGetFile()
        }
      case Success(somethingUnexpected) =>
        log.info(s"Simulator[$userId] get file [$fileId] failed")
        masterRef ! MasterCase.FinishGetFile()
      case Failure(error) =>
        log.info(s"Simulator[$userId] get file [$fileId] failed")
        masterRef ! MasterCase.FinishGetFile()
    }
  }

  def shareFile(fileId: String, inviteeId: String, enAESKey: String) = {
    val pipeline = sendReceive ~> unmarshal[GoogleApiResult[String]]
    val responseFuture = pipeline {
      val inviteeIdMap: collection.mutable.HashMap[String, String] = collection.mutable.HashMap[String, String]()
      inviteeIdMap += (inviteeId -> enAESKey)
      Post("http://127.0.0.1:8080/sharefile", new ShareFileJson(fileId, userId, inviteeIdMap.toMap))
    }
    responseFuture onComplete {
      case Success(GoogleApiResult(status: String, results: List[String])) =>
        if (status == "Succeed")
          println(s"Simulator[$userId] share file[$fileId] succeed")
        else
          println(s"Simulator[$userId] share file[$fileId] failed")
        masterRef ! MasterCase.FinishShareFile()
      case Success(somethingUnexpected) =>
        //log.warning(s"Simulator[$userId] add friend failed '{}'.", somethingUnexpected)
        log.info(s"Simulator[$userId] share file[$fileId] failed")
        masterRef ! MasterCase.FinishShareFile()
      case Failure(error) =>
        //log.error(error, "Couldn't get elevation")
        log.info(s"Simulator[$userId] share file[$fileId] failed")
        masterRef ! MasterCase.FinishShareFile()
    }
  }

  def receive = {
    case SimulatorCase.UploadFile(userId: String, filename: String) =>
      masterRef = sender()
      this.uploadFile(userId, filename)
    case SimulatorCase.GetFile(fileId: String) =>
      this.getFile(fileId)
    case SimulatorCase.FetchFile(url: String, enAESKey: String) =>
      this.fetchFile(url, enAESKey)
    case SimulatorCase.PreShareFile(fileId: String, inviteeId: String) =>
      this.preShareFile(fileId, inviteeId)
    case SimulatorCase.ShareFile(fileId: String, inviteeId: String, enAESKey: String) =>
      this.shareFile(fileId, inviteeId, enAESKey)
    case _ =>
  }
}
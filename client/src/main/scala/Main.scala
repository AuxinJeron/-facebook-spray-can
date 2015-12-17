import ServerActor.DataManager
import akka.routing.BroadcastPool
import akka.util.Timeout

import scala.concurrent.Await
import scala.util.{Success, Failure}
import scala.concurrent.duration._
import akka.actor._
import akka.pattern.ask
import akka.event.Logging
import akka.io.IO
import spray.can.Http
import spray.client.pipelining._
import spray.util._
import DataCenter._

object Main extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("simple-spray-client")
  val log = Logging(system, getClass)

  if (args.length != 2) {
    println("Invalid arguments. Command should be `run <simulator_num> <friend_num>.")
    system.shutdown()
  }
  else {
    val master = system actorOf(Props(new Master(args(0).toInt, args(1).toInt)), name = "Master")
    master ! MasterCase.RegisterUser()
    def shutdown(): Unit = {
      IO(Http).ask(Http.CloseAll)(1.second).await
      system.shutdown()
    }
  }
}

object MasterCase {
  case class RegisterUser()
  case class FinishRegisterUser(userId: String)
  case class AddFriends(num: Int)
  case class FinishAddFriends()
  case class FindStranger()
}

object SimulatorCase {
  case class RegisterUser()
  case class GetUserProfile(userId: String)
  case class FindStranger(strangerId: String, hopNum: Int)
  case class FoundStranger(success: Boolean, hopNum: Int)
  case class FindStrangerByFriends(friendListJson: AnyRef, strangerId: String, hopNum: Int)
}

object DataStore {
  val userIdList = collection.mutable.MutableList[String]()
  val simulatorMap = collection.mutable.Map[String, ActorPath]()
  var usersNum = 0
}

class Master(val simulatorNum: Int, val friendNum: Int) extends Actor {
  var finishedNum = 0
  val log = Logging(context.system, this)
  var foundStrangerNum = 0
  var notFoundStrangerNum = 0
  var totalHopNum: BigInt = 0

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
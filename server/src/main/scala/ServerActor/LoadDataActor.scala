package ServerActor

import DataCenter.UserProfile
import akka.actor.{ActorRef, Actor}
import akka.event.Logging
import scala.collection.mutable
import scala.collection.mutable.HashMap

/**
 * Created by leon on 11/26/15.
 */

object DataManager {
  var userProfileMap : HashMap[String, UserProfile] = new mutable.HashMap[String, UserProfile]()
  def addUser(userProfile: UserProfile) = {
    userProfileMap += (userProfile.userId -> userProfile)
  }
}

class LoadDataActor extends Actor {
  var server : ActorRef = null
  val log = Logging(context.system, this)

  def startLoadData() : Unit = {
    log.info("Start to load data.")
    this.loadUserProfile("leon.li@ufl.edu")
  }

  def loadUserProfile(email: String) : Unit = {
    val userProfile = new UserProfile(emailAddress = email)
    log.info("Add userid " + userProfile.userId)
    DataManager.addUser(userProfile)
  }

  def endLoadData() : Unit = {
    log.info("End loading data.")
    server ! LoadDataCase.End
  }

  def receive = {
    case LoadDataCase.Start =>
      this.startLoadData()
      server = sender
    case _ =>

  }
}

object LoadDataCase {
  case object Start
  case object End
}

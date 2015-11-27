package ServerActor

import DataCenter.{UserProfileJson, UserProfile}
import akka.actor.Actor
import akka.event.Logging

object UserProfileCase {
  case class GetUserProfile(userId: String)
}

class UserProfileActor extends Actor {
  val log = Logging(context.system, this)

  def getUserProfile(userId: String) : UserProfileJson = {
    val user = DataManager.userProfileMap.get(userId)
    user match {
      case None => return new UserProfileJson("null", "null")
      case Some(profile) => return new UserProfileJson(profile.userId, profile.pageId)
    }
  }

  def receive = {
    case UserProfileCase.GetUserProfile(userId: String) =>
      log.info("Receive case GetUserProfile")
      sender() ! this.getUserProfile(userId)
  }
}

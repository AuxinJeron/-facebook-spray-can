package DataCenter

import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import scala.collection.immutable.Map

case class UserProfileJson(userId: String, pageId: String)

object FacebookJsonProtocol extends DefaultJsonProtocol {
  implicit val userProfileFormat = jsonFormat2(UserProfileJson)
}


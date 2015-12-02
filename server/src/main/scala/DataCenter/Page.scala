package DataCenter

import scala.collection.mutable

/**
 * Created by leon on 11/28/15.
 */

class Page(user : String) {
  val pageId : String = java.util.UUID.randomUUID().toString
  val userId = user
  val accessToken = userId
  val content: String = s"$userId's page content"
  val albumSet: collection.mutable.Set[String] = mutable.Set[String]()
}

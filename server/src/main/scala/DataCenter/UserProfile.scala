package DataCenter

import scala.collection.mutable

class UserProfile(val emailAddress: String) {
  var userId = java.util.UUID.randomUUID().toString
  var pageId = java.util.UUID.randomUUID().toString
  var postMap: mutable.HashMap[String, String] = new mutable.HashMap[String, String]()
  var friendMap: mutable.HashMap[String, String] = new mutable.HashMap[String, String]()
  var albumSet: collection.mutable.Set[String] = mutable.Set[String]()
  var groupSet: collection.mutable.Set[String] = mutable.Set[String]()
  var fileSet: collection.mutable.Set[String] = mutable.Set[String]()
}
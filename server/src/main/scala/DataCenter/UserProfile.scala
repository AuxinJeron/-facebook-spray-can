package DataCenter

import scala.collection.mutable

class UserProfile(emailAddress: String) {
  var userId = java.util.UUID.randomUUID().toString
  var pageId = java.util.UUID.randomUUID().toString
  var postMap : mutable.HashMap[String, Post] = new mutable.HashMap[String, Post]()
  var friendMap : mutable.HashMap[String, UserProfile] = new mutable.HashMap[String, UserProfile]()
}
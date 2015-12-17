package DataCenter

import spray.http.DateTime

import scala.collection.mutable

/**
 * Created by leon on 11/30/15.
 */

class File(aUserId: String, aFileId: String, val encrypt: Boolean) {
  val fileId: String = java.util.UUID.randomUUID().toString
  val url: String = "/file/" + aFileId
  val createdTime = DateTime.now
  val createdTimeString = createdTime.toIsoDateTimeString
  val fromUserId: String = aUserId
  // key is userId, value is encrypted AES key
  val individualAES: collection.mutable.HashMap[String, String] = new mutable.HashMap[String, String]()
  // key is groupId, value is encrypted AES key
  val groupAES: collection.mutable.HashMap[String, String] = new mutable.HashMap[String, String]()
}

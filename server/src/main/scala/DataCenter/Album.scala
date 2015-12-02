package DataCenter

import spray.http.DateTime

/**
 * Created by leon on 11/30/15.
 */

class Album(aTitle: String, aUserId: String, aCoverPhotoId: String) {
  var albumId: String = java.util.UUID.randomUUID().toString
  val title: String = aTitle
  val fromUserId: String = aUserId
  val createTime = DateTime.now
  val createTimeString = createTime.toIsoDateTimeString
  var coverPhotoId: String = aCoverPhotoId
  var photoSet: collection.mutable.Set[String] = collection.mutable.Set[String]()
}

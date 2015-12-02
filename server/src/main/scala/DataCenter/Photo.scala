package DataCenter

import spray.http.DateTime

/**
 * Created by leon on 11/30/15.
 */

class Photo(anAlbumId: String, aUserId: String, imgId: String) {
  val photoId: String = java.util.UUID.randomUUID().toString
  val url: String = "/img/" + imgId
  val albumId: String = anAlbumId
  val createdTime = DateTime.now
  val createdTimeString = createdTime.toIsoDateTimeString
  val fromUserId: String = aUserId
}

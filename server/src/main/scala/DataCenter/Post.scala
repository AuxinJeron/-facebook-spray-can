package DataCenter

import spray.http.DateTime

/**
 * Created by leon on 11/27/15.
 */

class Post(aTitle: String, aContent: String) {
 var postId : String = java.util.UUID.randomUUID().toString
 var title = aTitle
 var content = aContent
 var date = DateTime.now
 var dateString = date.toIsoDateTimeString
}

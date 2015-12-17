package DataCenter

import spray.json.{NullOptions, JsonFormat, DefaultJsonProtocol}

case class AddUserJson(email: String)
case class UserProfileJson(userId: String, pageId: String, email: String, postMap: Map[String, String], friendMap: Map[String, String], albumList: Set[String])
case class PageJson(pageId: String, userId: String, content: String)
case class PostJson(postId: String, title: String, content: String, dateString: String)
case class FriendListJson(friendMap: Map[String, String])
case class AddFriendsJson(userId1: String, userId2: String)
case class AlbumJson(albumId: String, title: String, fromUserId: String, createTime: String, coverPhotoId: String)
case class PhotoJson(photoId: String, url: String, albumId: String, createdTime: String, fromUserId: String)
case class PhotoListJson(photoList: Set[String])
case class FileListJson(fileList: Set[String])
case class AddFileJson(userId: String, fileUrl: String, encrypt: Boolean, encryptedAES: String)
case class FileJson(fileId:String, userId: String, fileUrl:String, encrypt: Boolean, encryptedAES: String)
case class GroupJson(groupId: String, name: String, description: String, ownerId: String, admins: Set[String], members: Set[String], albums: Set[String])
case class AddGroupJson(name: String, description: String, ownerId: String)
case class AddGroupMemberJson(groupId: String, members: Set[String])
case class GoogleApiResult[T](status: String, results: List[T])

object FacebookJsonProtocol extends DefaultJsonProtocol with NullOptions {
  implicit val AddUserJsonFormat = jsonFormat1(AddUserJson)
  implicit val userProfileFormat = jsonFormat6(UserProfileJson)
  implicit val pageFormat = jsonFormat3(PageJson)
  implicit val postFormat = jsonFormat4(PostJson)
  implicit val friendListFormat = jsonFormat1(FriendListJson)
  implicit val addFriendsFormat = jsonFormat2(AddFriendsJson)
  implicit val AlbumJsonFormamt = jsonFormat5(AlbumJson)
  implicit val PhotoJsonFormat = jsonFormat5(PhotoJson)
  implicit val PhotoListJsonFormat = jsonFormat1(PhotoListJson)
  implicit val FileListJsonFormat = jsonFormat1(FileListJson)
  implicit val AddFileJsonFormat = jsonFormat4(AddFileJson)
  implicit val FileJsonFormat = jsonFormat5(FileJson)
  implicit val GroupJsonFormat = jsonFormat7(GroupJson)
  implicit val AddGroupJsonForamt = jsonFormat3(AddGroupJson)
  implicit val AddGroupMemberJsonFormat = jsonFormat2(AddGroupMemberJson)
  implicit def googleApiResultFormat[T :JsonFormat] = jsonFormat2(GoogleApiResult.apply[T])
}

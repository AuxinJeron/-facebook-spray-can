package ServerActor

import DataCenter._
import akka.actor.Actor
import akka.event.Logging

import scala.collection
import scala.collection.parallel.mutable

object UserProfileCase {
  case class AddUserProfile(addUserJson: AddUserJson)
  case class GetUserProfile(userId: String)
  case class GetUserPage(userId: String)
  case class GetUserPost(userId: String)
  case class GetUserFriends(userId: String)
  case class GetUserFiles(userId: String)
  case class AddUserAlbum(userId: String, albumJson: AlbumJson)
  case class GetPage(pageId: String)
  case class AddPageAlbum(pageId: String, albumJson: AlbumJson)
  case class GetPost(postId: String)
  case class UpdatePost(userId: String, postJson: PostJson)
  case class AddFriends(userId1: String, userId2: String)
  case class GetAlbum(albumId: String)
  case class AddAlbumPhoto(albumId: String, userId: String, imgId: String)
  case class GetAlbumPhotoList(albumId: String)
  case class AddFile(addFileJson: AddFileJson)
  case class GetFile(fileId: String, userId: String)
  case class GetGroup(groupId: String)
  case class AddGroup(addGroupJson: AddGroupJson)
  case class AddGroupMember(addGroupMemberJson: AddGroupMemberJson)
}

class UserProfileActor extends Actor {
  val log = Logging(context.system, this)

  def addUserProfile(addUserJson: AddUserJson) : Option[String] = {
    val user = new UserProfile(addUserJson.email)
    DataManager.addUser(user)
    return Some(user.userId)
  }

  def getUserProfile(userId: String) : Option[UserProfileJson] = {
    val user = DataManager.getUser(userId)
    user match {
      case None|null => return None
      case Some(profile : UserProfile) => return Some(new UserProfileJson(profile.userId, profile.pageId, profile.emailAddress,
        profile.postMap.toMap, profile.friendMap.toMap, profile.albumSet.toSet))
    }
  }

  def getUserPage(userId: String) : Option[PageJson] = {
    val user = DataManager.getUser(userId)
    var userProfile: UserProfile = null
    user match {
      case None|null => return None
      case Some(user: UserProfile) => userProfile = user
    }
    val page = DataManager.getPage(userProfile.pageId)
    page match {
      case None|null => return None
      case Some(page : Page) => return Some(new PageJson(page.pageId, page.userId, page.content))
    }
  }

  def getUserPostList(userId: String) : Option[List[PostJson]] = {
    val user = DataManager.getUser(userId)
    var userProfile: UserProfile = null
    user match {
      case None|null => return None
      case Some(user: UserProfile) => userProfile = user
    }
    val postMap = userProfile.postMap
    val postList = collection.mutable.MutableList[PostJson]()
    var postId: String = null
    for (postId <- postMap.keys) {
      val post = DataManager.getPost(postId)
      post match {
        case None =>
        case Some(post: Post) =>
          postList += new PostJson(post.postId, post.title, post.content, post.dateString)
      }
    }
    return Some(postList.toList)
  }

  def getUserFriends(userId: String) : Option[FriendListJson] = {
    val userOption = DataManager.getUser(userId)
    var user: UserProfile = null
    userOption match {
      case None|null => return None
      case Some(userOption: UserProfile) => user = userOption
    }
    return Some(new FriendListJson(user.friendMap.toMap))
  }

  def getUserFiles(userId: String) : Option[FileListJson] = {
    val userOption = DataManager.getUser(userId)
    var user: UserProfile = null
    userOption match {
      case None|null => return None
      case Some(userOption: UserProfile) => user = userOption
    }
    val fileList = collection.mutable.Set[String]()
    user.fileSet.foreach(fileList.add(_))
    return Some(new FileListJson(fileList.toSet))
  }

  def updateUserAlbum(userId: String, albumJson: AlbumJson) : Boolean = {
    val userOption = DataManager.getUser(userId)
    var user: UserProfile = null
    userOption match {
      case None => return false
      case Some(userOption: UserProfile) => user = userOption
    }
    val originAlbum = DataManager.getAlbum(albumJson.albumId)
    val newAlbum = new Album(albumJson.title, albumJson.fromUserId, albumJson.coverPhotoId)
    originAlbum match {
      case None =>
      case Some(originAlbum: Album) => newAlbum.albumId = originAlbum.albumId
    }
    DataManager.addUserAlbum(user, newAlbum)
    return true
  }

  def getPage(pageId: String) : Option[PageJson] = {
    val page = DataManager.getPage(pageId)
    page match {
      case None|null => return None
      case Some(page: Page) => return Some(new PageJson(page.pageId, page.userId, page.content))
    }
  }

  def updatePageAlbum(pageId: String, albumJson: AlbumJson) : Boolean = {
    val pageOption = DataManager.getPage(pageId)
    var page: Page = null
    pageOption match {
      case None => return false
      case Some(pageOption: Page) => page = pageOption
    }
    val originAlbum = DataManager.getAlbum(albumJson.albumId)
    val newAlbum = new Album(albumJson.title, albumJson.fromUserId, albumJson.coverPhotoId)
    originAlbum match {
      case None =>
      case Some(originAlbum: Album) => newAlbum.albumId = originAlbum.albumId
    }
    DataManager.addPageAlbum(page, newAlbum)
    return true
  }

  def getPost(postId: String) : Option[PostJson] = {
    val post = DataManager.getPost(postId)
    post match {
      case None|null => return None
      case Some(post: Post) => return Some(new PostJson(post.postId, post.title, post.content, post.dateString))
    }
  }

  def updatePost(userId: String, postJson: PostJson) : Boolean = {
    val user = DataManager.getUser(userId)
    var userProfile: UserProfile = null
    user match {
      case None => return false
      case Some(user: UserProfile) => userProfile = user
    }
    val originPost = DataManager.getPost(postJson.postId)
    val post = new Post(postJson.title, postJson.content)
    originPost match {
      case None =>
      case Some(originPost) => post.postId = originPost.postId
    }
    DataManager.addPost(userProfile, post)
    return true
  }

  def addFriends(userId1: String, userId2: String) : Boolean = {
    val user1Option = DataManager.getUser(userId1)
    var user1: UserProfile = null
    user1Option match {
      case None => return false
      case Some(user1Option: UserProfile) => user1 = user1Option
    }
    val user2Option = DataManager.getUser(userId2)
    var user2: UserProfile = null
    user2Option match {
      case None => return false
      case Some(user2Option: UserProfile) => user2 = user2Option
    }
    DataManager.addFriends(user1, user2)
    return true
  }

  def getAlbum(albumId: String) : Option[AlbumJson] = {
    val album = DataManager.getAlbum(albumId)
    album match {
      case None|null => return None
      case Some(album: Album) => return Some(new AlbumJson(album.albumId, album.title, album.fromUserId, album.createTimeString, album.coverPhotoId))
    }
  }

  def addAlbumPhoto(albumId: String, userId: String, imgId: String) : Boolean = {
    val albumOption = DataManager.getAlbum(albumId)
    var album: Album = null
    albumOption match {
      case None => return false
      case Some(albumOption: Album) => album = albumOption
    }
    val photo = new Photo(albumId, userId, imgId)
    DataManager.addAlbumPhoto(album, photo)
    return true
  }

  def getAlbumPhotoList(albumId: String) : Option[PhotoListJson] = {
    val albumOption = DataManager.getAlbum(albumId)
    var album: Album = null
    albumOption match {
      case None => return None
      case Some(albumOption: Album) => album = albumOption
    }
    val photoList = collection.mutable.Set[String]()
    album.photoSet.foreach(photoList.add(_))
    return Some(new PhotoListJson(photoList.toSet))
  }

  def addFile(addFileJson: AddFileJson) : Boolean = {
    val userOption = DataManager.getUser(addFileJson.userId)
    var user: UserProfile = null
    userOption match {
      case None => return false
      case Some(userOption: UserProfile) => user = userOption
    }
    val file = new File(addFileJson.userId, addFileJson.fileUrl, addFileJson.encrypt)
    file.individualAES += (addFileJson.userId -> addFileJson.encryptedAES)
    DataManager.addFile(file)
    return true
  }

  def getFile(fileId: String, userId: String) : Option[FileJson] = {
    val fileOption = DataManager.getFile(fileId)
    var file: File = null
    fileOption match {
      case None => return None
      case Some(value: File) => file = value
    }
    if (file.encrypt == false)
      return Some(FileJson(file.fileId, file.fromUserId, file.url, false, ""))

    val AESKeyOption = file.individualAES.get(userId)
    var AESKey: String = null
    AESKeyOption match {
      case None => return None
      case Some(value) => AESKey = value
    }
    return Some(FileJson(file.fileId, file.fromUserId, file.url, true, AESKey))
  }

  def getGroup(groupId: String) : Option[GroupJson] = {
    val groupOption = DataManager.getGroup(groupId)
    var group: Group = null
    groupOption match {
      case None|null => return None
      case Some(groupOption: Group) => group = groupOption
    }
    return Some(new GroupJson(group.groupId, group.name, group.description, group.owner,
      group.admins.toSet, group.members.toSet, group.albums.toSet))
  }

  def addGroup(addGroupJson: AddGroupJson) : Boolean = {
    val userOption = DataManager.getUser(addGroupJson.ownerId)
    var user: UserProfile = null
    userOption match {
      case None => return false
      case Some(userOption: UserProfile) => user = userOption
    }
    val group = new Group(addGroupJson.name, addGroupJson.description, addGroupJson.ownerId)
    DataManager.addGroup(group)
    DataManager.addAdminToGroup(group, user)
    return true
  }

  def addGroupMember(addGroupMemberJson: AddGroupMemberJson) : Boolean = {
    val groupOption = DataManager.getGroup(addGroupMemberJson.groupId)
    var group: Group = null
    groupOption match {
      case None => return false
      case Some(groupOption: Group) => group = groupOption
    }

    addGroupMemberJson.members.foreach((userId: String) =>
      {
        val user = DataManager.getUser(userId)
        user match {
          case None =>
          case Some(user: UserProfile) =>
            group.members.add(userId)
        }
      }
    )

    return true
  }

  def receive = {
    case UserProfileCase.AddUserProfile(addUserJson: AddUserJson) =>
      log.info("Receive case AddUserProfile")
      sender() ! this.addUserProfile(addUserJson)
    case UserProfileCase.GetUserProfile(userId: String) =>
      log.info("Receive case GetUserProfile")
      sender() ! this.getUserProfile(userId)
    case UserProfileCase.GetUserPage(userId: String) =>
      log.info("Receive case GetUserPage")
      sender() ! this.getUserPage(userId)
    case UserProfileCase.GetUserPost(userId: String) =>
      log.info("Receive case GetUserPost")
      sender() ! this.getUserPostList(userId)
    case UserProfileCase.GetUserFriends(userId: String) =>
      log.info("Receive case GetUserFriends")
      sender() ! this.getUserFriends(userId)
    case UserProfileCase.GetUserFiles(userId:  String) =>
      log.info("receive case GetuserFiles")
      sender() ! this.getUserFiles(userId)
    case UserProfileCase.AddUserAlbum(userId: String, albumJson: AlbumJson) =>
      log.info("Receive case AddUserAlbum")
      sender() ! this.updateUserAlbum(userId, albumJson)
    case UserProfileCase.GetPage(pageId:  String) =>
      log.info("Receive case GetPage")
      sender() ! this.getPage(pageId)
    case UserProfileCase.AddPageAlbum(pageId: String, albumJson: AlbumJson) =>
      log.info("Receive case AddPageAlbum")
      sender() ! this.updatePageAlbum(pageId, albumJson)
    case UserProfileCase.GetPost(postId: String) =>
      log.info("Receive case GetPost")
      sender() ! this.getPost(postId)
    case UserProfileCase.UpdatePost(userId: String, postJson: PostJson) =>
      log.info("Receive case AddPost")
      sender() ! this.updatePost(userId, postJson)
    case UserProfileCase.AddFriends(userId1: String, userId2: String) =>
      log.info("Receive case AddFriends")
      sender() ! this.addFriends(userId1, userId2)
    case UserProfileCase.GetAlbum(albumId: String) =>
      log.info("Receive case GetAlbum")
      sender() ! this.getAlbum(albumId)
    case UserProfileCase.AddAlbumPhoto(albumId: String, userId: String, imgId: String) =>
      log.info("Receive case AddAlbumPhoto")
      sender() ! this.addAlbumPhoto(albumId, userId, imgId)
    case UserProfileCase.GetAlbumPhotoList(albumId: String) =>
      log.info("Receive case GetAlbumPhotoList")
      sender() ! this.getAlbumPhotoList(albumId)
    case UserProfileCase.AddFile(addFileJson: AddFileJson) =>
      log.info("Receive case AddFile")
      sender() ! this.addFile(addFileJson)
    case UserProfileCase.GetFile(fileId: String, userId: String) =>
      log.info("Receive case GetFile")
      sender() ! this.getFile(fileId, userId)
    case UserProfileCase.GetGroup(groupId: String) =>
      log.info("Receive case GetGroup")
      sender() ! this.getGroup(groupId)
    case UserProfileCase.AddGroup(addGroupJson: AddGroupJson) =>
      log.info("Receive case AddGroup")
      sender() ! this.addGroup(addGroupJson)
    case UserProfileCase.AddGroupMember(addGroupMemberJson: AddGroupMemberJson) =>
      log.info("Receive case AddGroupMember")
      sender() ! this.addGroupMember(addGroupMemberJson)
  }
}
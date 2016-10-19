package ServerActor

import DataCenter._
import akka.actor.{ActorRef, Actor}
import akka.event.Logging
import spray.http.DateTime
import scala.collection.mutable
import scala.collection.mutable.HashMap

/**
 * Created by leon on 11/26/15.
 */

object DataManager {
  var userSessionMap : HashMap[String, String] = new mutable.HashMap[String, String]()
  var userProfileMap : HashMap[String, UserProfile] = new mutable.HashMap[String, UserProfile]()
  var pageMap : HashMap[String, Page] = new mutable.HashMap[String, Page]()
  var postMap : HashMap[String, Post] = new mutable.HashMap[String, Post]()
  var albumMap : HashMap[String, Album] = new mutable.HashMap[String, Album]()
  var photoMap : HashMap[String, Photo] = new mutable.HashMap[String, Photo]()
  var groupMap : HashMap[String, Group] = new mutable.HashMap[String, Group]()
  var fileMap : HashMap[String, File] = new mutable.HashMap[String, File]()
  var userPrivateKeyMap: HashMap[String, String] = new mutable.HashMap[String, String]()
  var userPublicKeyMap: HashMap[String, String] = new mutable.HashMap[String, String]()
  var groupPrivateKeyMap: HashMap[String, String] = new mutable.HashMap[String, String]()

  def getUser(userId: String) : AnyRef = {
    return userProfileMap.get(userId)
  }

  def addUser(userProfile: UserProfile) = {
    val page = new Page(userProfile.userId)
    pageMap += (page.pageId -> page)
    userProfile.pageId = page.pageId
    userProfileMap += (userProfile.userId -> userProfile)
  }

  def addUserAlbum(user: UserProfile, album: Album) = {
    albumMap += (album.albumId -> album)
    user.albumSet += album.albumId
  }

  def getPage(pageId: String) : AnyRef = {
    return pageMap.get(pageId)
  }

  def addPageAlbum(page: Page, album: Album) = {
    albumMap += (album.albumId -> album)
    page.albumSet += album.albumId
  }

  def getPost(postId: String) : Option[Post] = {
    return postMap.get(postId)
  }

  def addPost(user: UserProfile, post: Post) = {
    postMap += (post.postId -> post)
    user.postMap += (post.postId -> post.title)
  }

  def addFriends(user1: UserProfile, user2: UserProfile) = {
    user1.friendMap += (user2.userId -> ("pageId: " + user2.pageId))
    user2.friendMap += (user1.userId -> ("pageId: " + user1.pageId))
  }

  def getAlbum(albumId: String): AnyRef = {
    return albumMap.get(albumId)
  }

  def addAlbumPhoto(album: Album, photo: Photo) = {
    photoMap += (photo.photoId -> photo)
    album.photoSet += photo.photoId
  }

  def getPhoto(photoId: String) : AnyRef = {
    return photoMap.get(photoId)
  }

  def addFile(file: File) = {
    fileMap += (file.fileId -> file)
    val userOption = userProfileMap.get(file.fromUserId)
    userOption match {
      case None =>
      case Some(userProfile) => userProfile.fileSet.add(file.fileId)
    }
  }

  def shareFile(file: File, userId: String, enAESKey: String) = {
    val userOption = userProfileMap.get(userId)
    var user: UserProfile = null
    userOption match {
      case Some(userOption) => user = userOption
        file.individualAES += (user.userId -> enAESKey)
        user.fileSet.add(file.fileId)
      case None =>
    }
  }

  def shareFileToGroup(file: File, groupId: String, enAESkey: String) = {
    val groupOption = groupMap.get(groupId)
    var group: Group = null
    groupOption match {
      case Some(groupOption) => group = groupOption
        file.groupAES += (group.groupId -> enAESkey)
        group.fileSet.add(file.fileId)
      case None =>
    }
  }

  def getFile(fileId: String) : AnyRef = {
    return fileMap.get(fileId)
  }

  def getGroup(groupId: String) : AnyRef = {
    return groupMap.get(groupId)
  }

  def addGroup(group: Group) = {
    groupMap += (group.groupId -> group)
  }

  def addAdminToGroup(group: Group, user: UserProfile) = {
    user.groupSet.add(group.groupId)
    group.members.add(user.userId)
    group.admins.add(user.userId)
  }

  def addMemberToGroup(group: Group, user: UserProfile) = {
    user.groupSet.add(group.groupId)
    group.members.add(user.userId)
  }
}

class LoadDataActor extends Actor {
  var server : ActorRef = null
  val log = Logging(context.system, this)

  def startLoadData() : Unit = {
    log.info("Start to load data.")
    for (i <- 0 until 10) {
      this.loadUserProfile("leon.li@ufl.edu")
    }
  }

  def loadUserProfile(email: String) : Unit = {
    val userProfile = new UserProfile(emailAddress = email)
    log.info("Add userid " + userProfile.userId)
    DataManager.addUser(userProfile)

    // load post for this user
    for (i <- 0 until 10) {
      val title = "Post in " + DateTime.now.toIsoDateTimeString
      val content: String = "hahahaha"
      val post = new Post(title, content)
      DataManager.addPost(userProfile, post)
    }
  }

  def loadPost(user: UserProfile, title: String, content: String) = {
    val post = new Post(title, content)
    log.info("Add post " + post.postId)
    DataManager.addPost(user, post)
  }

  def endLoadData() : Unit = {
    log.info("End loading data.")
    server ! LoadDataCase.End
  }

  def receive = {
    case LoadDataCase.Start =>
      this.startLoadData()
      server = sender
    case _ =>
  }
}

object LoadDataCase {
  case object Start
  case object End
}

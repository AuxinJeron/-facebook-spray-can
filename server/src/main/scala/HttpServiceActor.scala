import java.io._
import java.io.{ ByteArrayInputStream, InputStream, OutputStream }
import ServerActor.{UserProfileCase, UserProfileActor, LoadDataCase, LoadDataActor}
import akka.pattern.Patterns
import akka.routing.RoundRobinPool
import akka.util.Timeout
import spray.http
import spray.http.HttpData.NonEmpty
import spray.http.HttpHeaders.`Content-Type`
import spray.httpx._
import spray.http._
import spray.http.HttpHeaders
import spray.http.MediaTypes._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import akka.actor._
import spray.routing._
import DataCenter._

class RouterActor extends Actor with RouteService {
  def actorRefFactory = context

  // load data
  val loadDataActor = actorRefFactory.actorOf(Props(new LoadDataActor()), "LoadDataActor")
  loadDataActor ! LoadDataCase.Start

  // init actor pool
  val actorsNum = 1000
  val userProfileActors = actorRefFactory.actorOf(Props(new UserProfileActor()).withRouter(RoundRobinPool(actorsNum)), name = "UserProfileActor")
  //val userProfileActors = actorRefFactory.actorOf(Props(new UserProfileActor()), name = "UserProfileActor")

  def receive = runRoute(facebookRoute)
}

trait RouteService extends HttpService {
  import DataCenter.FacebookJsonProtocol._
  import spray.httpx.SprayJsonSupport._

  implicit def executionContext = actorRefFactory.dispatcher
  val timeout = new Timeout(Duration.create(5, "seconds"))

  val demoRoute = {
    get {
      pathSingleSlash {
        complete(index)
      } ~
      path("ping") {
        complete("PONG!")
      }
    }
  }

  val facebookRoute = {
    path("") {
      get {
        complete(entry)
      }
    } ~
    path("user" / "register") {
      post {
        decompressRequest() {
          entity(as[AddUserJson]) { addUserJson =>
            val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
            val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.AddUserProfile(addUserJson), timeout)
            val result = Await.result(future, timeout.duration)
            complete {
              result match {
                case None => GoogleApiResult[String]("Failed", null)
                case Some(userId: String) => GoogleApiResult[String]("Success", List(userId))
              }
            }
          }
        }
      }
    } ~
    path("user" / "page" / Segment) { userId =>
      get {
        val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
        val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.GetUserPage(userId), timeout)
        val page = Await.result(future, timeout.duration)
        complete {
          page match {
            case None => GoogleApiResult[String]("Succeed", List("null"))
            case Some(page: PageJson) => GoogleApiResult[PageJson]("Success", List(page))
          }
        }
      }
    } ~
    path("user" / "post" / Segment) { userId =>
      get {
        val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
        val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.GetUserPost(userId), timeout)
        val postList = Await.result(future, timeout.duration)
        complete {
          postList match {
            case None => GoogleApiResult[String]("Succeed", List("null"))
            case Some(postList: List[PostJson]) => GoogleApiResult[PostJson]("Success", postList)
          }
        }
      } ~
      post {
        decompressRequest() {
          entity(as[PostJson]) { postJson =>
            val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
            val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.UpdatePost(userId, postJson), timeout)
            val result = Await.result(future, timeout.duration)
            complete {
              if (result == true)
                GoogleApiResult[String]("Succeed", List("Succeed"))
              else
                GoogleApiResult[String]("Failed", List("Failed"))
            }
          }
        }
      }
    } ~
    path("user" / "friendlist" / Segment) { userId =>
      get {
        val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
        val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.GetUserFriends(userId), timeout)
        val friendList = Await.result(future, timeout.duration)
        complete {
          friendList match {
            case None => GoogleApiResult[String]("Succeed", List("null"))
            case Some(friendList: FriendListJson) => GoogleApiResult[FriendListJson]("Success", List(friendList))
          }
        }
      }
    } ~
    path ("user" / "album" / Segment) { userId =>
      post {
        decompressRequest() {
          entity(as[AlbumJson]) { albumJson =>
            val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
            val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.AddUserAlbum(userId, albumJson), timeout)
            val result = Await.result(future, timeout.duration)
            complete {
              if (result == true)
                GoogleApiResult[String]("Succeed", List("Succeed"))
              else
                GoogleApiResult[String]("Failed", List("Failed"))
            }
          }
        }
      }
    } ~
    path("user" / "group" / Segment) { userId =>
      post {
        decompressRequest() {
          entity(as[AddGroupJson]) { addGroupJson =>
            val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
            val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.AddGroup(addGroupJson), timeout)
            val result = Await.result(future, timeout.duration)
            complete {
              if (result == true)
                GoogleApiResult[String]("Succeed", List("Succeed"))
              else
                GoogleApiResult[String]("Failed", List("Failed"))
            }
          }
        }
      }
    } ~
    path("user" / Segment) { userId =>
      get {
        val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
        val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.GetUserProfile(userId), timeout)
        val userProfile = Await.result(future, timeout.duration)
        complete {
          userProfile match {
            case None => GoogleApiResult[String]("Succeed", List("null"))
            case Some(userProfile: UserProfileJson) => GoogleApiResult[UserProfileJson]("Success", List(userProfile))
          }
        }
      }
    } ~
    path("page" / "album" / Segment) { pageId =>
      post {
        decompressRequest() {
          entity(as[AlbumJson]) { albumJson =>
            val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
            val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.AddPageAlbum(pageId, albumJson), timeout)
            val result = Await.result(future, timeout.duration)
            complete {
              if (result == true)
                GoogleApiResult[String]("Succeed", List("Succeed"))
              else
                GoogleApiResult[String]("Failed", List("Failed"))
            }
          }
        }
      }
    } ~
    path("page" / Segment) { pageId =>
      get {
        val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
        val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.GetPage(pageId), timeout)
        val page = Await.result(future, timeout.duration)
        complete {
          page match {
            case None => GoogleApiResult[String]("Success", List("null"))
            case Some(page: PageJson) => GoogleApiResult[PageJson]("Success", List(page))
          }
        }
      }
    } ~
    path("post" / Segment) { postId =>
      get {
        val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
        val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.GetPost(postId), timeout)
        val post = Await.result(future, timeout.duration)
        complete {
          post match {
            case None => GoogleApiResult[String]("Success", List("null"))
            case Some(post: PostJson) => GoogleApiResult[PostJson]("Success", List(post))
          }
        }
      }
    } ~
    path("friends") {
      post {
        decompressRequest() {
          entity(as[AddFriendsJson]) { addFriendsJson =>
            val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
            val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.AddFriends(addFriendsJson.userId1, addFriendsJson.userId2), timeout)
            val result = Await.result(future, timeout.duration)
            complete {
              if (result == true)
                GoogleApiResult[String]("Succeed", List("Succeed"))
              else
                GoogleApiResult[String]("Failed", List("Failed"))
            }
          }
        }
      }
    } ~
    path("album" / "photo" / Segment) { albumId =>
      get {
        val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
        val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.GetAlbumPhotoList(albumId), timeout)
        val photoList = Await.result(future, timeout.duration)
        complete {
          photoList match {
            case None => GoogleApiResult[String]("Success", List("null"))
            case Some(photoList: PhotoListJson) => GoogleApiResult[PhotoListJson]("Success", List(photoList))
          }
        }
      } ~
      post {
        respondWithMediaType(`application/json`) {
          entity(as[MultipartFormData]) { formData =>
            complete {
              val details = formData.fields.map {
                case BodyPart(entity, headers) =>
                //val content = entity.buffer
                  val content = new ByteArrayInputStream(entity.data.toByteArray)
                  var contentType: ContentType = null
                  entity.toOption match {
                    case None =>
                    case Some(HttpEntity.NonEmpty(aContentType, aData)) => contentType = aContentType
                  }
                  var fileName: String = ""
                  headers.foreach {
                    _ match {
                      case http.HttpHeaders.`Content-Disposition`(dispositionType: String, parameters: Map[String, String]) =>
                        parameters.get("filename") match {
                          case None =>
                          case Some(name) => fileName = name
                        }
                    }
                  }
                  //val fileName = headers.find(h => h.is("Content-Disposition")).get.value.split("filename=").last
                  val imgId = java.util.UUID.randomUUID().toString
                  val path: String = "server/img/" + imgId + ".jpg"
                  var result = saveAttachment(path, content)
                  // create photo
                  val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
                  // TODO: the userId should be contained in header in next version
                  val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.AddAlbumPhoto(albumId, "", imgId), timeout)
                  val succeed = Await.result(future, timeout.duration)
                  if (succeed == false) {
                    result = false
                  }
                  (contentType, fileName, result)
                case _ =>
              }
              s"""{"status": "Processed POST request, details=$details" }"""
            }
          }
        }
      }
    } ~
    path("album" / Segment) { albumId =>
      get {
        val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
        val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.GetAlbum(albumId), timeout)
        val albumJson = Await.result(future, timeout.duration)
        complete {
          albumJson match {
            case None => GoogleApiResult[String]("Success", List("null"))
            case Some(albumJson: AlbumJson) => GoogleApiResult[AlbumJson]("Success", List(albumJson))
          }
        }
      }
    } ~
    pathPrefix("img") {
      getFromDirectory("server/img")
    } ~
    path("group" / "addmembers") {
      post {
        decompressRequest() {
          entity(as[AddGroupMemberJson]) { addGroupMemberJson =>
            val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
            val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.AddGroupMember(addGroupMemberJson), timeout)
            val result = Await.result(future, timeout.duration)
            complete {
              if (result == true)
                GoogleApiResult[String]("Success", List("Succeed"))
              else
                GoogleApiResult[String]("Failed", List("Failed"))
            }
          }
        }
      }
    } ~
    path("group" / Segment) { groupId =>
      get {
        val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
        val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.GetGroup(groupId), timeout)
        val groupJson = Await.result(future, timeout.duration)
        complete {
          groupJson match {
            case None => GoogleApiResult[String]("Success", List("null"))
            case Some(groupJson: GroupJson) => GoogleApiResult[GroupJson]("Success", List(groupJson))
          }
        }
      }
    }
  }

  lazy val index =
    <html>
      <body>
        <h1>Say hello to <i>spray-routing</i> on <i>spray-can</i>!</h1>
        <p>Defined resources:</p>
        <ul>
          <li><a href="/ping">/ping</a></li>
          <li><a href="/stream1">/stream1</a> (via a Stream[T])</li>
          <li><a href="/stream2">/stream2</a> (manually)</li>
          <li><a href="/stream-large-file">/stream-large-file</a></li>
          <li><a href="/stats">/stats</a></li>
          <li><a href="/timeout">/timeout</a></li>
          <li><a href="/cached">/cached</a></li>
          <li><a href="/crash">/crash</a></li>
          <li><a href="/fail">/fail</a></li>
          <li><a href="/stop?method=post">/stop</a></li>
        </ul>
      </body>
    </html>

  lazy val entry =
    <html>
      <body>
        <h1>Say hello to <i>spray-routing</i> on <i>spray-can</i>!</h1>
        <p>Defined resources:</p>
        <ul>
          <li><a href="/user/dd3dacbf-d86e-4b76-a1a0-ffd71b2f115c">/user</a></li>
          <li><a href="/stream1">/stream1</a> (via a Stream[T])</li>
          <li><a href="/stream2">/stream2</a> (manually)</li>
          <li><a href="/stream-large-file">/stream-large-file</a></li>
          <li><a href="/stats">/stats</a></li>
          <li><a href="/timeout">/timeout</a></li>
          <li><a href="/cached">/cached</a></li>
          <li><a href="/crash">/crash</a></li>
          <li><a href="/fail">/fail</a></li>
          <li><a href="/stop?method=post">/stop</a></li>
        </ul>
      </body>
    </html>

  private def saveAttachment(fileName: String, content: Array[Byte]): Boolean = {
    saveAttachment[Array[Byte]](fileName, content, {(is, os) => os.write(is)})
    true
  }

  private def saveAttachment(fileName: String, content: InputStream): Boolean = {
    saveAttachment[InputStream](fileName, content,
    { (is, os) =>
      val buffer = new Array[Byte](16384)
      Iterator
        .continually (is.read(buffer))
        .takeWhile (-1 !=)
        .foreach (read=>os.write(buffer,0,read))
    }
    )
  }

  private def saveAttachment[T](fileName: String, content: T, writeFile: (T, OutputStream) => Unit): Boolean = {
    try {
      val fos = new java.io.FileOutputStream(fileName)
      writeFile(content, fos)
      fos.close()
      true
    } catch {
      case _ => false
    }
  }
}






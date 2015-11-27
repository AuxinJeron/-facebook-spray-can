import java.io.File
import ServerActor.{UserProfileCase, UserProfileActor, LoadDataCase, LoadDataActor}
import akka.event.Logging
import akka.pattern.Patterns
import akka.routing.RoundRobinPool
import akka.util.Timeout
import spray.http.MediaType
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import org.parboiled.common.FileUtils
import akka.actor._
import spray.routing.{HttpServiceBase, HttpService, RequestContext}
import DataCenter.{UserProfileJson}

class RouterActor extends Actor with RouteService {
  def actorRefFactory = context

  // load data
  val loadDataActor = actorRefFactory.actorOf(Props(new LoadDataActor()), "LoadDataActor")
  loadDataActor ! LoadDataCase.Start

  // init actor pool
  val actorsNum = 10
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
    path("user" / Segment) { userId =>
      get {
        val actor = actorRefFactory.actorSelection("/user/handler/UserProfileActor")
        val future: Future[AnyRef] = Patterns.ask(actor, UserProfileCase.GetUserProfile(userId), timeout)
        val userProfile = Await.result(future, timeout.duration).asInstanceOf[UserProfileJson]
        complete {
          userProfile
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
}

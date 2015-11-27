/**
 * Created by leon on 11/20/15.
 */

import akka.actor._
import akka.io.IO
import spray.can.Http

object Main extends App {

  // create, start and supervise the TestService actor, which holds our custom request handling logic
  // as well as the HttpServer actor
  implicit val system = ActorSystem("FacebookServer")

  // the handler actor replies to incoming HttpRequests
  val handler = system.actorOf(Props[RouterActor], name = "handler")

  IO(Http) ! Http.Bind(handler, interface = "localhost", port = 8080)
}

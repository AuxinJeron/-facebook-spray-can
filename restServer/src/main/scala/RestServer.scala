/**
 * Created by leon on 11/25/15.
 */

import akka.actor.ActorSystem
import spray.routing.{HttpServiceActor, SimpleRoutingApp}

object RestServer extends HttpServiceActor {
  implicit val system = ActorSystem("RESTServerSystem")

  def receive = runRoute{
    path("ping") {
      get {
        complete("PONG")
      }
    }
  }
}

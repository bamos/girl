package io.github.bamos

// Akka
import akka.actor.{Actor,ActorRefFactory}
import akka.pattern.ask
import akka.util.Timeout

// Spray
import spray.http._
import spray.routing.HttpService
import MediaTypes._

// Scala
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global

// Actor accepting Http requests for the Scala collector.
class RequestHandlerActor extends Actor with HttpService {
  implicit val timeout: Timeout = 2000.second // For the actor 'asks'
  import context.dispatcher
  def actorRefFactory = context

  private val collectorService = new RequestHandler(context)

  // Message loop for the Spray service.
  def receive = handleTimeouts orElse runRoute(collectorService.collectorRoute)

  def handleTimeouts: Receive = {
    case Timedout(_) => sender !
      HttpResponse(status = 408, entity = "Error: Page timed out.")
  }
}

class RequestHandler(context: ActorRefFactory) extends HttpService {
  implicit def actorRefFactory = context
  val collectorRoute = {
    get {
      pathSingleSlash {
        redirect("/bamos", StatusCodes.Found)
      }~
      path("favicon.ico") {
        complete(StatusCodes.NotFound)
      }~
      path(Rest) { path =>
        getFromResource("%s" format path)
      }~
      path(Rest) { path =>
        getFromResource("bootstrap/%s" format path)
      }~
      path(Segment) { user =>
        respondWithMediaType(`text/html`) {
          complete(Girl.getUserBrokenLinksMemoized(user))
        }
      }~
      path(Segment/Segment) { (user,repo) =>
        respondWithMediaType(`text/html`) {
          complete(Girl.getRepoBrokenLinksMemoized(user,repo))
        }
      }
    }~
    complete(HttpResponse(status = 404, entity = "404 Not found"))
  }
}

package io.github.bamos

// Akka and Spray
import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http

object Main extends App {
  implicit val system = ActorSystem.create("query")
  val handler = system.actorOf(
    Props(classOf[RequestHandlerActor]),
    name = "handler"
  )
  IO(Http) ! Http.Bind(handler, interface="0.0.0.0", port=8585)
}

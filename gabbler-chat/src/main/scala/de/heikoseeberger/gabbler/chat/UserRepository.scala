/*
 * Copyright 2016 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.heikoseeberger.gabbler.chat

import akka.actor.{ ActorLogging, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.persistence.{ PersistentActor, RecoveryCompleted }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink }
import de.heikoseeberger.akkasse.ServerSentEvent
import de.heikoseeberger.akkasse.pattern.ServerSentEventClient
import io.circe.{ Decoder, jawn }

object UserRepository {

  final case class User(id: Long, username: String, nickname: String)

  final case class FindUserByUsername(username: String)
  final case class UsernameUnknown(username: String)

  private final case class AddUser(id: Long,
                                   username: String,
                                   nickname: String)
  private final case class UserAdded(eventId: String, user: User)

  private final case class RemoveUser(id: Long)
  private final case class UserRemoved(eventId: String, user: User)

  final val Name = "user-repository"

  def props(userEvents: Uri): Props = Props(new UserRepository(userEvents))

  private def decode[A: Decoder](s: String) =
    jawn.decode[A](s).valueOr(throw _)
}

final class UserRepository(userEvents: Uri)
    extends PersistentActor
    with ActorLogging {
  import UserRepository._
  import context.dispatcher
  import io.circe.generic.auto._

  override val persistenceId = Name

  private implicit val mat = ActorMaterializer()

  private var users = Map.empty[String, User]

  private var lastEventId = Option.empty[String]

  override def receiveCommand = {
    case FindUserByUsername(username) => handleFindUserByUsername(username)
    case (eventId: String, AddUser(id, username, nickname)) =>
      handleAddUser(eventId, id, username, nickname)
    case (eventId: String, RemoveUser(id)) => handleRemoveUser(eventId, id)
  }

  override def receiveRecover = {
    case RecoveryCompleted =>
      ServerSentEventClient(userEvents,
                            userEventHandler,
                            Http(context.system).singleRequest(_),
                            lastEventId).runWith(Sink.ignore)

    case UserAdded(eventId, user) =>
      lastEventId = Some(eventId)
      users += user.username -> user
      log.info(s"Added user with username ${ user.username }")

    case UserRemoved(eventId, user) =>
      lastEventId = Some(eventId)
      users -= user.username
      log.info(s"Removed user with username ${ user.username }")
  }

  private def handleFindUserByUsername(username: String) =
    users.get(username) match {
      case None       => sender() ! UsernameUnknown(username)
      case Some(user) => sender() ! user
    }

  private def handleAddUser(eventId: String,
                            id: Long,
                            username: String,
                            nickname: String) =
    persist(UserAdded(eventId, User(id, username, nickname))) { userAdded =>
      receiveRecover(userAdded)
    }

  private def handleRemoveUser(eventId: String, id: Long) = {
    val user = users.values
      .find(_.id == id)
      .getOrElse(
        throw new IllegalStateException(s"User with id $id does not exist!")
      )
    persist(UserRemoved(eventId, user)) { userRemoved =>
      receiveRecover(userRemoved)
    }
  }

  private def userEventHandler =
    Flow[ServerSentEvent].map {
      case ServerSentEvent(user, Some("user-added"), Some(eventId), _) =>
        eventId -> decode[AddUser](user)
      case ServerSentEvent(user, Some("user-removed"), Some(eventId), _) =>
        eventId -> decode[RemoveUser](user)
    }.to(Sink.foreach(self ! _))
}

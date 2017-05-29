package io.digitalcat.publictransportation.services.identity.impl

import java.util.UUID

import com.lightbend.lagom.scaladsl.api.transport.{ExceptionMessage, NotFound, TransportErrorCode}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import io.digitalcat.publictransportation.services.common.response.GeneratedIdDone
import io.digitalcat.publictransportation.services.identity.api.{IdentityStateDone, UserLogin, UserLoginDone, User => ResponseUser}
import io.digitalcat.publictransportation.services.identity.impl.util.{SecurePasswordHashing, Token}

import scala.collection.immutable.Seq


class IdentityEntity extends PersistentEntity {
  override type Command = IdentityCommand
  override type Event = IdentityEvent
  override type State = IdentityState

  override def behavior: Behavior = {
    Actions()
      .onCommand[RegisterClient, GeneratedIdDone] {
        case (RegisterClient(company, firstName, lastName, username, email, password), ctx, state) =>
          state.client match {
            case Some(_) =>
              ctx.invalidCommand(s"User ${entityId} is already registered") // TODO: fix check if user exists (this implementation is wrong)
              ctx.done
            case None =>
              val hashedPassword = SecurePasswordHashing.hashPassword(password)
              val userId = UUID.randomUUID().toString

              ctx.thenPersistAll(
                ClientCreated(company),
                UserCreated(userId, firstName, lastName, username, email, hashedPassword)
              ) { () =>
                ctx.reply(GeneratedIdDone(entityId))
              }
          }
      }
      .onCommand[CreateUser, GeneratedIdDone] {
        case (CreateUser(firstName, lastName, email, username, password), ctx, state) =>
          state.client match {
            case Some(_) =>
              val hashedPassword = SecurePasswordHashing.hashPassword(password)
              val userId = UUID.randomUUID().toString

              ctx.thenPersist(UserCreated(userId, firstName, lastName, email, username, hashedPassword)) { _ =>
                ctx.reply(GeneratedIdDone(userId))
              }
            case None =>
              ctx.invalidCommand(s"Client ${entityId} not found")
              ctx.done
          }
      }
      .onReadOnlyCommand[GetIdentityState, IdentityStateDone] {
        case (GetIdentityState(), ctx, state) =>
          state.client match {
            case None =>
              throw new NotFound(TransportErrorCode.BadRequest, new ExceptionMessage(s"Client registered with ${entityId} can't be found", ""))
            case Some(client: Client) =>
              ctx.reply(
                IdentityStateDone(
                  entityId,
                  client.name,
                  client.users.map(user =>
                    ResponseUser(
                      id = user.userId,
                      firstName = user.firstName,
                      lastName = user.lastName,
                      email = user.email,
                      username = user.username
                    )
                  )
                )
              )
          }
      }
      .onEvent {
        case (ClientCreated(company), _) => IdentityState(Some(Client(company)))
        case (UserCreated(userId, firstName, lastName, username, email, password), state) => state.addUser(User(userId, firstName, lastName, username, email, password))
      }
  }

  override def initialState: IdentityState = IdentityState(None)
}

object IdentitySerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[GeneratedIdDone],
    JsonSerializer[CreateUser],
    JsonSerializer[RegisterClient],
    JsonSerializer[IdentityStateDone],
    JsonSerializer[ClientCreated],
    JsonSerializer[UserCreated],
    JsonSerializer[UserLogin],
    JsonSerializer[UserLoginDone],
    JsonSerializer[UserCreated],
    JsonSerializer[User],
    JsonSerializer[Token],
    JsonSerializer[IdentityState]
  )
}

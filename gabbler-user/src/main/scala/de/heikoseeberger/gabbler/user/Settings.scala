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

package de.heikoseeberger.gabbler.user

import akka.actor.{ Actor, ExtendedActorSystem, Extension, ExtensionKey }
import scala.concurrent.duration.FiniteDuration

object Settings extends ExtensionKey[Settings]

final class Settings(system: ExtendedActorSystem) extends Extension {

  final object userApi {

    val address: String = config.getString("gabbler-user.user-api.address")

    val port: Int = config.getInt("gabbler-user.user-api.port")

    val userRepositoryTimeout: FiniteDuration = config
      .getDuration("gabbler-user.user-api.user-repository-timeout")
      .asScala
  }

  private def config = system.settings.config
}

trait ActorSettings { this: Actor =>
  protected val settings: Settings = Settings(context.system)
}

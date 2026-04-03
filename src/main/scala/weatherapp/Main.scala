package weatherapp

import cats.effect.{IO, IOApp}
import com.comcast.ip4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger

object Main extends IOApp.Simple {

  override def run: IO[Unit] =
    EmberClientBuilder
      .default[IO]
      .build
      .use { client =>
        val weatherService = WeatherService.impl[IO](client)
        val httpApp        = Logger.httpApp(logHeaders = true, logBody = false)(
          WeatherRoutes.routes[IO](weatherService).orNotFound
        )

        EmberServerBuilder
          .default[IO]
          .withHost(host"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(httpApp)
          .build
          .useForever
      }
}

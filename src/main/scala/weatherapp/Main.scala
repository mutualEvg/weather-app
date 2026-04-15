package weatherapp

import cats.effect.{IO, IOApp}
import com.comcast.ip4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger

object Main extends IOApp.Simple {

  override def run: IO[Unit] = {
    val cfg = AppConfig.load

    EmberClientBuilder
      .default[IO]
      .withTimeout(cfg.clientTimeout)
      .build
      .use { client =>
        for {
          cache <- WeatherCache.inMemory[IO](cfg.cacheTtl)

          service = WeatherService.withCache[IO](
            WeatherService.withRetry[IO](
              WeatherService.impl[IO](client),
              cfg.nwsClient.maxRetries
            ),
            cache
          )

          host <- IO.fromOption(Host.fromString(cfg.server.host))(
            new RuntimeException(s"Invalid host: ${cfg.server.host}")
          )
          port <- IO.fromOption(Port.fromInt(cfg.server.port))(
            new RuntimeException(s"Invalid port: ${cfg.server.port}")
          )

          httpApp = Logger.httpApp(logHeaders = true, logBody = false)(
            WeatherRoutes.routes[IO](service).orNotFound
          )

          _ <- EmberServerBuilder
            .default[IO]
            .withHost(host)
            .withPort(port)
            .withHttpApp(httpApp)
            .build
            .useForever
        } yield ()
      }
  }
}

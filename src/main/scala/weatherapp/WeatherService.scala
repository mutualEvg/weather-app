package weatherapp

import cats.effect.{Concurrent, Temporal}
import cats.syntax.all._
import org.http4s.client.Client
import org.http4s.{Header, Request, Uri}
import org.http4s.circe.CirceEntityDecoder._
import org.typelevel.ci.CIString
import scala.concurrent.duration._

trait WeatherService[F[_]] {
  def forecast(lat: Double, lon: Double): F[WeatherResponse]
}

object WeatherService {

  private val NwsBase   = "https://api.weather.gov"
  private val UserAgent = Header.Raw(CIString("User-Agent"), "(weather-app, contact@weather-app.local)")

  /** Raw NWS client — two HTTP calls per invocation, no resilience. */
  def impl[F[_]: Concurrent](client: Client[F]): WeatherService[F] =
    new WeatherService[F] {
      override def forecast(lat: Double, lon: Double): F[WeatherResponse] =
        for {
          pointsUri   <- Concurrent[F].fromEither(Uri.fromString(s"$NwsBase/points/$lat,$lon"))
          points      <- client.expect[NwsPointsResponse](
                           Request[F](uri = pointsUri).putHeaders(UserAgent)
                         )
          forecastUri <- Concurrent[F].fromEither(Uri.fromString(points.properties.forecast))
          fc          <- client.expect[NwsForecastResponse](
                           Request[F](uri = forecastUri).putHeaders(UserAgent)
                         )
          today       <- Concurrent[F].fromOption(
                           fc.properties.periods.find(_.isDaytime),
                           new RuntimeException("No daytime forecast period found")
                         )
        } yield WeatherResponse(
          shortForecast    = today.shortForecast,
          temperature      = today.temperature,
          temperatureUnit  = today.temperatureUnit,
          characterization = WeatherResponse.characterize(today.temperature, today.temperatureUnit)
        )
    }

  /** Decorator: retry with exponential backoff on any error.
    * Requires Temporal (extends Concurrent) for sleep between attempts. */
  def withRetry[F[_]: Temporal](
      underlying: WeatherService[F],
      maxRetries: Int,
      baseDelay: FiniteDuration = 500.milliseconds
  ): WeatherService[F] =
    new WeatherService[F] {
      private def attempt(lat: Double, lon: Double, remaining: Int, delay: FiniteDuration): F[WeatherResponse] =
        underlying.forecast(lat, lon).handleErrorWith { err =>
          if (remaining <= 0) Temporal[F].raiseError(err)
          else Temporal[F].sleep(delay) *> attempt(lat, lon, remaining - 1, delay * 2)
        }

      override def forecast(lat: Double, lon: Double): F[WeatherResponse] =
        attempt(lat, lon, maxRetries, baseDelay)
    }

  /** Decorator: cache responses keyed by rounded lat/lon. */
  def withCache[F[_]: Concurrent](
      underlying: WeatherService[F],
      cache: WeatherCache[F]
  ): WeatherService[F] =
    new WeatherService[F] {
      override def forecast(lat: Double, lon: Double): F[WeatherResponse] = {
        val key = WeatherCache.cacheKey(lat, lon)
        cache.get(key).flatMap {
          case Some(hit) => Concurrent[F].pure(hit)
          case None      => underlying.forecast(lat, lon).flatTap(r => cache.put(key, r))
        }
      }
    }
}

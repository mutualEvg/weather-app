package weatherapp

import cats.effect.Concurrent
import cats.implicits._
import org.http4s.client.Client
import org.http4s.{Header, Request, Uri}
import org.http4s.circe.CirceEntityDecoder._
import org.typelevel.ci.CIString

trait WeatherService[F[_]] {
  def forecast(lat: Double, lon: Double): F[WeatherResponse]
}

object WeatherService {

  private val NwsBase   = "https://api.weather.gov"
  private val UserAgent = Header.Raw(CIString("User-Agent"), "(weather-app, contact@weather-app.local)")

  def impl[F[_]: Concurrent](client: Client[F]): WeatherService[F] =
    new WeatherService[F] {

      override def forecast(lat: Double, lon: Double): F[WeatherResponse] =
        for {
          pointsUri <- Concurrent[F].fromEither(Uri.fromString(s"$NwsBase/points/$lat,$lon"))
          points <- client.expect[NwsPointsResponse](Request[F](uri = pointsUri).putHeaders(UserAgent))
          forecastUri <- Concurrent[F].fromEither(Uri.fromString(points.properties.forecast))
          fc <- client.expect[NwsForecastResponse](Request[F](uri = forecastUri).putHeaders(UserAgent))
          today <- Concurrent[F].fromOption(
            fc.properties.periods.find(_.isDaytime),
            new RuntimeException("No daytime forecast period found")
          )
        } yield WeatherResponse(
          shortForecast   = today.shortForecast,
          temperature     = today.temperature,
          temperatureUnit = today.temperatureUnit,
          characterization = WeatherResponse.characterize(today.temperature, today.temperatureUnit)
        )
    }
}

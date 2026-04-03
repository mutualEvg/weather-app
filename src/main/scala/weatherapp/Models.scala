package weatherapp

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

// ── NWS API response shapes ────────────────────────────────────────

final case class NwsPointsProperties(forecast: String)
object NwsPointsProperties {
  implicit val decoder: Decoder[NwsPointsProperties] =
    Decoder.forProduct1("forecast")(NwsPointsProperties.apply)
}

final case class NwsPointsResponse(properties: NwsPointsProperties)
object NwsPointsResponse {
  implicit val decoder: Decoder[NwsPointsResponse] = deriveDecoder
}

final case class ForecastPeriod(
    name: String,
    temperature: Int,
    temperatureUnit: String,
    shortForecast: String,
    isDaytime: Boolean
)
object ForecastPeriod {
  implicit val decoder: Decoder[ForecastPeriod] = deriveDecoder
}

final case class NwsForecastProperties(periods: List[ForecastPeriod])
object NwsForecastProperties {
  implicit val decoder: Decoder[NwsForecastProperties] = deriveDecoder
}

final case class NwsForecastResponse(properties: NwsForecastProperties)
object NwsForecastResponse {
  implicit val decoder: Decoder[NwsForecastResponse] = deriveDecoder
}

// ── API response ───────────────────────────────────────────────

final case class WeatherResponse(
    shortForecast: String,
    temperature: Int,
    temperatureUnit: String,
    characterization: String
)

object WeatherResponse {
  implicit val encoder: Encoder[WeatherResponse] = deriveEncoder

  def characterize(temp: Int, unit: String): String = {
    val fahrenheit = if (unit == "C") temp * 9 / 5 + 32 else temp
    if (fahrenheit >= 85) "hot"
    else if (fahrenheit <= 50) "cold"
    else "moderate"
  }
}

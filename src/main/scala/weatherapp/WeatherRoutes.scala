package weatherapp

import cats.effect.Concurrent
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import org.http4s.circe.CirceEntityEncoder._

object WeatherRoutes {

  private object LatParam extends QueryParamDecoderMatcher[Double]("lat")
  private object LonParam extends QueryParamDecoderMatcher[Double]("lon")

  def routes[F[_]: Concurrent](service: WeatherService[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root / "forecast" :? LatParam(lat) +& LonParam(lon) =>
        service
          .forecast(lat, lon)
          .flatMap(Ok(_))
          .handleErrorWith { err =>
            BadRequest(io.circe.Json.obj("error" -> io.circe.Json.fromString(err.getMessage)))
          }
    }
  }
}

package weatherapp

import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.implicits._
import org.http4s.circe.CirceEntityDecoder._
import io.circe.Json

class WeatherServiceSpec extends CatsEffectSuite {

  // ── Simple/pure unit tests: temperature characterization ──────────────

  test("characterize: >= 85F is hot") {
    assertEquals(WeatherResponse.characterize(85, "F"), "hot")
    assertEquals(WeatherResponse.characterize(100, "F"), "hot")
  }

  test("characterize: <= 50F is cold") {
    assertEquals(WeatherResponse.characterize(50, "F"), "cold")
    assertEquals(WeatherResponse.characterize(10, "F"), "cold")
  }

  test("characterize: 51-84F is moderate") {
    assertEquals(WeatherResponse.characterize(51, "F"), "moderate")
    assertEquals(WeatherResponse.characterize(72, "F"), "moderate")
    assertEquals(WeatherResponse.characterize(84, "F"), "moderate")
  }

  test("characterize: converts Celsius to Fahrenheit") {
    // 30°C = 86°F → hot
    assertEquals(WeatherResponse.characterize(30, "C"), "hot")
    // 0°C = 32°F → cold
    assertEquals(WeatherResponse.characterize(0, "C"), "cold")
    // 20°C = 68°F → moderate
    assertEquals(WeatherResponse.characterize(20, "C"), "moderate")
  }

  // ── Route-level tests with a stubbed service ───────────────────

  private def stubService(result: IO[WeatherResponse]): WeatherService[IO] =
    new WeatherService[IO] {
      def forecast(lat: Double, lon: Double): IO[WeatherResponse] = result
    }

  private val sampleResponse = WeatherResponse("Sunny", 75, "F", "moderate")

  test("GET /forecast returns 200 with valid query params") {
    val routes = WeatherRoutes.routes[IO](stubService(IO.pure(sampleResponse)))
    val req    = Request[IO](Method.GET, uri"/forecast?lat=40.0&lon=-74.0")

    routes.orNotFound.run(req).flatMap { resp =>
      IO {
        assertEquals(resp.status, Status.Ok)
      } *> resp.as[Json].map { json =>
        assertEquals(json.hcursor.get[String]("shortForecast").toOption, Some("Sunny"))
        assertEquals(json.hcursor.get[Int]("temperature").toOption, Some(75))
        assertEquals(json.hcursor.get[String]("characterization").toOption, Some("moderate"))
      }
    }
  }

  test("GET /forecast returns 400 when service fails") {
    val failing = stubService(IO.raiseError(new RuntimeException("NWS is down")))
    val routes  = WeatherRoutes.routes[IO](failing)
    val req     = Request[IO](Method.GET, uri"/forecast?lat=40.0&lon=-74.0")

    routes.orNotFound.run(req).map { resp =>
      assertEquals(resp.status, Status.BadRequest)
    }
  }

  test("GET /forecast returns 404 for missing query params") {
    val routes = WeatherRoutes.routes[IO](stubService(IO.pure(sampleResponse)))
    val req    = Request[IO](Method.GET, uri"/forecast")

    routes.orNotFound.run(req).map { resp =>
      assertEquals(resp.status, Status.NotFound)
    }
  }
}

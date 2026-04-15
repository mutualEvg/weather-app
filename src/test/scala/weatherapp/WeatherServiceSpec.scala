package weatherapp

import cats.effect.{IO, Ref}
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.implicits._
import org.http4s.circe.CirceEntityDecoder._
import io.circe.Json
import scala.concurrent.duration._

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

  // ── Cache decorator tests ─────────────────────────────────────

  private def countingService(ref: Ref[IO, Int]): WeatherService[IO] =
    new WeatherService[IO] {
      def forecast(lat: Double, lon: Double): IO[WeatherResponse] =
        ref.update(_ + 1).as(sampleResponse)
    }

  test("withCache: second call for same coords hits cache, not underlying") {
    for {
      callCount <- Ref.of[IO, Int](0)
      cache     <- WeatherCache.inMemory[IO](5.minutes)
      service    = WeatherService.withCache[IO](countingService(callCount), cache)
      r1        <- service.forecast(40.0, -74.0)
      r2        <- service.forecast(40.0, -74.0)
      count     <- callCount.get
    } yield {
      assertEquals(r1, sampleResponse)
      assertEquals(r2, sampleResponse)
      assertEquals(count, 1) // underlying only called once
    }
  }

  test("withCache: different coords are cached independently") {
    for {
      callCount <- Ref.of[IO, Int](0)
      cache     <- WeatherCache.inMemory[IO](5.minutes)
      service    = WeatherService.withCache[IO](countingService(callCount), cache)
      _         <- service.forecast(40.0, -74.0)
      _         <- service.forecast(25.76, -80.19)
      count     <- callCount.get
    } yield assertEquals(count, 2)
  }

  // ── Retry decorator tests ─────────────────────────────────────

  test("withRetry: succeeds after transient failures") {
    for {
      failsLeft <- Ref.of[IO, Int](2)
      flaky      = new WeatherService[IO] {
                     def forecast(lat: Double, lon: Double): IO[WeatherResponse] =
                       failsLeft.modify { n =>
                         if (n > 0) (n - 1, IO.raiseError[WeatherResponse](new RuntimeException("flake")))
                         else       (0, IO.pure(sampleResponse))
                       }.flatten
                   }
      service    = WeatherService.withRetry[IO](flaky, maxRetries = 3, baseDelay = 10.millis)
      result    <- service.forecast(40.0, -74.0)
    } yield assertEquals(result, sampleResponse)
  }

  test("withRetry: gives up after exhausting retries") {
    val alwaysFails = new WeatherService[IO] {
      def forecast(lat: Double, lon: Double): IO[WeatherResponse] =
        IO.raiseError(new RuntimeException("permanently down"))
    }
    val service = WeatherService.withRetry[IO](alwaysFails, maxRetries = 2, baseDelay = 10.millis)

    service.forecast(40.0, -74.0).attempt.map { result =>
      assert(result.isLeft)
      assertEquals(result.left.toOption.get.getMessage, "permanently down")
    }
  }
}

package weatherapp

import cats.effect.{Clock, Concurrent, Ref}
import cats.syntax.all._
import scala.concurrent.duration.FiniteDuration

trait WeatherCache[F[_]] {
  def get(key: String): F[Option[WeatherResponse]]
  def put(key: String, value: WeatherResponse): F[Unit]
}

object WeatherCache {

  private case class Entry(value: WeatherResponse, expiresAtMillis: Long)

  def inMemory[F[_]: Concurrent: Clock](ttl: FiniteDuration): F[WeatherCache[F]] =
    Ref.of[F, Map[String, Entry]](Map.empty).map { ref =>
      new WeatherCache[F] {
        def get(key: String): F[Option[WeatherResponse]] =
          for {
            now   <- Clock[F].realTime.map(_.toMillis)
            store <- ref.get
          } yield store.get(key).filter(_.expiresAtMillis > now).map(_.value)

        def put(key: String, value: WeatherResponse): F[Unit] =
          Clock[F].realTime.map(_.toMillis).flatMap { now =>
            ref.update(_.updated(key, Entry(value, now + ttl.toMillis)))
          }
      }
    }

  def cacheKey(lat: Double, lon: Double): String = f"$lat%.2f,$lon%.2f"
}

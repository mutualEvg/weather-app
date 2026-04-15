package weatherapp

import pureconfig._
import pureconfig.generic.auto._
import scala.concurrent.duration.FiniteDuration

final case class ServerConfig(host: String, port: Int)
final case class NwsClientConfig(timeoutSeconds: Int, maxRetries: Int)
final case class CacheConfig(ttlMinutes: Int)

final case class AppConfig(
    server: ServerConfig,
    nwsClient: NwsClientConfig,
    cache: CacheConfig
) {
  def clientTimeout: FiniteDuration = {
    import scala.concurrent.duration._
    nwsClient.timeoutSeconds.seconds
  }
  def cacheTtl: FiniteDuration = {
    import scala.concurrent.duration._
    cache.ttlMinutes.minutes
  }
}

object AppConfig {
  def load: AppConfig = ConfigSource.default.loadOrThrow[AppConfig]
}

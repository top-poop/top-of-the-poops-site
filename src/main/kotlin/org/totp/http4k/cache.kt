import org.http4k.core.*
import org.http4k.events.Event
import org.http4k.events.Events
import redis.clients.jedis.commands.JedisCommands
import redis.clients.jedis.exceptions.JedisConnectionException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.time.Duration


fun sha256Key(uri: Uri): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(uri.toString().toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}

enum class CacheEventType { HIT, MISS, INSERT }
data class CacheEvent(val type: CacheEventType, val uri: Uri, val key: String) : Event

fun redisCacheFilter(
    redis: JedisCommands,
    events: Events,
    prefix: String = "cache",
    ttl: (Request) -> Duration = { Duration.ofMinutes(5) },
    key: (Request) -> String = { it.uri.toString() }
): Filter = Filter { next ->
    { request ->
        val cacheKey = key(request)
        val bodyKey = "$prefix:body:$cacheKey"
        val headersKey = "$prefix:headers:$cacheKey"

        try {
            redis.get(bodyKey)?.let { cachedBody ->
                events(CacheEvent(CacheEventType.HIT, request.uri, bodyKey))
                redis.hgetAll(headersKey).entries.fold(
                    Response(Status.OK).body(cachedBody)
                ) { resp, (name, value) -> resp.header(name, value) }
            } ?: run {
                events(CacheEvent(CacheEventType.MISS, request.uri, bodyKey))

                next(request).also { response ->
                    if (response.status == Status.OK) {
                        events(CacheEvent(CacheEventType.INSERT, request.uri, bodyKey))

                        val cacheTtl = ttl(request)
                        try {
                            with(redis) {
                                set(bodyKey, response.bodyString())
                                expire(bodyKey, cacheTtl.toSeconds())

                                hset(headersKey, response.headers.toMap())
                                expire(headersKey, cacheTtl.toSeconds())
                            }
                        } catch (_: JedisConnectionException) {
                            // ignore
                        } catch (_: SocketTimeoutException) {
                            // ignore
                        }
                    }
                }
            }
        } catch (_: JedisConnectionException) {
            next(request)
        } catch (_: SocketTimeoutException) {
            next(request)
        }
    }
}

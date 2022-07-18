package com.github.diamondminer88.myq

import io.ktor.client.*
import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

internal suspend fun <T> disableRedirects(http: HttpClient, block: suspend () -> T): T {
	http.config { followRedirects = false }
	try {
		return block()
	} catch (t: Throwable) {
		throw t
	} finally {
		http.config { followRedirects = true }
	}
}

/**
 * Convert the Set-Cookie headers from a response to the Cookie header format for requests.
 */
internal fun convertSetCookies(headers: Headers): String {
	return headers
		.getAll(HttpHeaders.SetCookie)
		?.joinToString("; ") { cookie -> cookie.takeWhile { it != ';' } }
		?: throw Error("Failed to parse cookies")
}

public class UUIDSerializer : KSerializer<UUID> {
	override val descriptor: SerialDescriptor
		get() = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

	override fun deserialize(decoder: Decoder): UUID {
		return UUID.fromString(decoder.decodeString())
	}

	override fun serialize(encoder: Encoder, value: UUID) {
		encoder.encodeString(value.toString())
	}
}

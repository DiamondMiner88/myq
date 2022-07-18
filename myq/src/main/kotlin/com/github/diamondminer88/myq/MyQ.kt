package com.github.diamondminer88.myq

import com.github.diamondminer88.myq.model.MyQAuthResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.util.*

class MyQAccount

class MyQ {
	private val http: HttpClient = HttpClient {
		install(ContentNegotiation) {
			json(Json {
				ignoreUnknownKeys = true
			})
		}
	}

	private var accounts: Array<MyQAccount>? = null
	private var tokenScope: String? = null
	private var accessToken: String? = null
	private var expiresAt: Long? = null
	var refreshToken: String? = null
		private set

	/**
	 * Retrieve an access token & refresh token using the OAuth2 login flow.
	 * It's recommended to save the refresh token and re-use it instead of re-authorizing every single time.
	 */
	@Throws()
	suspend fun login(email: String, password: String) {
		val pkceVerifier = PkceUtil.generateCodeVerifier()
		val pkceChallenge = PkceUtil.generateCodeChallenge(pkceVerifier)

		val authPage = http.get("https://partner-identity.myq-cloud.com/connect/authorize") {
			header(HttpHeaders.UserAgent, "null")
			url.parameters.apply {
				append("client_id", "IOS_CGI_MYQ")
				append("code_challenge", pkceChallenge)
				append("code_challenge_method", "S256")
				append("redirect_uri", "com.myqops://ios")
				append("response_type", "code")
				append("scope", "MyQ_Residential offline_access")
			}
		}

		val authPageCookies = convertSetCookies(authPage.headers)
		val requestVerificationToken = authPage.bodyAsText().let { body ->
			"""<input.+?name="__RequestVerificationToken".+?value="(.+?)""""
				.toRegex()
				.find(body)
				?.groupValues
				?.get(1)
				?: throw Error("Failed to parse auth page")
		}

		val loginResponse = disableRedirects {
			http.post(authPage.request.url) {
				header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded)
				header(HttpHeaders.Cookie, authPageCookies)
				header(HttpHeaders.UserAgent, "null")

				val loginInfo = Parameters.build {
					append("Email", email)
					append("Password", password)
					append("__RequestVerificationToken", requestVerificationToken)
				}
				setBody(loginInfo.formUrlEncode())
			}
		}

		if ((loginResponse.headers.getAll(HttpHeaders.SetCookie)?.size ?: 0) < 2) {
			throw Error("Invalid com.github.diamondminer88.myq.MyQ login credentials!")
		}

		val oauthRedirectResponse = disableRedirects {
			val oauthRedirect = loginResponse.headers[HttpHeaders.Location]
				?: throw Error("Could not find redirect in login page")
			val oauthRedirectUrl = URLBuilder(oauthRedirect).apply {
				host = loginResponse.request.url.host
				protocol = loginResponse.request.url.protocol
			}
			http.get(oauthRedirectUrl.build()) {
				header(HttpHeaders.UserAgent, "null")
				header(HttpHeaders.Cookie, convertSetCookies(loginResponse.headers))
			}
		}

		val appRedirect = oauthRedirectResponse.headers[HttpHeaders.Location]
			?.let { Url(it) }
			?: throw Error("Failed to get app redirect")

		val requestBody = Parameters.build {
			append("client_id", "IOS_CGI_MYQ")
			append("client_secret", Base64.getDecoder().decode("VUQ0RFhuS3lQV3EyNUJTdw==").toString())
			append("redirect_uri", "com.myqops://ios")
			append("grant_type", "authorization_code")
			append("code_verifier", pkceVerifier)
			append(
				"code", appRedirect.parameters["code"]
					?: throw Error("Failed to get oauth code from app redirect")
			)
			append(
				"scope", appRedirect.parameters["scope"]
					?: throw Error("Failed to get scope from app redirect")
			)
		}
		val request = http.post("https://partner-identity.myq-cloud.com/connect/token") {
			header(HttpHeaders.UserAgent, "null")
			header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded)
			setBody(requestBody.formUrlEncode())
		}

		if (!request.status.isSuccess()) {
			throw Error("Failed to get access token: ${request.bodyAsText()}")
		}

		val auth = request.body<MyQAuthResponse>()
		this.refreshToken = auth.refreshToken
		this.accessToken = auth.accessToken
		this.tokenScope = auth.tokenScope
		this.expiresAt = System.currentTimeMillis() + auth.expiresInSeconds * 1000
	}

	/**
	 * Re-use an existing refresh token from a previous login.
	 */
	@Throws
	suspend fun login(refreshToken: String) {
		this.refreshToken = refreshToken
		refreshLogin()
	}

	private suspend fun refreshLogin() {
		if (this.refreshToken == null) {
			throw Error("This MyQ instance has been initialized with a login yet!")
		}
	}

	private suspend fun <T> disableRedirects(block: suspend () -> T): T {
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
	private fun convertSetCookies(headers: Headers): String {
		return headers
			.getAll(HttpHeaders.SetCookie)
			?.joinToString("; ") { cookie -> cookie.takeWhile { it != ';' } }
			?: throw Error("Failed to parse cookies")
	}
}

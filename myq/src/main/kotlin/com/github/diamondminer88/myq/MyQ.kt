@file:Suppress("MemberVisibilityCanBePrivate")

package com.github.diamondminer88.myq

import com.github.diamondminer88.myq.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.util.*

public class MyQ {
	private val http: HttpClient = HttpClient {
		install(ContentNegotiation) {
			json(Json {
				ignoreUnknownKeys = true
			})
		}
		defaultRequest {
			if (accessToken != null) {
				header(HttpHeaders.Authorization, accessToken)
			}
		}
	}

	private var accounts: List<MyQAccount>? = null
	private var tokenScope: String? = null
	private var accessToken: String? = null
	private var expiresAt: Long? = null
	private var refreshToken: String? = null

	/**
	 * Get the cached refresh token after a login has already occurred.
	 */
	public fun getRefreshToken(): String {
		return refreshToken
			?: throw Error("This MyQ instance has not been initialized with a login yet!")
	}

	/**
	 * Gets the cached myQ homes (accounts) for the current token.
	 */
	public fun getCachedAccounts(): List<MyQAccount> {
		return accounts
			?: throw Error("This MyQ instance has not been initialized with a login yet!")
	}

	private fun clearInternalState() {
		http.config { followRedirects = true }
		accounts = null
		tokenScope = null
		accessToken = null
		expiresAt = null
		refreshToken = null
	}

	/**
	 * Retrieve an access token & refresh token using the OAuth2 login flow.
	 * It's recommended to save the refresh token and re-use it instead of re-authorizing every single time.
	 */
	public suspend fun login(email: String, password: String) {
		clearInternalState()

		val pkceVerifier = PkceUtils.generateCodeVerifier()
		val pkceChallenge = PkceUtils.generateCodeChallenge(pkceVerifier)

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

		val loginResponse = disableRedirects(http) {
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

		val oauthRedirectResponse = disableRedirects(http) {
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

		updateAuthState(request.body())
		refreshAccounts()
	}

	/**
	 * Re-use an existing refresh token from a previous login.
	 */
	@Throws
	public suspend fun login(refreshToken: String) {
		clearInternalState()
		this.refreshToken = refreshToken
		refreshToken()
		refreshAccounts()
	}

	/**
	 * Gets a new access token through the refresh token.
	 */
	internal suspend fun refreshToken() {
		val token = getRefreshToken()

		val body = Parameters.build {
			append("client_id", "IOS_CGI_MYQ")
			append("client_secret", Base64.getDecoder().decode("VUQ0RFhuS3lQV3EyNUJTdw==").toString())
			append("redirect_uri", "com.myqops://ios")
			append("grant_type", "refresh_token")
			append("refresh_token", token)
			append("scope", tokenScope!!)
		}

		val response = http.post("https://partner-identity.myq-cloud.com/connect/token") {
			header(HttpHeaders.UserAgent, "null")
			header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded)
			setBody(body.formUrlEncode())
		}

		if (!response.status.isSuccess()) {
			throw Error("Failed to get refresh token: ${response.bodyAsText()}")
		}

		updateAuthState(response.body())
	}

	private fun updateAuthState(auth: MyQAuthResponse) {
		this.refreshToken = auth.refreshToken
		this.accessToken = auth.tokenType + ' ' + auth.accessToken
		this.tokenScope = auth.tokenScope
		this.expiresAt = System.currentTimeMillis() + auth.expiresInSeconds * 1000
	}

	internal suspend fun refreshAccounts() {
		val response = http.get("https://accounts.myq-cloud.com/api/v6.0/accounts")
		val data = response.body<MyQAccountsResponse>()
		this.accounts = data.accounts
	}

	/**
	 * Fetch all devices from all accounts/homes.
	 */
	public suspend fun fetchDevices(): List<MyQDevice> {
		return getCachedAccounts().flatMap {
			fetchDevices(it.id)
		}
	}

	/**
	 * Fetch all devices for a specific account/home.
	 */
	public suspend fun fetchDevices(account: MyQAccount): List<MyQDevice> {
		return fetchDevices(account.id)
	}

	/**
	 * Fetch all devices for a specific account/home.
	 */
	public suspend fun fetchDevices(accountId: UUID): List<MyQDevice> {
		return http.get("https://devices.myq-cloud.com/api/v5.2/Accounts/${accountId}/Devices")
			.body<MyQDevicesResponse>()
			.devices
			.also { println(it) }
	}
}

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

		val authPage = http.get(MyQData.authUrl) {
			header(HttpHeaders.UserAgent, "null")
			url.parameters.apply {
				append("redirect_uri", MyQData.redirectUri)
				append("client_id", MyQData.clientId)
				append("scope", MyQData.tokenScope)
				append("response_type", "code")
				append("code_challenge_method", "S256")
				append("code_challenge", pkceChallenge)
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
			throw Error("Invalid MyQ login credentials!")
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
		val request = http.post(MyQData.refreshUrl) {
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
	 * Gets a new access token through the refresh token if it's (almost) expired.
	 */
	internal suspend fun refreshToken() {
		val token = getRefreshToken()

		if (expiresAt!! > System.currentTimeMillis())
			return

		val body = Parameters.build {
			append("client_id", MyQData.clientId)
			append("client_secret", MyQData.clientSecret)
			append("redirect_uri", MyQData.redirectUri)
			append("scope", MyQData.tokenScope)
			append("grant_type", "refresh_token")
			append("refresh_token", token)
		}

		val response = http.post(MyQData.refreshUrl) {
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
		this.expiresAt = System.currentTimeMillis() + (auth.expiresInSeconds - 60) * 1000
	}

	internal suspend fun refreshAccounts() {
		refreshToken()
		val response = http.get(MyQData.accountsUrl)
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
		refreshToken()
		return http.get(MyQData.devicesUrl(accountId))
			.body<MyQDevicesResponse>()
			.devices
	}

	/**
	 * Open/close a garage door through myQ.
	 */
	public suspend fun setGarageDoorState(device: MyQDevice, open: Boolean) {
		if (device.deviceFamily != "garagedoor") {
			throw Error("Incompatible device type! (${device.deviceFamily})")
		}

		setGarageDoorState(device.account, device.serial, open)
	}

	/**
	 * Open/close a garage door through myQ.
	 */
	public suspend fun setGarageDoorState(accountId: UUID, deviceSerial: String, open: Boolean) {
		refreshToken()

		val command = if (open) "open" else "close"
		val response = http.put(MyQData.garageDoorUrl(accountId, deviceSerial, command))

		if (!response.status.isSuccess()) {
			throw Error("Failed to set garage door state! ${response.bodyAsText()}")
		}
	}

	/**
	 * Turn on/off a lamp through myQ.
	 */
	public suspend fun setLampState(device: MyQDevice, isOn: Boolean) {
		if (device.deviceFamily != "lamps") {
			throw Error("Incompatible device type! (${device.deviceFamily})")
		}

		setLampState(device.account, device.serial, isOn)
	}

	/**
	 * Turn on/off a lamp through myQ.
	 */
	public suspend fun setLampState(accountId: UUID, deviceSerial: String, isOn: Boolean) {
		refreshToken()

		val command = if (isOn) "turnon" else "turnoff"
		val response = http.put(MyQData.lampUrl(accountId, deviceSerial, command))

		if (!response.status.isSuccess()) {
			throw Error("Failed to set lamp state! ${response.bodyAsText()}")
		}
	}
}

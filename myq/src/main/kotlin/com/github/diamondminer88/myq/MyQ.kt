import com.github.diamondminer88.myq.PkceUtil
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class MyQAccount

class MyQ {
    private val http: HttpClient = HttpClient()
    private var accounts: Array<MyQAccount>? = null
    private var tokenScope: String? = null
    private var accessToken: String? = null
    private var nextRefresh: Long? = null
    var refreshToken: String? = null
        private set

    /**
     * Retrieve an access token & refresh token using the OAuth2 login flow.
     * It's recommended to save the refresh token and re-use it instead of re-authorizing every single time.
     */
    suspend fun login(email: String, password: String): Boolean {
        val token = getOAuthToken(email, password)
        return false
    }

    /**
     * Re-use an existing refresh token from a previous login.
     */
    suspend fun login(refreshToken: String): Boolean {
        this.refreshToken = refreshToken
        return false
    }

    private suspend fun getOAuthToken(email: String, password: String): String? {
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
            throw Error("Invalid MyQ login credentials!")
        }

        //// Complete 1st OAuth2 flow by visiting the redirect ////

        val oauthRedirect = loginResponse.headers[HttpHeaders.Location]
            ?: throw Error("Could not find redirect in login page")
        val oauthRedirectUrl = URLBuilder(oauthRedirect).apply {
            host = loginResponse.request.url.host
            protocol = loginResponse.request.url.protocol
        }

        http.config { followRedirects = false }
        val oauthRedirectResponse = http.get(oauthRedirectUrl.build()) {
            header(HttpHeaders.UserAgent, "null")
            header(HttpHeaders.Cookie, convertSetCookies(loginResponse.headers))
        }
        http.config { followRedirects = true }


        return null
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

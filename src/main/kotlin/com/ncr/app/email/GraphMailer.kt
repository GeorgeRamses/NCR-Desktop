package com.ncr.app.email

import com.ncr.app.data.GraphAccount
import com.ncr.app.data.GraphRepository
import com.ncr.app.email.EmailService.SendResult
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Sends mail through Microsoft Graph (POST /me/sendMail) over HTTPS.
 * Sign-in = OAuth2 authorization-code flow with PKCE in the user's browser, so normal password +
 * Authenticator approval are used. No SMTP, no app password. Only a refresh token is stored.
 */
object GraphMailer {

    private const val SCOPE = "openid profile offline_access https://graph.microsoft.com/Mail.Send"
    private const val MAX_ATTACHMENT_BYTES = 2_800_000L // Graph limit for inline attachments is ~3 MB

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedExpiryMs: Long = 0

    // ---------- helpers ----------
    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
    private fun form(vararg p: Pair<String, String>) = p.joinToString("&") { "${enc(it.first)}=${enc(it.second)}" }
    private fun b64url(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun tokenUrl(tenant: String) = "https://login.microsoftonline.com/${enc(tenant)}/oauth2/v2.0/token"

    private fun jsonEscape(s: String): String = buildString {
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }

    private fun jsonField(json: String, name: String): String? =
        Regex("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)
            ?.replace("\\/", "/")

    private fun jwtClaim(jwt: String, claim: String): String? {
        val parts = jwt.split(".")
        if (parts.size < 2) return null
        return try {
            jsonField(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8), claim)
        } catch (e: Exception) {
            null
        }
    }

    private fun describe(body: String): String =
        (jsonField(body, "error_description") ?: jsonField(body, "message") ?: body.take(300))
            .substringBefore("\\r\\n")

    private fun postForm(url: String, body: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )

    private fun cache(accessToken: String, responseBody: String) {
        val seconds = Regex("\"expires_in\"\\s*:\\s*(\\d+)").find(responseBody)?.groupValues?.get(1)?.toLongOrNull() ?: 3000
        cachedToken = accessToken
        cachedExpiryMs = System.currentTimeMillis() + seconds * 1000
    }

    // ---------- sign in / out ----------
    suspend fun signIn(tenant: String, client: String): SendResult = withContext(Dispatchers.IO) {
        if (tenant.isBlank() || client.isBlank()) {
            return@withContext SendResult(false, "Enter the Tenant ID and Client ID first.")
        }
        var server: HttpServer? = null
        try {
            val rnd = SecureRandom()
            val verifier = b64url(ByteArray(48).also { rnd.nextBytes(it) })
            val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
            val state = b64url(ByteArray(16).also { rnd.nextBytes(it) })

            // Tiny local web server that receives the redirect from Microsoft
            val callback = CompletableFuture<Map<String, String>>()
            server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
            server.createContext("/") { ex ->
                val params = (ex.requestURI.rawQuery ?: "").split("&")
                    .filter { it.contains("=") }
                    .associate {
                        val kv = it.split("=", limit = 2)
                        kv[0] to URLDecoder.decode(kv[1], "UTF-8")
                    }
                val page = "<html><body style=\"font-family:Arial\"><h3>You can close this window and return to the NCR app.</h3></body></html>"
                    .toByteArray(Charsets.UTF_8)
                ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                ex.sendResponseHeaders(200, page.size.toLong())
                ex.responseBody.use { it.write(page) }
                if (params.containsKey("code") || params.containsKey("error")) callback.complete(params)
            }
            server.start()
            val redirect = "http://localhost:${server.address.port}"

            val authUrl = "https://login.microsoftonline.com/${enc(tenant)}/oauth2/v2.0/authorize?" + form(
                "client_id" to client,
                "response_type" to "code",
                "redirect_uri" to redirect,
                "response_mode" to "query",
                "scope" to SCOPE,
                "state" to state,
                "code_challenge" to challenge,
                "code_challenge_method" to "S256",
                "prompt" to "select_account"
            )
            if (!Desktop.isDesktopSupported()) return@withContext SendResult(false, "Cannot open a browser on this PC.")
            Desktop.getDesktop().browse(URI(authUrl))

            val params = try {
                callback.get(3, TimeUnit.MINUTES)
            } catch (e: java.util.concurrent.TimeoutException) {
                return@withContext SendResult(false, "Sign-in timed out (3 minutes). Try again.")
            }
            params["error"]?.let {
                return@withContext SendResult(false, "Sign-in failed: ${params["error_description"] ?: it}")
            }
            if (params["state"] != state) return@withContext SendResult(false, "Sign-in failed: state mismatch.")
            val code = params["code"] ?: return@withContext SendResult(false, "Sign-in failed: no code returned.")

            val resp = postForm(
                tokenUrl(tenant),
                form(
                    "client_id" to client,
                    "scope" to SCOPE,
                    "code" to code,
                    "redirect_uri" to redirect,
                    "grant_type" to "authorization_code",
                    "code_verifier" to verifier
                )
            )
            val body = resp.body()
            if (resp.statusCode() != 200) return@withContext SendResult(false, "Token request failed: ${describe(body)}")

            val refresh = jsonField(body, "refresh_token")
                ?: return@withContext SendResult(false, "No refresh token returned (offline_access was not granted).")
            val access = jsonField(body, "access_token")
                ?: return@withContext SendResult(false, "No access token returned.")
            val email = jsonField(body, "id_token")?.let { jwtClaim(it, "preferred_username") ?: jwtClaim(it, "email") }

            GraphRepository.save(GraphAccount(tenant, client, refresh, email))
            cache(access, body)
            SendResult(true, "Signed in${email?.let { " as $it" } ?: ""}")
        } catch (e: Exception) {
            SendResult(false, "Sign-in failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            server?.stop(0)
        }
    }

    fun signOut() {
        cachedToken = null
        GraphRepository.get()?.let { GraphRepository.save(it.copy(refreshToken = null, accountEmail = null)) }
    }

    // ---------- access token (refreshed silently) ----------
    private fun accessToken(acc: GraphAccount): String {
        cachedToken?.let { if (System.currentTimeMillis() < cachedExpiryMs - 60_000) return it }
        val refresh = acc.refreshToken ?: throw Exception("Not signed in")
        val resp = postForm(
            tokenUrl(acc.tenantId),
            form(
                "client_id" to acc.clientId,
                "scope" to SCOPE,
                "refresh_token" to refresh,
                "grant_type" to "refresh_token"
            )
        )
        val body = resp.body()
        if (resp.statusCode() != 200) {
            cachedToken = null
            throw Exception("${describe(body)}  -> sign in again in Settings.")
        }
        val access = jsonField(body, "access_token") ?: throw Exception("No access token returned")
        jsonField(body, "refresh_token")?.let { GraphRepository.save(acc.copy(refreshToken = it)) }
        cache(access, body)
        return access
    }

    // ---------- send ----------
    suspend fun sendMail(
        recipients: List<String>,
        subject: String,
        htmlBody: String,
        pdfFile: File? = null
    ): SendResult = withContext(Dispatchers.IO) {
        if (recipients.isEmpty()) return@withContext SendResult(false, "No recipients")
        val acc = GraphRepository.get()
        if (acc == null || acc.refreshToken.isNullOrBlank()) {
            return@withContext SendResult(false, "Not signed in. Open Settings > Microsoft account > Sign in.")
        }
        try {
            val token = accessToken(acc)

            var note = ""
            var attachmentJson = ""
            if (pdfFile != null && pdfFile.exists() && pdfFile.length() > 0) {
                if (pdfFile.length() > MAX_ATTACHMENT_BYTES) {
                    note = " (PDF over 3 MB was not attached)"
                } else {
                    val b64 = Base64.getEncoder().encodeToString(pdfFile.readBytes())
                    attachmentJson = ",\"attachments\":[{\"@odata.type\":\"#microsoft.graph.fileAttachment\"," +
                        "\"name\":\"NCR_Register.pdf\",\"contentType\":\"application/pdf\",\"contentBytes\":\"$b64\"}]"
                }
            }

            val to = recipients.joinToString(",") {
                "{\"emailAddress\":{\"address\":\"${jsonEscape(it.trim())}\"}}"
            }
            val json = "{\"message\":{\"subject\":\"${jsonEscape(subject)}\"," +
                "\"body\":{\"contentType\":\"HTML\",\"content\":\"${jsonEscape(htmlBody)}\"}," +
                "\"toRecipients\":[$to]$attachmentJson},\"saveToSentItems\":true}"

            val resp = http.send(
                HttpRequest.newBuilder(URI("https://graph.microsoft.com/v1.0/me/sendMail"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, Charsets.UTF_8))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
            when (resp.statusCode()) {
                202 -> SendResult(true, "Sent through Microsoft Graph$note")
                401 -> { cachedToken = null; SendResult(false, "Microsoft rejected the sign-in (401). ${describe(resp.body())}") }
                403 -> SendResult(false, "Permission denied (403). The app needs the Mail.Send permission approved. ${describe(resp.body())}")
                else -> SendResult(false, "Graph error ${resp.statusCode()}: ${describe(resp.body())}")
            }
        } catch (e: Exception) {
            SendResult(false, "Graph send failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}

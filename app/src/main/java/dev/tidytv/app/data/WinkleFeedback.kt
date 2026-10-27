package dev.tidytv.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** What kind of message the user is sending. [wire] is the value the Winkle endpoint expects. */
enum class FeedbackKind(val wire: String, val label: String) {
    BUG("bug", "Something went wrong"),
    PRAISE("praise", "It worked great"),
    FEATURE("feature", "An idea"),
    QUESTION("question", "A question"),
}

/** One message from the feedback sheet, before device details are added. */
data class FeedbackDraft(
    val kind: FeedbackKind,
    val message: String,
    val email: String = "",
    val page: String = "",
    /** The shared log text (already scrubbed of serials, Wi-Fi names and addresses), or null. */
    val log: String? = null,
    /** A JPEG, already resized. */
    val screenshot: ByteArray? = null,
)

/**
 * Sends feedback to the shared Winkle feedback endpoint (see the winkle-feedback skill). Each
 * message becomes a row in Joe's feedback sheet, an email, and an item in Winkle HQ.
 */
class WinkleFeedback(
    private val version: String,
    private val device: String,
    private val os: String,
    private val userId: String,
    private val http: Http = Http.URL_CONNECTION,
) {
    /** The two HTTP calls the endpoint needs, separate so the logic can be tested without a network. */
    interface Http {
        /** POSTs [json] without following redirects. Returns the status and the Location header. */
        fun post(url: String, json: String): Pair<Int, String?>
        fun get(url: String): String

        companion object {
            val URL_CONNECTION = object : Http {
                override fun post(url: String, json: String): Pair<Int, String?> {
                    val c = URL(url).openConnection() as HttpURLConnection
                    try {
                        c.requestMethod = "POST"
                        c.instanceFollowRedirects = false
                        c.connectTimeout = 15_000
                        c.readTimeout = 45_000
                        c.doOutput = true
                        c.setRequestProperty("Content-Type", "application/json")
                        c.outputStream.use { it.write(json.toByteArray()) }
                        return c.responseCode to c.getHeaderField("Location")
                    } finally {
                        c.disconnect()
                    }
                }

                override fun get(url: String): String {
                    val c = URL(url).openConnection() as HttpURLConnection
                    try {
                        c.connectTimeout = 15_000
                        c.readTimeout = 15_000
                        return c.inputStream.bufferedReader().use { it.readText() }
                    } finally {
                        c.disconnect()
                    }
                }
            }
        }
    }

    fun payload(d: FeedbackDraft): JSONObject = JSONObject().apply {
        put("app", APP_NAME)
        put("type", d.kind.wire)
        put("feedback", buildString {
            append(d.message.trim())
            // The log goes in the message: the endpoint has no field for it, and it is what
            // turns "it didn't work on my TV" into a test fixture.
            d.log?.takeIf { it.isNotBlank() }?.let { append("\n\n--- TidyTV log ---\n").append(LogTrim.fit(it, MAX_LOG)) }
        })
        put("page", d.page)
        put("platform", "android")
        put("version", version)
        put("device", device)
        put("os", os)
        put("email", d.email.trim())
        put("user_id", userId)
        put("source", "in-app")
        d.screenshot?.let { put("screenshot_base64", Base64.encodeToString(it, Base64.NO_WRAP)) }
    }

    /** True once the message is saved. Never throws; the caller keeps the user's text on false. */
    suspend fun send(d: FeedbackDraft): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val (status, location) = http.post(ENDPOINT, payload(d).toString())
            when (status) {
                // Apps Script saves the row, then redirects to the result. The 302 means saved,
                // unless the result says otherwise (a rate-limited post answers success:false).
                302 -> location == null || runCatching { JSONObject(http.get(location)).optBoolean("success", true) }.getOrDefault(true)
                200 -> true
                else -> false
            }
        }.getOrDefault(false)
    }

    companion object {
        const val ENDPOINT = "https://script.google.com/macros/s/AKfycby0QaDRjH985eQtheMNWZ-ZkjUB17WFZx1FoLygxoey3Q301siHewE_Nb_Mb2Xj90yF/exec"

        /** Must match TidyTV's `name` in winkle-hq/config/apps.json. */
        const val APP_NAME = "TidyTV"
        /** The feedback sheet keeps only the first 5,000 characters of a message, so the log is fitted to that (see LogTrim). */
        private const val MAX_LOG = 4_800

        /** Decodes a picked image and re-encodes it as a JPEG of at most 1280 px, quality 70. */
        fun shrinkScreenshot(bytes: ByteArray): ByteArray? = runCatching {
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val scale = minOf(1f, 1280f / maxOf(src.width, src.height))
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
        }.getOrNull()
    }
}

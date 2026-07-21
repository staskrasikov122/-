package org.gsgit.admin.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

data class ApkVerification(
    val expectedSha256: String,
    val actualSha256: String,
    val bytesRead: Long,
) {
    val matches: Boolean get() = expectedSha256.equals(actualSha256, ignoreCase = true)
}

class ApkVerifier {
    suspend fun verify(url: String, expectedSha256: String): ApkVerification = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw ApiFailure.BadRequest("Для проверки APK требуется HTTPS-ссылка")
        }
        if (!SHA_256.matches(expectedSha256.trim())) {
            throw ApiFailure.BadRequest("SHA-256 должен содержать 64 шестнадцатеричных символа")
        }
        val connection = try {
            (URL(url).openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream")
                setRequestProperty("User-Agent", "GsGit-Admin")
            }
        } catch (_: Exception) {
            throw ApiFailure.BadRequest("Некорректная ссылка на APK")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) throw ApiFailure.Server(status)
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_APK_BYTES) throw ApiFailure.BadRequest("APK превышает допустимый размер проверки")

            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_APK_BYTES) throw ApiFailure.BadRequest("APK превышает допустимый размер проверки")
                    digest.update(buffer, 0, read)
                }
            }
            ApkVerification(
                expectedSha256 = expectedSha256.lowercase(),
                actualSha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) },
                bytesRead = total,
            )
        } catch (failure: ApiFailure) {
            throw failure
        } catch (_: IOException) {
            throw ApiFailure.Unreachable()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val SHA_256 = Regex("^[0-9a-fA-F]{64}$")
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 120_000
        const val MAX_APK_BYTES = 512L * 1024L * 1024L
    }
}

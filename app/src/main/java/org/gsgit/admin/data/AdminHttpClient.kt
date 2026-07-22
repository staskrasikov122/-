package org.gsgit.admin.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** Один сетевой транспорт для всех серверных админ-ручек. */
object AdminHttpClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun request(
        baseUrl: String,
        method: String,
        path: String,
        adminKey: String?,
        body: JSONObject? = null,
    ): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl$path"
        if (!url.startsWith("https://")) {
            throw ApiFailure.BadRequest("Админ-API доступен только по HTTPS")
        }

        val requestBody = body?.toString()?.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                if (adminKey != null) header("x-admin-key", adminKey)
                method(method, requestBody)
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when (response.code) {
                    in 200..299 -> raw
                    400 -> throw ApiFailure.BadRequest(readError(raw) ?: "Некорректный запрос")
                    401 -> throw ApiFailure.Unauthorized()
                    404 -> throw ApiFailure.NotFound(readError(raw) ?: "Объект не найден")
                    409 -> throw ApiFailure.Conflict(readError(raw) ?: "Операция недоступна")
                    502 -> throw ApiFailure.Upstream(readError(raw) ?: "Внешний сервис отклонил запрос")
                    else -> throw ApiFailure.Server(response.code)
                }
            }
        } catch (failure: ApiFailure) {
            throw failure
        } catch (_: SocketTimeoutException) {
            throw ApiFailure.Unreachable()
        } catch (_: IOException) {
            throw ApiFailure.Unreachable()
        } catch (_: IllegalArgumentException) {
            throw ApiFailure.InvalidResponse()
        }
    }

    suspend fun download(baseUrl: String, path: String, adminKey: String): LmgBackup = withContext(Dispatchers.IO) {
        val url = "$baseUrl$path"
        if (!url.startsWith("https://")) throw ApiFailure.BadRequest("Админ-API доступен только по HTTPS")
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("x-admin-key", adminKey)
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    when (response.code) {
                        400 -> throw ApiFailure.BadRequest(readError(raw) ?: "Некорректный запрос")
                        401 -> throw ApiFailure.Unauthorized()
                        404 -> throw ApiFailure.NotFound(readError(raw) ?: "Объект не найден")
                        else -> throw ApiFailure.Server(response.code)
                    }
                }
                val disposition = response.header("Content-Disposition").orEmpty()
                val remoteName = Regex("""filename="?([^";]+)""").find(disposition)?.groupValues?.getOrNull(1)
                val fileName = remoteName
                    ?.substringAfterLast('/')
                    ?.substringAfterLast('\\')
                    ?.takeIf { it.endsWith(".json", ignoreCase = true) }
                    ?: "lmg-backup-${System.currentTimeMillis()}.json"
                LmgBackup(fileName, response.body?.bytes() ?: throw ApiFailure.InvalidResponse())
            }
        } catch (failure: ApiFailure) {
            throw failure
        } catch (_: SocketTimeoutException) {
            throw ApiFailure.Unreachable()
        } catch (_: IOException) {
            throw ApiFailure.Unreachable()
        } catch (_: IllegalArgumentException) {
            throw ApiFailure.InvalidResponse()
        }
    }

    private fun readError(raw: String): String? = try {
        when (val error = JSONObject(raw).optString("error").takeIf { it.isNotBlank() }) {
            "bad json" -> "Некорректные данные запроса"
            "title and body required" -> "Заголовок и текст обязательны"
            "bad admin key" -> "Неверный admin-key"
            "not found" -> "Объект не найден"
            else -> error
        }
    } catch (_: JSONException) {
        null
    }
}

package com.shieldstream.tv.network

import com.shieldstream.tv.domain.ServerHealthGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real del candado de red: un GET corto a `<serverUrl>/health`.
 *
 * Usa el OkHttpClient inyectado pero con timeouts propios (4 s en total): el cliente general
 * espera hasta 30 s de lectura, demasiado para decidir si el servidor está a mano.
 */
@Singleton
class OkHttpServerHealthGate @Inject constructor(
    client: OkHttpClient,
) : ServerHealthGate {

    private val healthClient: OkHttpClient = client.newBuilder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun reachable(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        val base = serverUrl.trim().takeIf { it.isNotEmpty() } ?: return@withContext false
        val url = (if (base.endsWith("/")) base else "$base/") + "health"
        runCatching {
            val request = Request.Builder().url(url).get().build()
            healthClient.newCall(request).execute().use { response -> response.isSuccessful }
        }.getOrDefault(false)
    }

    internal companion object {
        const val TIMEOUT_SECONDS = 4L
    }
}

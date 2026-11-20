package com.shieldstream.tv.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Ajustes esenciales que se pueden llevar de un dispositivo a otro (QR o texto JSON):
 * servidor propio, servicio debrid y, solo en modo directo, la clave local del debrid.
 *
 * En modo servidor la clave vive en el servidor (el móvil no la tiene), así que NO se exporta.
 */
data class ExportedConfig(
    val serverUrl: String? = null,
    val debridService: String? = null,
    val token: String? = null,
) {
    val isEmpty: Boolean get() = serverUrl == null && debridService == null && token == null
}

object ConfigTransfer {

    const val VERSION = 1

    private val pretty = Json { prettyPrint = true }
    private val tolerant = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Normaliza la configuración actual para exportarla (nunca incluye la clave en modo servidor). */
    fun snapshot(serverUrl: String?, debridService: String, token: String?): ExportedConfig {
        val server = serverUrl?.trim()?.takeIf { it.isNotEmpty() }
        return ExportedConfig(
            serverUrl = server,
            debridService = debridService.trim().takeIf { it.isNotEmpty() },
            token = if (server == null) token?.trim()?.takeIf { it.isNotEmpty() } else null,
        )
    }

    /** JSON estable con solo los campos que existen (los ausentes no se escriben). */
    fun toJson(config: ExportedConfig): String = buildJsonObject {
        put("version", JsonPrimitive(VERSION))
        config.serverUrl?.let { put("serverUrl", JsonPrimitive(it)) }
        config.debridService?.let { put("debridService", JsonPrimitive(it)) }
        config.token?.let { put("token", JsonPrimitive(it)) }
    }.let { pretty.encodeToString(JsonObject.serializer(), it) }

    /**
     * Lectura tolerante: JSON inválido, claves desconocidas o campos ausentes no rompen nada.
     * Devuelve null si el texto no es un JSON de configuración (o si no trae ningún ajuste).
     */
    fun parse(text: String): ExportedConfig? = runCatching {
        val root = tolerant.parseToJsonElement(text.trim()).jsonObject
        val config = ExportedConfig(
            serverUrl = root.text("serverUrl"),
            debridService = root.text("debridService"),
            token = root.text("token"),
        )
        config.takeUnless { it.isEmpty }
    }.getOrNull()

    private fun JsonObject.text(key: String): String? =
        this[key]?.takeIf { it is JsonPrimitive && it.isString }?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

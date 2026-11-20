package com.shieldstream.tv.domain

/**
 * Comprueba que el servidor propio (debrid-proxy) está accesible desde este dispositivo.
 *
 * Solo se usa en modo servidor: si el usuario ha configurado una URL de servidor, antes de
 * resolver/reproducir se pregunta al servidor si responde (GET /health). Fuera de casa sin VPN
 * el móvil no llega y la reproducción se bloquea con un aviso claro.
 */
interface ServerHealthGate {
    /**
     * true si el servidor contesta (HTTP 2xx) en un tiempo corto; false si no hay respuesta.
     * Nunca lanza: cualquier error de red, URL mal formada o timeout cuenta como "no responde".
     */
    suspend fun reachable(serverUrl: String): Boolean
}

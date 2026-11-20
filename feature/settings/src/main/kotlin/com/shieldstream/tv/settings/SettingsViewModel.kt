package com.shieldstream.tv.settings

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.Texts
import com.shieldstream.tv.strings.sectionResource
import com.shieldstream.tv.domain.SourceProvider
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.model.AppLanguage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shieldstream.tv.domain.ApiKeysRepository
import com.shieldstream.tv.domain.ProfileRepository
import com.shieldstream.tv.domain.RequestRepository
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.DebridService
import com.shieldstream.tv.domain.EpisodeTrackingRepository
import com.shieldstream.tv.domain.KeyCheck
import com.shieldstream.tv.domain.SettingsRepository
import com.shieldstream.tv.domain.StremioAddons
import com.shieldstream.tv.media.PlaybackPolicyProvider
import com.shieldstream.tv.model.AudioCodec
import com.shieldstream.tv.model.DeviceCapabilities
import com.shieldstream.tv.model.DeviceOptimization
import com.shieldstream.tv.model.HdrType
import com.shieldstream.tv.model.MemoryProfile
import com.shieldstream.tv.model.Quality
import com.shieldstream.tv.model.SourceExclusions
import com.shieldstream.tv.model.SubtitleAppearance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ServiceStatus {
    data object Missing : ServiceStatus
    data object Checking : ServiceStatus
    data class Ok(val detail: String?) : ServiceStatus
    data class Bad(val message: String) : ServiceStatus
}

data class ServiceUi(
    val service: ApiService,
    val maskedKey: String?,
    val status: ServiceStatus,
)

data class KeyEditor(
    val service: ApiService,
    val hasKey: Boolean,
    val busy: Boolean = false,
    val error: String? = null,
    /** URL de un servidor propio (debrid-proxy); solo relevante para Real-Debrid. Nula = modo directo. */
    val serverUrl: String? = null,
)

private data class PlaybackSettings(
    val bufferSeconds: Int,
    val seekThumbnails: Boolean,
    val debrid: DebridService,
    val optimization: DeviceOptimization,
    val experimentalSubtitleSync: Boolean,
)

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Ready(
        val services: List<ServiceUi>,
        val bufferSeconds: Int,
        val seekThumbnails: Boolean,
        /** Servicio debrid en uso. */
        val debrid: DebridService,
        val optimization: DeviceOptimization,
        val experimentalSubtitleSync: Boolean = true,
        val capabilities: DeviceCapabilities,
        val memoryProfile: MemoryProfile,
        val editor: KeyEditor?,
        val language: AppLanguage = Lang.current,
    ) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeys: ApiKeysRepository,
    private val settings: SettingsRepository,
    private val playbackPolicies: PlaybackPolicyProvider,
    private val episodeTracking: EpisodeTrackingRepository,
    private val texts: Texts,
    private val profiles: ProfileRepository,
    private val requests: RequestRepository,
) : ViewModel() {

    private val statuses = MutableStateFlow<Map<ApiService, ServiceStatus>>(emptyMap())
    private val editor = MutableStateFlow<KeyEditor?>(null)

    /** URL del servidor propio (debrid-proxy); se muestra en el editor de Real-Debrid. */
    val serverUrl: StateFlow<String?> =
        settings.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val playback = combine(
        settings.maxBufferSeconds,
        settings.seekThumbnails,
        settings.debridService,
        settings.deviceOptimization,
        settings.experimentalSubtitleSync,
    ) { buffer, thumbnails, debrid, optimization, experimentalSync ->
        PlaybackSettings(buffer, thumbnails, debrid, optimization, experimentalSync)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        apiKeys.keys,
        statuses,
        playback,
        editor,
    ) { keys, status, playback, edit ->
        val policy = playbackPolicies.resolve(
            playback.optimization,
            playback.bufferSeconds,
            playback.seekThumbnails,
        )
        SettingsUiState.Ready(
            services = ApiService.entries.map { service ->
                val key = keys[service]
                ServiceUi(
                    service = service,
                    maskedKey = if (service == ApiService.AIOSTREAMS) {
                        texts.get(StringsR.string.settings_addons_count, StremioAddons.parse(key.orEmpty()).size)
                    } else {
                        key?.let(::mask)
                    },
                    status = if (key.isNullOrBlank()) ServiceStatus.Missing else status[service] ?: ServiceStatus.Checking,
                )
            },
            bufferSeconds = policy.maxBufferSeconds,
            seekThumbnails = policy.seekThumbnails,
            debrid = playback.debrid,
            optimization = playback.optimization,
            experimentalSubtitleSync = playback.experimentalSubtitleSync,
            capabilities = playbackPolicies.capabilities,
            memoryProfile = policy.memoryProfile,
            editor = edit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    init {
        apiKeys.keys.value.forEach { (service, key) ->
            if (!key.isNullOrBlank()) {
                viewModelScope.launch {
                    val result = apiKeys.check(service, key).toStatus()
                    statuses.update { it + (service to result) }
                }
            }
        }
    }

    fun setLanguage(language: AppLanguage, onSaved: () -> Unit) {
        if (language == settings.language) return
        viewModelScope.launch {
            settings.setLanguage(language)
            onSaved()
        }
    }

    val lightweightUi: StateFlow<Boolean> =
        settings.lightweightUi.stateIn(viewModelScope, SharingStarted.Eagerly, settings.lightweightUiEnabled)

    fun setLightweightUi(enabled: Boolean) {
        viewModelScope.launch { settings.setLightweightUi(enabled) }
    }

    val introAlways: StateFlow<Boolean> =
        settings.introAlways.stateIn(viewModelScope, SharingStarted.Eagerly, settings.introAlwaysEnabled)

    fun setIntroAlways(enabled: Boolean) {
        viewModelScope.launch { settings.setIntroAlways(enabled) }
    }

    val softBackdrops: StateFlow<Boolean> =
        settings.softBackdrops.stateIn(viewModelScope, SharingStarted.Eagerly, settings.softBackdropsEnabled)

    fun setSoftBackdrops(enabled: Boolean) {
        viewModelScope.launch { settings.setSoftBackdrops(enabled) }
    }

    val highResBackdrops: StateFlow<Boolean> =
        settings.highResBackdrops.stateIn(viewModelScope, SharingStarted.Eagerly, settings.highResBackdropsEnabled)

    fun setHighResBackdrops(enabled: Boolean) {
        viewModelScope.launch { settings.setHighResBackdrops(enabled) }
    }

    val homeRowItemLimit: StateFlow<Int> = settings.homeRowItemLimit
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SettingsRepository.DEFAULT_HOME_ROW_ITEM_LIMIT,
        )

    fun changeHomeRowItemLimit(delta: Int) {
        viewModelScope.launch {
            settings.setHomeRowItemLimit(settings.homeRowItemLimit.first() + delta)
        }
    }

    val providers: StateFlow<Set<SourceProvider>> =
        settings.sourceProviders.stateIn(viewModelScope, SharingStarted.Eagerly, SourceProvider.entries.toSet())

    val sourceExclusions: StateFlow<SourceExclusions> = settings.sourceExclusions
        .stateIn(viewModelScope, SharingStarted.Eagerly, SourceExclusions())

    val brokenSourceCount: StateFlow<Int> = settings.brokenSourceIds
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** El primer perfil creado administra las peticiones familiares. */
    /** Cualquier perfil adulto revisa las peticiones de los infantiles (solo se muestra si hay alguna). */
    val isAdmin: StateFlow<Boolean> = combine(profiles.active, requests.requests) { active, pending ->
        !active.kids && pending.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val pendingRequestCount: StateFlow<Int> = requests.requests
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun restoreBrokenSources() {
        viewModelScope.launch { settings.clearBrokenSources() }
    }

    fun toggleExcludedQuality(quality: Quality) {
        viewModelScope.launch {
            val current = settings.sourceExclusions.first()
            val qualities = if (quality in current.qualities) current.qualities - quality else current.qualities + quality
            settings.setSourceExclusions(current.copy(qualities = qualities))
        }
    }

    fun toggleExcludedHdrType(hdrType: HdrType) {
        viewModelScope.launch {
            val current = settings.sourceExclusions.first()
            val hdrTypes = if (hdrType in current.hdrTypes) current.hdrTypes - hdrType else current.hdrTypes + hdrType
            settings.setSourceExclusions(current.copy(hdrTypes = hdrTypes))
        }
    }

    fun toggleExcludedAudioCodec(codec: AudioCodec) {
        viewModelScope.launch {
            val current = settings.sourceExclusions.first()
            val audioCodecs = if (codec in current.audioCodecs) current.audioCodecs - codec else current.audioCodecs + codec
            settings.setSourceExclusions(current.copy(audioCodecs = audioCodecs))
        }
    }

    fun setExperimentalSubtitleSync(enabled: Boolean) {
        viewModelScope.launch { settings.setExperimentalSubtitleSync(enabled) }
    }

    val matchContentFrameRate: StateFlow<Boolean> = settings.matchContentFrameRate
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setMatchContentFrameRate(enabled: Boolean) {
        viewModelScope.launch { settings.setMatchContentFrameRate(enabled) }
    }

    val subtitleAppearance: StateFlow<SubtitleAppearance> = settings.subtitleAppearance
        .stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleAppearance())

    fun setSubtitleAppearance(appearance: SubtitleAppearance) {
        viewModelScope.launch { settings.setSubtitleAppearance(appearance.normalized()) }
    }

    fun toggleProvider(p: SourceProvider) {
        viewModelScope.launch {
            val current = settings.sourceProviders.first()
            val group = SourceProvider.entries.filter { texts.get(it.sectionResource) == texts.get(p.sectionResource) }.toSet()
            val next = if (group.all { it in current }) current - group else current + group
            if (next.isNotEmpty()) settings.setSourceProviders(next)
        }
    }

    fun openEditor(service: ApiService) {
        editor.value = KeyEditor(
            service = service,
            hasKey = !apiKeys.keys.value[service].isNullOrBlank(),
            serverUrl = serverUrl.value,
        )
    }

    fun closeEditor() {
        if (editor.value?.busy != true) editor.value = null
    }

    fun saveKey(input: String, serverUrl: String? = null, adminPassword: String? = null) {
        val current = editor.value ?: return
        val key = input.trim()
        val server = serverUrl?.trim().orEmpty()

        // Modo servidor propio: la clave se envía al servidor (que la guarda y la usa).
        if (server.isNotEmpty()) {
            editor.value = current.copy(busy = true, error = null)
            viewModelScope.launch {
                settings.setServerUrl(server)
                if (key.isNotEmpty()) {
                    val ok = apiKeys.pushKeyToServer(current.service, key, adminPassword.orEmpty())
                    if (!ok) {
                        editor.value = current.copy(
                            busy = false,
                            error = "No se pudo enviar la clave al servidor. Revisa la URL y la contraseña admin.",
                        )
                        return@launch
                    }
                }
                // Marcador local: la clave real vive en el servidor (el servidor inyecta la suya).
                apiKeys.set(current.service, key.ifEmpty { "server-mode" })
                statuses.update { it + (current.service to ServiceStatus.Ok(null)) }
                editor.value = null
            }
            return
        }

        // Modo directo: la clave es obligatoria y se valida.
        if (key.isEmpty()) {
            editor.value = current.copy(error = texts.get(StringsR.string.settings_type_the_key_first))
            return
        }
        editor.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            settings.setServerUrl(null)
            when (val result = apiKeys.check(current.service, key)) {
                is KeyCheck.Valid -> {
                    apiKeys.set(current.service, key)
                    statuses.update { it + (current.service to ServiceStatus.Ok(result.detail)) }
                    editor.value = null
                }
                is KeyCheck.Invalid -> editor.value = current.copy(busy = false, error = result.message)
            }
        }
    }

    fun completeAllDebridConnect() {
        statuses.update { it + (ApiService.ALLDEBRID to ServiceStatus.Ok(texts.get(StringsR.string.settings_connected))) }
        editor.value = null
    }

    fun removeKey() {
        val current = editor.value ?: return
        if (current.service.required) return
        viewModelScope.launch {
            apiKeys.set(current.service, null)
            statuses.update { it - current.service }
            editor.value = null
        }
    }

    fun setDebrid(service: DebridService) {
        viewModelScope.launch { settings.setDebridService(service) }
    }

    fun setDeviceOptimization(optimization: DeviceOptimization) {
        viewModelScope.launch { settings.setDeviceOptimization(optimization) }
    }

    val episodeNotifications: StateFlow<Boolean> = episodeTracking.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setEpisodeNotifications(enabled: Boolean) = episodeTracking.setNotificationsEnabled(enabled)

    val autoNextEpisode: StateFlow<Boolean> = settings.autoNextEpisode
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAutoNextEpisode(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoNextEpisode(enabled) }
    }

    val switchFromPickedSource: StateFlow<Boolean> = settings.switchFromPickedSource
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setSwitchFromPickedSource(enabled: Boolean) {
        viewModelScope.launch { settings.setSwitchFromPickedSource(enabled) }
    }

    val autoSkipIntro: StateFlow<Boolean> = settings.autoSkipIntro
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setAutoSkipIntro(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoSkipIntro(enabled) }
    }

    val showCast: StateFlow<Boolean> = settings.showCast
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setShowCast(enabled: Boolean) {
        viewModelScope.launch { settings.setShowCast(enabled) }
    }

    /** Prioridad al reproducir, sobre las dos opciones del perfil (castellano primero / solo castellano). */
    val playbackPriority: StateFlow<com.shieldstream.tv.model.PlaybackPriority> = combine(
        settings.spanishFirst,
        settings.spanishOnlySources,
    ) { first, only ->
        when {
            only -> com.shieldstream.tv.model.PlaybackPriority.SPANISH_ONLY
            first -> com.shieldstream.tv.model.PlaybackPriority.SPANISH_FIRST
            else -> com.shieldstream.tv.model.PlaybackPriority.QUALITY_FIRST
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, com.shieldstream.tv.model.PlaybackPriority.SPANISH_FIRST)

    fun setPlaybackPriority(priority: com.shieldstream.tv.model.PlaybackPriority) {
        viewModelScope.launch {
            settings.setSpanishOnlySources(priority == com.shieldstream.tv.model.PlaybackPriority.SPANISH_ONLY)
            settings.setSpanishFirst(priority != com.shieldstream.tv.model.PlaybackPriority.QUALITY_FIRST)
        }
    }

    val preferredQuality: StateFlow<com.shieldstream.tv.model.PreferredQuality> = settings.preferredQuality
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.shieldstream.tv.model.PreferredQuality.DV_4K)

    fun setPreferredQuality(quality: com.shieldstream.tv.model.PreferredQuality) {
        viewModelScope.launch { settings.setPreferredQuality(quality) }
    }

    val tracksPanelCard: StateFlow<Boolean> = settings.tracksPanelCard
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setTracksPanelCard(enabled: Boolean) {
        viewModelScope.launch { settings.setTracksPanelCard(enabled) }
    }

    fun setSeekThumbnails(enabled: Boolean) {
        viewModelScope.launch {
            if (settings.deviceOptimization.first() == DeviceOptimization.AUTOMATIC) return@launch
            settings.setSeekThumbnails(enabled)
        }
    }

    fun changeBuffer(deltaSeconds: Int) {
        viewModelScope.launch {
            if (settings.deviceOptimization.first() == DeviceOptimization.AUTOMATIC) return@launch
            val now = settings.maxBufferSeconds.first()
            settings.setMaxBufferSeconds(now + deltaSeconds)
        }
    }

    private fun KeyCheck.toStatus(): ServiceStatus = when (this) {
        is KeyCheck.Valid -> ServiceStatus.Ok(detail)
        is KeyCheck.Invalid -> ServiceStatus.Bad(message)
    }

    // --- Exportar / importar configuración (QR y JSON) -----------------------------------------

    private val _export = MutableStateFlow<ExportUi?>(null)
    val exportSheet: StateFlow<ExportUi?> = _export.asStateFlow()

    private val _import = MutableStateFlow<ImportUi?>(null)
    val importSheet: StateFlow<ImportUi?> = _import.asStateFlow()

    /** Prepara el JSON de exportación (servidor, debrid y clave local si estamos en modo directo). */
    fun openExport() {
        viewModelScope.launch {
            val config = ConfigTransfer.snapshot(
                serverUrl = settings.serverUrl.first(),
                debridService = settings.debridService.first().name,
                token = settings.token.first(),
            )
            _export.value = ExportUi(ConfigTransfer.toJson(config))
        }
    }

    fun closeExport() {
        _export.value = null
    }

    fun openImport() {
        _import.value = ImportUi()
    }

    fun closeImport() {
        _import.value = null
    }

    /**
     * Restaura lo que traiga el JSON pegado, con los setters de siempre. Tolerante: si el texto no
     * es válido se avisa y no se toca nada.
     */
    fun applyImportedConfig(text: String) {
        val config = ConfigTransfer.parse(text)
        if (config == null) {
            _import.value = ImportUi(error = texts.get(StringsR.string.settings_import_invalid))
            return
        }
        viewModelScope.launch {
            val server = config.serverUrl
            if (server != null) {
                // Modo servidor: la clave vive en el servidor, así que no se guarda ninguna local.
                settings.setServerUrl(server)
                settings.setToken(null)
            } else if (config.token != null) {
                // Modo directo: la clave del debrid viene en el JSON.
                settings.setServerUrl(null)
                settings.setToken(config.token)
            }
            config.debridService
                ?.let { name -> DebridService.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
                ?.let { settings.setDebridService(it) }
            _import.value = ImportUi(applied = true, restartNeeded = config.serverUrl != null)
        }
    }

    private fun mask(key: String): String = if (key.length <= 4) "••••" else "••••••••  " + key.takeLast(4)
}

/** Configuración serializada y lista para enseñar como QR / texto. */
data class ExportUi(val json: String)

/** Estado del diálogo de importación. */
data class ImportUi(
    val error: String? = null,
    val applied: Boolean = false,
    /** Cambiar de servidor pide reiniciar: las APIs ya se construyeron con la URL anterior. */
    val restartNeeded: Boolean = false,
)

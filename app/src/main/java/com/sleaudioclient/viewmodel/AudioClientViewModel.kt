package com.sleaudioclient.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sleaudioclient.audio.PcmPlaybackEngine
import com.sleaudioclient.data.ConfigStore
import com.sleaudioclient.model.ClientConfig
import com.sleaudioclient.model.NetStats
import com.sleaudioclient.model.PcmFormat
import com.sleaudioclient.model.UiState
import com.sleaudioclient.network.UdpPcmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AudioClientViewModel(application: Application) : AndroidViewModel(application) {
    private val configStore = ConfigStore(application)
    private val udpClient = UdpPcmClient()
    private val playbackEngine = PcmPlaybackEngine()
    private val stateMutex = Mutex()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var sessionJob: Job? = null
    private var playbackJob: Job? = null
    @Volatile
    private var shouldRun = false
    private val queueLock = Any()
    private val packetQueue = ArrayDeque<ByteArray>()
    private var queueDrops = 0L
    private val maxQueuePackets = 512

    init {
        viewModelScope.launch {
            configStore.configFlow.collect { config ->
                _uiState.value = _uiState.value.copy(config = config)
            }
        }
    }

    fun start() {
        if (sessionJob?.isActive == true) return
        packetQueue.clear()
        queueDrops = 0L
        shouldRun = true
        val config = _uiState.value.config
        playbackEngine.start(config)
        _uiState.value = _uiState.value.copy(isRunning = true, status = "Running")

        playbackJob = viewModelScope.launch(Dispatchers.IO) {
            while (shouldRun) {
                val packet = synchronized(queueLock) {
                    if (packetQueue.isNotEmpty()) packetQueue.removeFirst() else null
                }
                if (packet != null) {
                    playbackEngine.write(packet, _uiState.value.config)
                    _uiState.value = _uiState.value.copy(
                        stats = _uiState.value.stats.copy(
                            queueDepth = synchronized(queueLock) { packetQueue.size },
                            queueDrops = queueDrops,
                            underruns = playbackEngine.underruns()
                        ),
                        lastPacketHex = packet.toHexPreview()
                    )
                } else {
                    delay(2)
                }
            }
        }

        sessionJob = viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                udpClient.runSession(
                    config = config,
                    shouldContinue = { shouldRun },
                    onPacket = { packet ->
                        var accepted = true
                        synchronized(queueLock) {
                            if (packetQueue.size >= maxQueuePackets) {
                                queueDrops += 1
                                accepted = false
                            } else {
                                packetQueue.addLast(packet)
                            }
                        }
                        _uiState.value = _uiState.value.copy(
                            stats = _uiState.value.stats.copy(
                                packets = _uiState.value.stats.packets + 1,
                                bytes = _uiState.value.stats.bytes + packet.size,
                                queueDepth = synchronized(queueLock) { packetQueue.size },
                                queueDrops = queueDrops
                            )
                        )
                        accepted
                    },
                    onRate = { rate ->
                        _uiState.value = _uiState.value.copy(
                            stats = _uiState.value.stats.copy(bytesPerSecond = rate, underruns = playbackEngine.underruns())
                        )
                    }
                )
            }
            stateMutex.withLock {
                if (result.isSuccess) {
                    val value = result.getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        status = "Stopped",
                        stats = _uiState.value.stats.copy(
                            packets = value.packets,
                            bytes = value.bytes,
                            queueDepth = synchronized(queueLock) { packetQueue.size },
                            queueDrops = queueDrops,
                            underruns = playbackEngine.underruns()
                        )
                    )
                } else {
                    _uiState.value = _uiState.value.copy(status = "Error: ${result.exceptionOrNull()?.message ?: "unknown"}")
                }
                _uiState.value = _uiState.value.copy(isRunning = false)
            }
            playbackEngine.stop()
        }
    }

    fun stop() {
        shouldRun = false
        sessionJob?.cancel()
        playbackJob?.cancel()
        sessionJob = null
        playbackJob = null
        playbackEngine.stop()
        _uiState.value = _uiState.value.copy(isRunning = false, status = "Stopped")
    }

    fun probe() {
        val config = _uiState.value.config
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { udpClient.sendProbe(config) }
                .onSuccess { _uiState.value = _uiState.value.copy(status = "Probe sent") }
                .onFailure { _uiState.value = _uiState.value.copy(status = "Probe failed: ${it.message}") }
        }
    }

    fun resetStats() {
        _uiState.value = _uiState.value.copy(stats = NetStats(), lastPacketHex = "")
    }

    fun updateConfig(update: (ClientConfig) -> ClientConfig) {
        val next = update(_uiState.value.config)
        _uiState.value = _uiState.value.copy(config = next)
        viewModelScope.launch { configStore.save(next) }
    }

    fun setServerIp(value: String) = updateConfig { it.copy(serverIp = value) }
    fun setServerPort(value: String) = updateConfig { it.copy(serverPort = value.toIntOrNull() ?: it.serverPort) }
    fun setBindIp(value: String) = updateConfig { it.copy(bindIp = value) }
    fun setBindPort(value: String) = updateConfig { it.copy(bindPort = value.toIntOrNull() ?: it.bindPort) }
    fun setSampleRate(value: String) = updateConfig { it.copy(sampleRate = value.toIntOrNull() ?: it.sampleRate) }
    fun setChannels(value: String) = updateConfig { it.copy(channels = value.toIntOrNull()?.coerceIn(1, 2) ?: it.channels) }
    fun setPrebufferMs(value: String) = updateConfig { it.copy(prebufferMs = value.toIntOrNull()?.coerceAtLeast(20) ?: it.prebufferMs) }
    fun setHeartbeatMs(value: String) =
        updateConfig { it.copy(heartbeatMs = value.toIntOrNull()?.coerceIn(200, 120_000) ?: it.heartbeatMs) }
    fun setVolume(value: String) = updateConfig { it.copy(volume = value.toFloatOrNull()?.coerceIn(0f, 2f) ?: it.volume) }
    fun setPlaybackEnabled(value: Boolean) = updateConfig { it.copy(playbackEnabled = value) }
    fun setPcmFormat(format: PcmFormat) = updateConfig { it.copy(pcmFormat = format) }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return AudioClientViewModel(application) as T
        }
    }
}

private fun ByteArray.toHexPreview(maxBytes: Int = 48): String {
    if (isEmpty()) return ""
    val size = maxBytes.coerceAtMost(this.size)
    return this.take(size).joinToString(" ") { "%02X".format(it) }
}

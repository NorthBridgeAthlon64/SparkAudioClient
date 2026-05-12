package com.sleaudioclient.model

data class ClientConfig(
    val serverIp: String = "192.168.130.1",
    val serverPort: Int = 9999,
    val bindIp: String = "0.0.0.0",
    val bindPort: Int = 0,
    val sampleRate: Int = 48000,
    val channels: Int = 2,
    val pcmFormat: PcmFormat = PcmFormat.INT32,
    val prebufferMs: Int = 80,
    /** Must stay below firmware PCM_UDP_PEER_IDLE_MS (often 3000); matches Python DEFAULT_HEARTBEAT_MS. */
    val heartbeatMs: Int = 1000,
    val volume: Float = 1.0f,
    val playbackEnabled: Boolean = true
)

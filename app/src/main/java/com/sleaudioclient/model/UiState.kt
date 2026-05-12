package com.sleaudioclient.model

data class UiState(
    val isRunning: Boolean = false,
    val status: String = "Idle",
    val config: ClientConfig = ClientConfig(),
    val stats: NetStats = NetStats(),
    val lastPacketHex: String = ""
)

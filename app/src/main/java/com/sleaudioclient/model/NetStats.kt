package com.sleaudioclient.model

data class NetStats(
    val packets: Long = 0,
    val bytes: Long = 0,
    val bytesPerSecond: Long = 0,
    val queueDepth: Int = 0,
    val queueDrops: Long = 0,
    val underruns: Long = 0
)

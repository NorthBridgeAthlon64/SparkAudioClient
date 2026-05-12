package com.sleaudioclient.network

import com.sleaudioclient.model.ClientConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * WiFi UDP PCM session: probe [0x01], periodic heartbeat (same payload), [PCM_BYE] on teardown.
 * Aligns with firmware [Radar/WiFiUDP.cpp] and Python [pcm_udp_player.UdpPcmReceiver].
 */
class UdpPcmClient {
    data class SessionResult(
        val packets: Long,
        val bytes: Long
    )

    fun sendProbe(config: ClientConfig) {
        DatagramSocket().use { socket ->
            val serverAddress = InetAddress.getByName(config.serverIp)
            val ping = DatagramPacket(HEARTBEAT_PAYLOAD, HEARTBEAT_PAYLOAD.size, serverAddress, config.serverPort)
            socket.send(ping)
        }
    }

    suspend fun runSession(
        config: ClientConfig,
        shouldContinue: () -> Boolean,
        onPacket: (ByteArray) -> Boolean,
        onRate: (Long) -> Unit
    ): SessionResult {
        var socket: DatagramSocket? = null
        var packets = 0L
        var bytes = 0L
        var lastBytes = 0L
        var lastTick = System.currentTimeMillis()
        val hbMs = clampHeartbeatMs(config.heartbeatMs)
        try {
            val bindAddress =
                if (config.bindIp == "0.0.0.0") InetSocketAddress(config.bindPort)
                else InetSocketAddress(config.bindIp, config.bindPort)
            socket = DatagramSocket(bindAddress).apply { soTimeout = RECV_TIMEOUT_MS }
            val serverAddress = InetAddress.getByName(config.serverIp)
            val ping = DatagramPacket(HEARTBEAT_PAYLOAD, HEARTBEAT_PAYLOAD.size, serverAddress, config.serverPort)
            socket.send(ping)
            var nextHeartbeatAt = System.currentTimeMillis() + hbMs
            val buffer = ByteArray(1500)
            while (shouldContinue()) {
                val nowLoop = System.currentTimeMillis()
                if (nowLoop >= nextHeartbeatAt) {
                    runCatching {
                        socket.send(DatagramPacket(HEARTBEAT_PAYLOAD, HEARTBEAT_PAYLOAD.size, serverAddress, config.serverPort))
                    }
                    nextHeartbeatAt = nowLoop + hbMs
                }
                val packet = DatagramPacket(buffer, buffer.size)
                runCatching { socket.receive(packet) }.onSuccess {
                    val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                    packets += 1
                    bytes += data.size
                    onPacket(data)
                }
                val now = System.currentTimeMillis()
                if (now - lastTick >= 1_000) {
                    val bps = bytes - lastBytes
                    onRate(bps)
                    lastBytes = bytes
                    lastTick = now
                }
            }
        } finally {
            socket?.let { s ->
                sendGoodbye(s, config)
                s.close()
            }
        }
        return SessionResult(packets = packets, bytes = bytes)
    }

    /**
     * Firmware treats ASCII `PCM_BYE` (optional trailing \\n/\\r) as unregister — not the short string `bye`.
     */
    private fun sendGoodbye(socket: DatagramSocket, config: ClientConfig) {
        try {
            val serverAddress = InetAddress.getByName(config.serverIp)
            val payload = PCM_BYE_BYTES
            val packet = DatagramPacket(payload, payload.size, serverAddress, config.serverPort)
            repeat(3) {
                socket.send(packet)
            }
        } catch (_: Exception) {
            // Match Python: swallow send errors on teardown
        }
    }

    companion object {
        private val HEARTBEAT_PAYLOAD = byteArrayOf(0x01)
        /** Same as WiFiUDP `k_pcm_udp_bye[] = "PCM_BYE"`. */
        private val PCM_BYE_BYTES = "PCM_BYE".toByteArray(StandardCharsets.US_ASCII)
        private const val RECV_TIMEOUT_MS = 250
        private fun clampHeartbeatMs(ms: Int): Int = ms.coerceIn(200, 120_000)
    }
}

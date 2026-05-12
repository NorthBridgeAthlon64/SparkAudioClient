package com.sleaudioclient.codec

import com.sleaudioclient.model.ClientConfig
import com.sleaudioclient.model.PcmFormat
import kotlin.math.roundToInt

object PcmDecoder {
    fun decodeToPcm16(input: ByteArray, config: ClientConfig): ShortArray {
        val base = when (config.pcmFormat) {
            PcmFormat.INT16 -> decodeInt16(input)
            PcmFormat.INT32 -> decodeInt32ToInt16(input)
        }
        val channelMapped = if (config.channels == 1) monoToStereo(base) else base
        return applyVolume(channelMapped, config.volume)
    }

    private fun decodeInt16(input: ByteArray): ShortArray {
        val count = input.size / 2
        val out = ShortArray(count)
        var i = 0
        var j = 0
        while (j + 1 < input.size) {
            val lo = input[j].toInt() and 0xFF
            val hi = input[j + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort()
            i++
            j += 2
        }
        return out
    }

    private fun decodeInt32ToInt16(input: ByteArray): ShortArray {
        val count = input.size / 4
        val out = ShortArray(count)
        var i = 0
        var j = 0
        while (j + 3 < input.size) {
            val b0 = input[j].toInt() and 0xFF
            val b1 = input[j + 1].toInt() and 0xFF
            val b2 = input[j + 2].toInt() and 0xFF
            val b3 = input[j + 3].toInt()
            val value = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
            out[i] = (value shr 16).toShort()
            i++
            j += 4
        }
        return out
    }

    private fun monoToStereo(input: ShortArray): ShortArray {
        val out = ShortArray(input.size * 2)
        var i = 0
        var j = 0
        while (i < input.size) {
            out[j] = input[i]
            out[j + 1] = input[i]
            i++
            j += 2
        }
        return out
    }

    private fun applyVolume(input: ShortArray, volume: Float): ShortArray {
        if (volume >= 0.999f) return input
        val out = ShortArray(input.size)
        for (i in input.indices) {
            val scaled = (input[i] * volume).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = scaled.toShort()
        }
        return out
    }
}

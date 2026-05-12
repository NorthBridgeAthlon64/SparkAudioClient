package com.sleaudioclient.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.sleaudioclient.codec.PcmDecoder
import com.sleaudioclient.model.ClientConfig
import java.util.concurrent.atomic.AtomicLong

class PcmPlaybackEngine {
    private var audioTrack: AudioTrack? = null
    private val underrunsAtomic = AtomicLong(0)

    fun start(config: ClientConfig) {
        stop()
        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val minBufferSize = AudioTrack.getMinBufferSize(
            config.sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bytesPerMs = config.sampleRate * 2 * 2 / 1000
        val preferredBuffer = (config.prebufferMs * bytesPerMs).coerceAtLeast(minBufferSize)
        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(config.sampleRate)
                .setChannelMask(channelConfig)
                .build(),
            preferredBuffer,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        ).apply { play() }
        underrunsAtomic.set(0)
    }

    fun write(packet: ByteArray, config: ClientConfig) {
        val track = audioTrack ?: return
        if (!config.playbackEnabled) return
        val pcm = PcmDecoder.decodeToPcm16(packet, config)
        val written = track.write(pcm, 0, pcm.size, AudioTrack.WRITE_NON_BLOCKING)
        if (written <= 0) {
            underrunsAtomic.incrementAndGet()
        }
    }

    fun underruns(): Long = underrunsAtomic.get()

    fun stop() {
        audioTrack?.runCatching {
            stop()
            flush()
            release()
        }
        audioTrack = null
    }
}

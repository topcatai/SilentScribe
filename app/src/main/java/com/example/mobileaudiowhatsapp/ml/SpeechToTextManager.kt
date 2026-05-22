package com.example.mobileaudiowhatsapp.ml

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException

class SpeechToTextManager(private val context: Context) {

    suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.Default) {
        val modelPath = VoskModelManager.customModelPath(context)
            ?: VoskModelManager.ensureModel(context).absolutePath
        val model = Model(modelPath)
        val recognizer = Recognizer(model, 16000.0f)
        val pcmFile = decodeToPcm(audioFile)

        try {
            val result = StringBuilder()
            pcmFile.inputStream().use { stream ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } >= 0) {
                    ensureActive()
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        val partial = JSONObject(recognizer.result).optString("text")
                        if (partial.isNotBlank()) result.append(partial).append(" ")
                    }
                }
                val final = JSONObject(recognizer.finalResult).optString("text")
                if (final.isNotBlank()) result.append(final)
            }
            result.toString().trim()
        } finally {
            recognizer.close()
            model.close()
            if (pcmFile.absolutePath != audioFile.absolutePath) {
                pcmFile.delete()
            }
        }
    }

    private suspend fun decodeToPcm(input: File): File = withContext(Dispatchers.IO) {
        if (input.extension.lowercase() == "wav") {
            // Verify with MediaMetadataRetriever before trusting it
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(input.absolutePath)
                val sr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toInt()
                if (sr == 16000) {
                    return@withContext input // Already correct — no temp file created, nothing to clean up
                }
            } catch (e: Exception) {
                // Ignore and proceed to decode if it fails to read sample rate
            } finally {
                mmr.release()
            }
        }

        val output = File(input.parentFile ?: context.cacheDir, "${input.nameWithoutExtension}_pcm.wav")

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(input.absolutePath)
        } catch (e: Exception) {
            extractor.release()
            throw IOException("Failed to set data source for audio file: ${input.absolutePath}", e)
        }

        // Find the first audio track
        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = trackFormat
                break
            }
        }
        
        if (audioTrackIndex < 0 || format == null) {
            extractor.release()
            throw IOException("No audio track found in ${input.name}")
        }

        extractor.selectTrack(audioTrackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 16000
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
        
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmBuffer = mutableListOf<ByteArray>()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                // Feed input
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // Drain output
                when (val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* format change — ignore for PCM */ }
                    MediaCodec.INFO_TRY_AGAIN_LATER       -> { /* wait */ }
                    else -> {
                        if (outIndex >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outIndex)!!
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.get(chunk)
                            pcmBuffer.add(chunk)
                            codec.releaseOutputBuffer(outIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            }

            codec.stop()
            
            // Resample to 16kHz mono if needed — Vosk requires exactly this
            val rawPcm = pcmBuffer.flatMap { it.toList() }.toByteArray()
            val targetPcm = if (sampleRate != 16000 || channelCount != 1) {
                resampleToMono16k(rawPcm, sampleRate, channelCount)
            } else {
                rawPcm
            }

            // Write WAV file with correct header
            writeWav(output, targetPcm, sampleRate = 16000, channels = 1)
        } finally {
            codec.release()
            extractor.release()
        }

        output
    }

    // Downsample PCM to 16kHz mono using linear interpolation
    private fun resampleToMono16k(
        input: ByteArray,
        sourceSampleRate: Int,
        sourceChannels: Int
    ): ByteArray {
        // Convert byte array to 16-bit shorts
        val shorts = ShortArray(input.size / 2) { i ->
            ((input[i * 2 + 1].toInt() shl 8) or (input[i * 2].toInt() and 0xFF)).toShort()
        }

        // Mix down to mono if stereo
        val mono = if (sourceChannels == 1) shorts else ShortArray(shorts.size / sourceChannels) { i ->
            var sum = 0L
            for (ch in 0 until sourceChannels) {
                val idx = i * sourceChannels + ch
                if (idx < shorts.size) {
                    sum += shorts[idx]
                }
            }
            (sum / sourceChannels).toShort()
        }

        // Resample to 16kHz
        val ratio = sourceSampleRate.toDouble() / 16000.0
        val outputSize = (mono.size / ratio).toInt()
        val resampled = ShortArray(outputSize) { i ->
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt().coerceIn(0, mono.size - 2)
            val frac = srcPos - srcIdx
            ((mono[srcIdx] * (1 - frac)) + (mono[srcIdx + 1] * frac)).toInt().toShort()
        }

        // Convert back to byte array (little-endian 16-bit PCM)
        return ByteArray(resampled.size * 2) { i ->
            if (i % 2 == 0) (resampled[i / 2].toInt() and 0xFF).toByte()
            else ((resampled[i / 2].toInt() shr 8) and 0xFF).toByte()
        }
    }

    // Minimal WAV header writer — Vosk needs valid WAV format
    private fun writeWav(file: File, pcm: ByteArray, sampleRate: Int, channels: Int) {
        val byteRate = sampleRate * channels * 2   // 16-bit = 2 bytes per sample
        val blockAlign = channels * 2
        val dataSize = pcm.size
        val totalSize = 36 + dataSize

        file.outputStream().use { out ->
            fun Int.le4() = byteArrayOf(
                (this and 0xFF).toByte(), ((this shr 8) and 0xFF).toByte(),
                ((this shr 16) and 0xFF).toByte(), ((this shr 24) and 0xFF).toByte()
            )
            fun Short.le2() = byteArrayOf((this.toInt() and 0xFF).toByte(), ((this.toInt() shr 8) and 0xFF).toByte())

            out.write("RIFF".toByteArray())
            out.write(totalSize.le4())
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(16.le4())                          // Chunk size
            out.write((1.toShort()).le2())               // PCM format
            out.write(channels.toShort().le2())
            out.write(sampleRate.le4())
            out.write(byteRate.le4())
            out.write(blockAlign.toShort().le2())
            out.write((16.toShort()).le2())              // Bits per sample
            out.write("data".toByteArray())
            out.write(dataSize.le4())
            out.write(pcm)
        }
    }
}

package com.example.mobileaudiowhatsapp.ml

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.WaveReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class SpeechToTextManager(private val context: Context) {

    data class WhisperModelFiles(
        val encoder: File,
        val decoder: File,
        val tokens: File
    )

    private fun findWhisperModelFiles(dir: File): WhisperModelFiles {
        if (!dir.exists() || !dir.isDirectory) {
            throw IOException("Model directory does not exist or is not a directory: ${dir.absolutePath}")
        }

        var encoderFile: File? = null
        var decoderFile: File? = null
        var tokensFile: File? = null

        fun walk(file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { walk(it) }
            } else {
                val name = file.name.lowercase()
                if (name.contains("encoder") && (name.endsWith(".onnx") || name.endsWith(".ort"))) {
                    encoderFile = file
                } else if (name.contains("decoder") && (name.endsWith(".onnx") || name.endsWith(".ort"))) {
                    decoderFile = file
                } else if (name.contains("tokens") && name.endsWith(".txt")) {
                    tokensFile = file
                }
            }
        }

        walk(dir)

        if (encoderFile == null) throw IOException("Could not find encoder.onnx in ${dir.absolutePath}")
        if (decoderFile == null) throw IOException("Could not find decoder.onnx in ${dir.absolutePath}")
        if (tokensFile == null) throw IOException("Could not find tokens.txt in ${dir.absolutePath}")

        return WhisperModelFiles(encoderFile!!, decoderFile!!, tokensFile!!)
    }

    suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.Default) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val customPathStr = prefs.getString("custom_model_path", "")
        if (customPathStr.isNullOrBlank()) {
            throw IOException("Whisper model path is not configured in settings.")
        }
        val modelDir = File(customPathStr)
        val modelFiles = findWhisperModelFiles(modelDir)

        val isTranslate = prefs.getBoolean("translate_mode", false)
        val taskType = if (isTranslate) "translate" else "transcribe"

        val whisperConfig = OfflineWhisperModelConfig(
            encoder = modelFiles.encoder.absolutePath,
            decoder = modelFiles.decoder.absolutePath,
            language = "", // auto-detect
            task = taskType
        )
        val modelConfig = OfflineModelConfig(
            whisper = whisperConfig,
            tokens = modelFiles.tokens.absolutePath,
            numThreads = 4,
            debug = false,
            provider = "cpu"
        )
        val recognizerConfig = OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        val recognizer = OfflineRecognizer(config = recognizerConfig)
        val pcmFile = decodeToPcm(audioFile)

        try {
            val waveData = WaveReader.readWave(pcmFile.absolutePath)
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(waveData.samples, 16000)
                recognizer.decode(stream)
                val result = recognizer.getResult(stream)
                result.text.trim()
            } finally {
                stream.release()
            }
        } finally {
            recognizer.release()
            if (pcmFile.absolutePath != audioFile.absolutePath) {
                pcmFile.delete()
            }
        }
    }

    private suspend fun decodeToPcm(input: File): File = withContext(Dispatchers.IO) {
        if (input.extension.lowercase() == "wav") {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(input.absolutePath)
                val sr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toInt()
                if (sr == 16000) {
                    return@withContext input
                }
            } catch (e: Exception) {
                // ignore
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

                when (val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                    MediaCodec.INFO_TRY_AGAIN_LATER       -> {}
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
            
            val rawPcm = pcmBuffer.flatMap { it.toList() }.toByteArray()
            val targetPcm = if (sampleRate != 16000 || channelCount != 1) {
                resampleToMono16k(rawPcm, sampleRate, channelCount)
            } else {
                rawPcm
            }

            writeWav(output, targetPcm, sampleRate = 16000, channels = 1)
        } finally {
            codec.release()
            extractor.release()
        }

        output
    }

    private fun resampleToMono16k(
        input: ByteArray,
        sourceSampleRate: Int,
        sourceChannels: Int
    ): ByteArray {
        val shorts = ShortArray(input.size / 2) { i ->
            ((input[i * 2 + 1].toInt() shl 8) or (input[i * 2].toInt() and 0xFF)).toShort()
        }

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

        val ratio = sourceSampleRate.toDouble() / 16000.0
        val outputSize = (mono.size / ratio).toInt()
        val resampled = ShortArray(outputSize) { i ->
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt().coerceIn(0, mono.size - 2)
            val frac = srcPos - srcIdx
            ((mono[srcIdx] * (1 - frac)) + (mono[srcIdx + 1] * frac)).toInt().toShort()
        }

        return ByteArray(resampled.size * 2) { i ->
            if (i % 2 == 0) (resampled[i / 2].toInt() and 0xFF).toByte()
            else ((resampled[i / 2].toInt() shr 8) and 0xFF).toByte()
        }
    }

    private fun writeWav(file: File, pcm: ByteArray, sampleRate: Int, channels: Int) {
        val byteRate = sampleRate * channels * 2
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
            out.write(16.le4())
            out.write((1.toShort()).le2())
            out.write(channels.toShort().le2())
            out.write(sampleRate.le4())
            out.write(byteRate.le4())
            out.write(blockAlign.toShort().le2())
            out.write((16.toShort()).le2())
            out.write("data".toByteArray())
            out.write(dataSize.le4())
            out.write(pcm)
        }
    }
}

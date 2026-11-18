package com.example.util

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.data.model.AppLanguage
import java.io.File
import java.util.Locale
import java.util.UUID

class VoiceServices(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var activeRecordingPath: String? = null
    private var recordingStartMillis: Long = 0L

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
        }
    }

    fun speakNotification(text: String, language: AppLanguage, enabled: Boolean) {
        if (!enabled || !ttsReady || text.isBlank()) return
        try {
            val locale = if (language == AppLanguage.BN) Locale("bn", "BD") else Locale.US
            tts?.language = locale
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "munaf_tts_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.w("VoiceServices", "TTS failed: ${e.message}")
        }
    }

    fun startSpeechRecognition(
        language: AppLanguage,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition unavailable on this device. Please type your command.")
            return
        }
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        onError("Voice capture error ($error). Try speaking clearly or typing below.")
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull().orEmpty()
                        if (text.isNotBlank()) onResult(text)
                        else onError("No speech detected.")
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == AppLanguage.BN) "bn-BD" else "en-US")
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Speech recognizer error")
        }
    }

    fun startRecordingVoiceNote(): Result<String> =
        try {
            val dir = File(context.filesDir, "voice_notes").apply { mkdirs() }
            val file = File(dir, "vn_${UUID.randomUUID().toString().take(12)}.m4a")
            activeRecordingPath = file.absolutePath
            recordingStartMillis = System.currentTimeMillis()
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun stopRecordingVoiceNote(): Pair<String, Int>? {
        val path = activeRecordingPath ?: return null
        val durationSec = ((System.currentTimeMillis() - recordingStartMillis) / 1000L).toInt().coerceAtLeast(1)
        return try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            activeRecordingPath = null
            path to durationSec
        } catch (_: Exception) {
            mediaRecorder?.release()
            mediaRecorder = null
            activeRecordingPath = null
            null
        }
    }

    fun playVoiceNote(filePath: String, onComplete: () -> Unit = {}) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener { onComplete() }
                start()
            }
        } catch (e: Exception) {
            Log.e("VoiceServices", "Playback failed", e)
            onComplete()
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    fun release() {
        stopPlayback()
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}

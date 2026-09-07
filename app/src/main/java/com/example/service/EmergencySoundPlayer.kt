package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class AlertVibrationMode {
    CRITICAL_SEISMIC, // High-intensity double staccato + earthquake crescendo rumble (Max Amplitude 255)
    PANIC_SOS,        // SOS Morsing pattern (... --- ...) with maximum amplitude impact
    DRILL_SIMULATION  // Distinct double buzz for drills
}

class EmergencySoundPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    init {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Forces Alarm & Music Streams to 100% volume, overrides silent/vibrate ringer mode, and plays siren sound + customized aggressive haptic vibrations.
     */
    fun startSirenAlert(
        forceMaxVolume: Boolean = true,
        mode: AlertVibrationMode = AlertVibrationMode.CRITICAL_SEISMIC,
        toneIndex: Int = 0,
        customUriString: String? = null
    ) {
        stopSirenAlert()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (forceMaxVolume) {
            try {
                // Override Silent / Vibrate mode if permitted by OS
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, 0)
                val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val parsedCustomUri = if (!customUriString.isNullOrBlank()) {
                try { Uri.parse(customUriString) } catch (e: Exception) { null }
            } else null

            val fallbackUri = when (toneIndex) {
                1 -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                2 -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            val alertUri: Uri = parsedCustomUri ?: fallbackUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                setVolume(1.0f, 1.0f)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        vibrateCustomPattern(mode, repeat = true)
    }

    /**
     * Plays customized aggressive vibration effects using VibrationEffect with amplitude control.
     */
    fun vibrateCustomPattern(mode: AlertVibrationMode, repeat: Boolean = true) {
        val repeatIndex = if (repeat) 0 else -1

        val (timings, amplitudes) = when (mode) {
            AlertVibrationMode.CRITICAL_SEISMIC -> {
                // Aggressive double staccato burst (255 max power) + escalating earthquake wave
                val t = longArrayOf(0, 180, 80, 180, 80, 400, 100, 250, 100, 700, 200)
                val a = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0)
                Pair(t, a)
            }
            AlertVibrationMode.PANIC_SOS -> {
                // SOS Morse code pattern: ... --- ... at maximum motor amplitude (255)
                val t = longArrayOf(
                    0, 120, 80, 120, 80, 120, 250, // S (short x3)
                    350, 100, 350, 100, 350, 250,   // O (long x3)
                    120, 80, 120, 80, 120, 500      // S (short x3)
                )
                val a = intArrayOf(
                    0, 255, 0, 255, 0, 255, 0,
                    255, 0, 255, 0, 255, 0,
                    255, 0, 255, 0, 255, 0
                )
                Pair(t, a)
            }
            AlertVibrationMode.DRILL_SIMULATION -> {
                // Distinct double-buzz pattern for simulated drills
                val t = longArrayOf(0, 220, 150, 220, 400)
                val a = intArrayOf(0, 200, 0, 200, 0)
                Pair(t, a)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibratorObj = vibrator
            if (vibratorObj != null && vibratorObj.hasAmplitudeControl()) {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, repeatIndex)
                vibratorObj.vibrate(effect)
            } else {
                val effect = VibrationEffect.createWaveform(timings, repeatIndex)
                vibratorObj?.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(timings, repeatIndex)
        }
    }

    /**
     * Triggers a non-looping single vibration test to allow user to preview haptic feedback.
     */
    fun triggerSingleHapticTest(mode: AlertVibrationMode) {
        vibrator?.cancel()
        vibrateCustomPattern(mode, repeat = false)
    }

    fun stopSirenAlert() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}


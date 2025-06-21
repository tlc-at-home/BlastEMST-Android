// SoundPlayer.kt
package com.example.blastemst

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer // Using MediaPlayer for flexibility with URIs
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class SoundPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var currentSoundUri: Uri? = null
    private var isSoundLoaded = false

    // Default built-in bell sound ID (if you still want one as a fallback)
    private var defaultBellSoundId: Int = 0 // Using SoundPool for the default
    private var soundPool: SoundPool? = null
    private var isDefaultSoundPoolLoaded = false


    init {
        // Initialize Vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Initialize SoundPool for the default sound
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == defaultBellSoundId) {
                isDefaultSoundPoolLoaded = true
                Log.d("SoundPlayer", "Default bell sound loaded successfully.")
            } else {
                Log.e("SoundPlayer", "Failed to load default sound ID: $sampleId, status: $status")
            }
        }
        // Load the default sound (ensure R.raw.bell_sound exists)
        try {
            defaultBellSoundId = soundPool?.load(context, R.raw.bell_sound, 1) ?: 0
            if (defaultBellSoundId == 0) {
                Log.e("SoundPlayer", "Failed to initiate loading of default bell sound.")
            }
        } catch (e: Exception) {
            Log.e("SoundPlayer", "Default sound resource not found (R.raw.bell_sound). No default sound will be available.", e)
            defaultBellSoundId = 0 // Ensure it's 0 if resource is missing
        }

    }

    private fun prepareMediaPlayer(uri: Uri) {
        releaseMediaPlayer() // Release any existing instance
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC) // Or SONIFICATION if short
                    .build()
            )
            try {
                // Important: Persist URI permission if needed, especially for URIs from ACTION_OPEN_DOCUMENT
                // For URIs from ACTION_OPEN_DOCUMENT, you might need to take persistable URI permission.
                // context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // This step is crucial if the app might lose access to the URI after the picker closes.
                // However, for immediate playback, direct usage might work.
                // Test thoroughly!

                setDataSource(context, uri)
                setOnPreparedListener {
                    isSoundLoaded = true
                    Log.d("SoundPlayer", "MediaPlayer prepared for URI: $uri")
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("SoundPlayer", "MediaPlayer Error: what $what, extra $extra for URI $uri")
                    isSoundLoaded = false
                    false // True if the error has been handled
                }
                prepareAsync() // Prepare asynchronously
                currentSoundUri = uri
            } catch (e: Exception) {
                Log.e("SoundPlayer", "Failed to set data source for MediaPlayer: $uri", e)
                releaseMediaPlayer()
            }
        }
    }

    fun loadSoundFromUri(uri: Uri?) {
        if (uri == null) {
            currentSoundUri = null
            isSoundLoaded = false
            releaseMediaPlayer()
            Log.d("SoundPlayer", "No custom sound URI provided. MediaPlayer resources released.")
            return
        }
        if (uri == currentSoundUri && isSoundLoaded) {
            Log.d("SoundPlayer", "Sound already loaded for URI: $uri")
            return
        }
        prepareMediaPlayer(uri)
    }

    fun loadDefaultSound() {
        currentSoundUri = null // Clear custom URI
        isSoundLoaded = false // MediaPlayer is not for default sound
        releaseMediaPlayer()
        // Default sound relies on SoundPool, which is loaded at init
        Log.d("SoundPlayer", "Switched to default sound (uses SoundPool if available).")
    }


    fun playSoundAndHaptic(isHapticEnabled: Boolean, customSoundUriString: String?) {
        // Play Sound
        if (!customSoundUriString.isNullOrEmpty()) {
            val soundUri = Uri.parse(customSoundUriString)
            if (soundUri != currentSoundUri || mediaPlayer == null || !isSoundLoaded) {
                // If URI changed, or MP not ready, try to load/prepare it
                // This might introduce a slight delay on the first play if not preloaded.
                Log.w("SoundPlayer", "Custom sound URI changed or MediaPlayer not ready. Attempting to load.")
                prepareMediaPlayer(soundUri) // Attempt to prepare it now
                // Note: prepareAsync might not complete by the time play is called immediately after.
                // For robust playback, ensure loaded state or handle delay.
                // A simple approach is to try playing, and if it fails because it's not prepared,
                // it won't play. Subsequent calls after preparation would work.
            }

            if (mediaPlayer != null && isSoundLoaded) {
                try {
                    if (mediaPlayer?.isPlaying == true) {
                        mediaPlayer?.stop()
                        mediaPlayer?.prepare() // Re-prepare if stopped
                    }
                    mediaPlayer?.start()
                } catch (e: IllegalStateException) {
                    Log.e("SoundPlayer", "MediaPlayer not in a valid state to play.", e)
                    // Attempt to re-prepare if in a bad state
                    prepareMediaPlayer(soundUri)
                } catch (e: Exception) {
                    Log.e("SoundPlayer", "Error playing custom sound.", e)
                }
            } else {
                Log.w("SoundPlayer", "MediaPlayer not ready for custom sound. Falling back to default if configured.")
                playDefaultSoundViaSoundPool()
            }
        } else {
            // No custom sound URI, play default sound via SoundPool
            playDefaultSoundViaSoundPool()
        }


        // Trigger Haptic Feedback
        if (isHapticEnabled && vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Create a pre-defined click effect or a custom one
                val vibrationEffect = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                // For more defined haptics:
                // val vibrationEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                vibrator?.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50) // Vibrate for 50 milliseconds
            }
        }
    }

    private fun playDefaultSoundViaSoundPool() {
        if (soundPool != null && isDefaultSoundPoolLoaded && defaultBellSoundId != 0) {
            soundPool?.play(defaultBellSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            Log.w("SoundPlayer", "Default sound (SoundPool) not ready or not found.")
        }
    }


    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
        isSoundLoaded = false
    }

    fun release() {
        releaseMediaPlayer()
        soundPool?.release()
        soundPool = null
        isDefaultSoundPoolLoaded = false
        Log.d("SoundPlayer", "All sound resources released.")
    }
}
package com.example.blastemst

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
data class HomeScreenState(
    val sessions: List<Session> = emptyList(),
    val appVersion: String = "",
    val isLoading: Boolean = true
)

data class AppSettings(
    val defaultReps: Int = 25,
    val weeklySessionGoal: Int = 5,
    val defaultPressure: Int = 30,
    val appTheme: String = "system",
    val remindersEnabled: Boolean = false,
    val repSoundUri: String? = null, // Stores URI of the chosen sound as a String
    val isHapticFeedbackEnabled: Boolean = true // Setting for haptics
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeScreenState())
    val uiState = _uiState.asStateFlow()

    private val _activeSession = MutableStateFlow<Session?>(null)
    val activeSession = _activeSession.asStateFlow()

    private val _repCount = MutableStateFlow(0L)
    val repCount = _repCount.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings = _settings.asStateFlow()

    private val soundPlayer = SoundPlayer(application.applicationContext)
    private val reminderManager = ReminderManager(application.applicationContext)

    private val _sessionsByDate = MutableStateFlow<Map<LocalDate, List<Session>>>(emptyMap())
    val sessionsByDate = _sessionsByDate.asStateFlow()

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth = _currentMonth.asStateFlow()


    init {
        viewModelScope.launch(Dispatchers.IO) {
            val dbPath = getApplication<Application>().getDatabasePath("blast_emst.db").absolutePath
            RustBridge.initDatabase(dbPath)

            loadInitialData()
            loadProfile()
            loadActiveSession()
            loadSettings()
            // Consider adding a log here too to see if settings are loaded before startNewSession is called
            Log.d("MainViewModel", "Initial settings loaded: ${_settings.value}")
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val version = getAppVersion()
            val sessionsJson = RustBridge.getAllSessions()
            val sessionList: List<Session> = Json.decodeFromString(sessionsJson)

            // --- Logic to group sessions by date ---
            _sessionsByDate.value = sessionList.groupBy { session ->
                // The end_time from Rust is an RFC3339 string like "2025-06-22T11:59:43.123Z"
                // We parse it and convert it to a simple LocalDate (year-month-day)
                session.end_time?.let {
                    LocalDate.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                }
            }.filterKeys { it != null }.mapKeys { it.key!! } // Clean up the map
            // --- End of grouping logic ---

            _uiState.update { currentState ->
                currentState.copy(
                    sessions = sessionList,
                    appVersion = version,
                    isLoading = false
                )
            }
        }
    }

    private fun getAppVersion(): String {
        return try {
            val context = getApplication<Application>().applicationContext
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "Version ${packageInfo.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            "Version N/A"
        }
    }

    private fun loadProfile() {
        val profileJson = RustBridge.getProfile()
        if (profileJson.isNotEmpty()) {
            _userProfile.value = Json.decodeFromString(profileJson)
        }
    }

    private fun loadRepCount(sessionId: Long) {
        _repCount.value = RustBridge.getTotalReps(sessionId)
    }

    private fun loadActiveSession() {
        Log.d("MainViewModel", "loadActiveSession called") // LOG 3
        val activeSessionJson = RustBridge.getActiveSession()
        Log.d("MainViewModel", "loadActiveSession - JSON from RustBridge: $activeSessionJson") // LOG 4
        if (activeSessionJson.isNullOrEmpty()) {
            _activeSession.value = null
            Log.d("MainViewModel", "loadActiveSession - JSON is null or empty, _activeSession set to null") // LOG 5a
        } else {
            try {
                val session: Session = Json.decodeFromString(activeSessionJson)
                _activeSession.value = session
                Log.d("MainViewModel", "loadActiveSession - Parsed session: $session, _activeSession updated") // LOG 5b
                loadRepCount(session.id)
            } catch (e: Exception) {
                Log.e("MainViewModel", "loadActiveSession - Failed to parse session JSON: $activeSessionJson", e) // LOG Error
                _activeSession.value = null // Ensure it's null if parsing fails
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val defaultRepsStr = RustBridge.getSetting("default_reps", "25")
            val weeklyGoalStr = RustBridge.getSetting("goal_sessions_per_week", "5")
            val defaultPressureStr = RustBridge.getSetting("default_pressure", "30")
            val themeStr = RustBridge.getSetting("theme", "system")
            val remindersStr = RustBridge.getSetting("reminders_enabled", "false")
            val soundUriStr = RustBridge.getSetting("rep_sound_uri", "") // Default to empty string
            val hapticsStr = RustBridge.getSetting("haptic_feedback_enabled", "true")

            _settings.update {
                it.copy(
                    defaultReps = defaultRepsStr.toIntOrNull() ?: 25,
                    weeklySessionGoal = weeklyGoalStr.toIntOrNull() ?: 5,
                    defaultPressure = defaultPressureStr.toIntOrNull() ?: 30,
                    appTheme = themeStr,
                    remindersEnabled = remindersStr.toBoolean(),
                    repSoundUri = if (soundUriStr.isNotEmpty()) soundUriStr else null,
                    isHapticFeedbackEnabled = hapticsStr.toBoolean()
                )
            }
            Log.i("ViewModel", "Settings loaded: ${_settings.value}")
            // Re-initialize SoundPlayer if sound URI changes
            _settings.value.repSoundUri?.let {
                uriString -> soundPlayer.loadSoundFromUri(Uri.parse(uriString))
            } ?: run {
                soundPlayer.loadDefaultSound() // Or unload if no default desired
            }
        }
    }
    fun saveSettings(newSettings: AppSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            RustBridge.setSetting("default_reps", newSettings.defaultReps.toString())
            RustBridge.setSetting("goal_sessions_per_week", newSettings.weeklySessionGoal.toString())
            RustBridge.setSetting("default_pressure", newSettings.defaultPressure.toString())
            RustBridge.setSetting("theme", newSettings.appTheme)
            RustBridge.setSetting("reminders_enabled", newSettings.remindersEnabled.toString())
            RustBridge.setSetting("rep_sound_uri", newSettings.repSoundUri ?: "")
            RustBridge.setSetting("haptic_feedback_enabled", newSettings.isHapticFeedbackEnabled.toString())

            // This logic now only handles turning reminders off completely.
            // Turning them on doesn't need to do anything immediately; the next
            // finished session will schedule the check.


            if (newSettings.remindersEnabled) {
                // This is the new smart logic
                val lastSessionTimeStr = RustBridge.getLastSessionEndTime()

                if (lastSessionTimeStr.isEmpty()) {
                    // CASE 1: NEW USER (or no completed sessions)
                    // Schedule the first reminder for 24 hours from now.
                    reminderManager.scheduleInactivityCheck(delayInMinutes = 1440)
                    Log.d("ViewModel", "New user enabled reminders. Scheduling 24-hour check.")
                } else {
                    // CASE 2: ACTIVE USER
                    // Calculate the time remaining until 24 hours have passed since the last session.
                    val lastSessionMillis = Instant.parse(lastSessionTimeStr).toEpochMilli()
                    val twentyFourHoursInMillis = TimeUnit.HOURS.toMillis(24)
                    val triggerAtMillis = lastSessionMillis + twentyFourHoursInMillis
                    val nowMillis = System.currentTimeMillis()

                    val delayInMillis = triggerAtMillis - nowMillis

                    if (delayInMillis > 0) {
                        // The 24-hour mark is in the future. Schedule the check for the remaining time.
                        val delayInMinutes = TimeUnit.MILLISECONDS.toMinutes(delayInMillis)
                        reminderManager.scheduleInactivityCheck(delayInMinutes)
                        Log.d("ViewModel", "Active user enabled reminders. Scheduling check for remaining time: $delayInMinutes minutes.")
                    } else {
                        // More than 24 hours have already passed. Schedule a check soon to remind them.
                        // We'll schedule it for 10 minutes from now as a gentle nudge.
                        reminderManager.scheduleInactivityCheck(delayInMinutes = 10)
                        Log.d("ViewModel", "Active user enabled reminders, but >24hrs passed. Scheduling gentle nudge in 10 minutes.")
                    }
                }
            } else {
                // User disabled reminders, cancel any pending check.
                reminderManager.cancelInactiveCheck()
            }
            loadSettings()
        }
    }

    fun updateRepSoundUri(uri: Uri?) {
        val currentSettings = _settings.value
        saveSettings(currentSettings.copy(repSoundUri = uri?.toString()))
    }

    fun updateHapticFeedback(isEnabled: Boolean) {
        val currentSettings = _settings.value
        saveSettings(currentSettings.copy(isHapticFeedbackEnabled = isEnabled))
    }

    fun startNewSession() {
        // When a new session starts, cancel any pending reminder checks.
        // This prevents the user from getting a reminder while actively using the app.
        if (_settings.value.remindersEnabled) {
            reminderManager.cancelInactiveCheck()
        }
        Log.d("MainViewModel", "startNewSession called") // LOG 1
        viewModelScope.launch(Dispatchers.IO) {
            val pressure = _settings.value.defaultPressure // Ensure _settings.value is up-to-date
            Log.d("MainViewModel", "startNewSession - Using pressure: $pressure from settings: ${_settings.value}")
            val newSessionId = RustBridge.startSession(pressure, "")
            Log.d("MainViewModel", "startNewSession - newSessionId from RustBridge: $newSessionId") // LOG 2

            if (newSessionId != -1L) {
                Log.d("MainViewModel", "startNewSession - newSessionId is valid, calling loadActiveSession()")
                loadActiveSession() // This function will update _activeSession.value
            } else {
                Log.w("MainViewModel", "startNewSession - Failed to start session via RustBridge (newSessionId is -1L)")
                // Consider what should happen to _activeSession here.
                // If a previous session was active, should it remain active? Or be cleared?
                // For now, it will just not call loadActiveSession, so _activeSession won't change from this path.
            }
            // Add a log here to see the state of _activeSession AFTER the conditional loadActiveSession
            Log.d("MainViewModel", "startNewSession - _activeSession.value after attempting to start/load: ${_activeSession.value}") // LOG 6
        }
    }

    fun addRep() {
        _activeSession.value?.let { currentSession ->
            viewModelScope.launch(Dispatchers.IO) {
                RustBridge.addRep(currentSession.id)
                loadRepCount(currentSession.id)
            }
            // Play sound and haptic based on current settings
            val settings = _settings.value
            soundPlayer.playSoundAndHaptic(
                isHapticEnabled = settings.isHapticFeedbackEnabled,
                customSoundUriString = settings.repSoundUri
            )
        }
    }

    fun finishActiveSession(notes: String) {
        _activeSession.value?.let { currentSession ->
            viewModelScope.launch(Dispatchers.IO) {
                RustBridge.endSession(currentSession.id, notes)
                _activeSession.value = null
                // After a session is successfully completed, schedule the next 24-hour inactivity check.
                if (_settings.value.remindersEnabled) {
                    // 24 hours * 60 minutes/hour = 1440 minutes
                    reminderManager.scheduleInactivityCheck(delayInMinutes = 1440)
                }
                loadInitialData()
            }
        }
    }

    fun saveProfile(profileToSave: UserProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            val profileJson = Json.encodeToString(profileToSave)
            RustBridge.updateProfile(profileJson)
            loadProfile()
        }
    }

    // Functions to handle month navigation ---
    fun onNextMonth() {
        _currentMonth.update { it.plusMonths(1) }
    }

    fun onPreviousMonth() {
        _currentMonth.update { it.minusMonths(1) }
    }
}
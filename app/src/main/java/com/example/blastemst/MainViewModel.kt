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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri

data class HomeScreenState(
    val sessions: List<Session> = emptyList(),
    val appVersion: String = "",
    val isLoading: Boolean = true,
    val lastSessionReps: Long = 0,
    val sessionsThisWeek: Int = 0,
    val weeklySessionGoal: Int = 5
)

data class AppSettings(
    val defaultReps: Int = 25,
    val weeklySessionGoal: Int = 5,
    val defaultPressure: Int = 30,
    val appTheme: String = "system",
    val remindersEnabled: Boolean = false,
    val repSoundUri: String? = null,
    val isHapticFeedbackEnabled: Boolean = true
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

    private val _sessionsByDate = MutableStateFlow<Map<LocalDate, List<Session>>>(emptyMap())
    val sessionsByDate = _sessionsByDate.asStateFlow()

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth = _currentMonth.asStateFlow()

    private val _activeSessionNotes = MutableStateFlow("")
    val activeSessionNotes = _activeSessionNotes.asStateFlow()

    private val soundPlayer = SoundPlayer(application.applicationContext)
    private val reminderManager = ReminderManager(application.applicationContext)


    init {
        viewModelScope.launch(Dispatchers.IO) {
            val dbPath = getApplication<Application>().getDatabasePath("blast_emst.db").absolutePath
            RustBridge.initDatabase(dbPath)

            loadSettings()
            loadInitialData()
            loadProfile()
            loadActiveSession()
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val version = getAppVersion()
            val sessionsJson = RustBridge.getAllSessions()
            val sessionList: List<Session> = Json.decodeFromString(sessionsJson)

            _sessionsByDate.value = sessionList.groupBy { session ->
                session.end_time?.let {
                    LocalDate.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                }
            }.filterKeys { it != null }.mapKeys { it.key!! }

            val lastSessionReps = (sessionList.firstOrNull { it.end_time != null }?.rep_count ?: 0).toLong()
            val sessionsThisWeek = RustBridge.getSessionCountForWeek()
            val weeklyGoal = _settings.value.weeklySessionGoal

            _uiState.update { currentState ->
                currentState.copy(
                    sessions = sessionList,
                    appVersion = version,
                    isLoading = false,
                    lastSessionReps = lastSessionReps,
                    sessionsThisWeek = sessionsThisWeek,
                    weeklySessionGoal = weeklyGoal
                )
            }
        }
    }

    private fun getAppVersion(): String {
        return try {
            val context = getApplication<Application>().applicationContext
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "Version ${packageInfo.versionName}"
        } catch (_: PackageManager.NameNotFoundException) {
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
        val activeSessionJson = RustBridge.getActiveSession()
        if (activeSessionJson.isNullOrEmpty()) {
            _activeSession.value = null
        } else {
            try {
                val session: Session = Json.decodeFromString(activeSessionJson)
                _activeSession.value = session
                _activeSessionNotes.value = session.notes
                loadRepCount(session.id)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to parse active session JSON", e)
                _activeSession.value = null
            }
        }
    }

    private fun loadSettings() {
        val defaultRepsStr = RustBridge.getSetting("default_reps", "25")
        val weeklyGoalStr = RustBridge.getSetting("goal_sessions_per_week", "5")
        val defaultPressureStr = RustBridge.getSetting("default_pressure", "30")
        val themeStr = RustBridge.getSetting("theme", "system")
        val remindersStr = RustBridge.getSetting("reminders_enabled", "false")
        val soundUriStr = RustBridge.getSetting("rep_sound_uri", "")
        val hapticsStr = RustBridge.getSetting("haptic_feedback_enabled", "true")

        _settings.update {
            it.copy(
                defaultReps = defaultRepsStr.toIntOrNull() ?: 25,
                weeklySessionGoal = weeklyGoalStr.toIntOrNull() ?: 5,
                defaultPressure = defaultPressureStr.toIntOrNull() ?: 30,
                appTheme = themeStr,
                remindersEnabled = remindersStr.toBoolean(),
                repSoundUri = soundUriStr.ifEmpty { null },
                isHapticFeedbackEnabled = hapticsStr.toBoolean()
            )
        }
        _settings.value.repSoundUri?.let {
                uriString -> soundPlayer.loadSoundFromUri(uriString.toUri())
        } ?: run {
            soundPlayer.loadDefaultSound()
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

            if (newSettings.remindersEnabled) {
                val lastSessionTimeStr = RustBridge.getLastSessionEndTime()
                if (lastSessionTimeStr.isEmpty()) {
                    reminderManager.scheduleInactivityCheck(delayInMinutes = 1440)
                } else {
                    val lastSessionMillis = Instant.parse(lastSessionTimeStr).toEpochMilli()
                    val twentyFourHoursInMillis = TimeUnit.HOURS.toMillis(24)
                    val triggerAtMillis = lastSessionMillis + twentyFourHoursInMillis
                    val nowMillis = System.currentTimeMillis()
                    val delayInMillis = triggerAtMillis - nowMillis
                    if (delayInMillis > 0) {
                        reminderManager.scheduleInactivityCheck(TimeUnit.MILLISECONDS.toMinutes(delayInMillis))
                    } else {
                        reminderManager.scheduleInactivityCheck(delayInMinutes = 10)
                    }
                }
            } else {
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
        if (_settings.value.remindersEnabled) {
            reminderManager.cancelInactiveCheck()
        }
        viewModelScope.launch(Dispatchers.IO) {
            _activeSessionNotes.value = ""
            val pressure = _settings.value.defaultPressure
            val newSessionId = RustBridge.startSession(pressure, "")
            if (newSessionId != -1L) {
                loadActiveSession()
            }
        }
    }

    fun addRep() {
        _activeSession.value?.let { currentSession ->
            viewModelScope.launch(Dispatchers.IO) {
                RustBridge.addRep(currentSession.id)
                loadRepCount(currentSession.id)
            }
            soundPlayer.playSoundAndHaptic(
                isHapticEnabled = settings.value.isHapticFeedbackEnabled,
                customSoundUriString = settings.value.repSoundUri
            )
        }
    }

    fun finishActiveSession() {
        _activeSession.value?.let { currentSession ->
            viewModelScope.launch(Dispatchers.IO) {
                RustBridge.endSession(currentSession.id, _activeSessionNotes.value)
                _activeSession.value = null
                _activeSessionNotes.value = ""
                if (_settings.value.remindersEnabled) {
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

    fun onNextMonth() {
        _currentMonth.update { it.plusMonths(1) }
    }

    fun onPreviousMonth() {
        _currentMonth.update { it.minusMonths(1) }
    }

    fun onMonthScrolled(newMonth: YearMonth) {
        _currentMonth.value = newMonth
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            RustBridge.deleteSession(sessionId)
            loadInitialData()
        }
    }

    fun onActiveSessionNotesChanged(newNotes: String) {
        _activeSessionNotes.value = newNotes
    }
}
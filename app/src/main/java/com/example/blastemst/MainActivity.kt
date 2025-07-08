package com.example.blastemst

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.format.TextStyle as JavaTextStyle

// Sealed class to define all our navigation destinations
sealed class AppScreen {
    data object Home : AppScreen()
    data object History : AppScreen()
    data object Profile : AppScreen()
    data object Settings : AppScreen()
    data class ActiveSession(val session: Session) : AppScreen()
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Observe all necessary state from the ViewModel
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
            val repCount by viewModel.repCount.collectAsStateWithLifecycle()
            val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val sessionsByDate by viewModel.sessionsByDate.collectAsStateWithLifecycle()
            val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
            val activeSessionNotes by viewModel.activeSessionNotes.collectAsStateWithLifecycle()

            BlastEmstTheme(themeSetting = settings.appTheme) {
                MainAppRouter(
                    uiState = uiState,
                    activeSession = activeSession,
                    repCount = repCount,
                    userProfile = userProfile,
                    settings = settings,
                    sessionsByDate = sessionsByDate,
                    currentMonth = currentMonth,
                    activeSessionNotes = activeSessionNotes,
                    onStartSession = { viewModel.startNewSession() },
                    onAddRep = { viewModel.addRep() },
                    onFinishSession = { viewModel.finishActiveSession() },
                    onSaveProfile = { updatedProfile -> viewModel.saveProfile(updatedProfile) },
                    onSaveSettings = { newSettings -> viewModel.saveSettings(newSettings)},
                    onUpdateRepSoundUri = { uri -> viewModel.updateRepSoundUri(uri) },
                    onUpdateHapticFeedback = { isEnabled -> viewModel.updateHapticFeedback(isEnabled) },
                    onNextMonth = { viewModel.onNextMonth() },
                    onPreviousMonth = { viewModel.onPreviousMonth() },
                    onMonthScrolled = { newMonth -> viewModel.onMonthScrolled(newMonth) },
                    onDeleteSession = { sessionId -> viewModel.deleteSession(sessionId) },
                    onActiveSessionNotesChanged = { newNotes -> viewModel.onActiveSessionNotesChanged(newNotes) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppRouter(
    uiState: HomeScreenState,
    activeSession: Session?,
    repCount: Long,
    userProfile: UserProfile?,
    settings: AppSettings,
    sessionsByDate: Map<LocalDate, List<Session>>,
    currentMonth: YearMonth,
    activeSessionNotes: String,
    onStartSession: () -> Unit,
    onAddRep: () -> Unit,
    onFinishSession: () -> Unit,
    onSaveProfile: (UserProfile) -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
    onUpdateRepSoundUri: (Uri?) -> Unit,
    onUpdateHapticFeedback: (Boolean) -> Unit,
    onNextMonth: () -> Unit,
    onPreviousMonth: () -> Unit,
    onMonthScrolled: (YearMonth) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onActiveSessionNotesChanged: (String) -> Unit
) {
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }

    LaunchedEffect(activeSession) {
        currentScreen = if (activeSession != null) {
            AppScreen.ActiveSession(activeSession)
        } else {
            if (currentScreen is AppScreen.ActiveSession) AppScreen.Home else currentScreen
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (uiState.isLoading || userProfile == null) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator()
            }
            return@Surface
        }

        when (currentScreen) {
            is AppScreen.Home -> HomeScreen(
                uiState = uiState,
                onNavigate = { newScreen -> currentScreen = newScreen },
                onStartSession = onStartSession
            )
            is AppScreen.History -> HistoryScreen(
                sessionsByDate = sessionsByDate,
                currentMonth = currentMonth,
                onNavigate = { newScreen -> currentScreen = newScreen },
                onNextMonth = onNextMonth,
                onPreviousMonth = onPreviousMonth,
                onMonthScrolled = onMonthScrolled,
                onDeleteSession = onDeleteSession
            )
            is AppScreen.Profile -> ProfileScreen(
                userProfile = userProfile,
                onSaveProfile = onSaveProfile,
                onNavigateHome = { currentScreen = AppScreen.Home }
            )
            is AppScreen.Settings -> SettingsScreen(
                settings = settings,
                onSaveSettings = onSaveSettings,
                onUpdateRepSoundUri = onUpdateRepSoundUri,
                onUpdateHapticFeedback = onUpdateHapticFeedback,
                onNavigate = { newScreen -> currentScreen = newScreen }
            )
            is AppScreen.ActiveSession -> ActiveSessionScreen(
                repCount = repCount,
                settings = settings,
                notes = activeSessionNotes,
                onNotesChanged = onActiveSessionNotesChanged,
                onLogRep = onAddRep,
                onFinishSession = onFinishSession
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeScreenState,
    onNavigate: (AppScreen) -> Unit,
    onStartSession: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blast EMST") },
                actions = {
                    IconButton(onClick = { onNavigate(AppScreen.Profile) }) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                    IconButton(onClick = { onNavigate(AppScreen.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard(
                        label = "Reps Last Session",
                        value = uiState.lastSessionReps.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Sessions This Week",
                        value = "${uiState.sessionsThisWeek} / ${uiState.weeklySessionGoal}",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(64.dp))
                Button(
                    onClick = onStartSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Start New Session",
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Start New Session", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(onClick = { onNavigate(AppScreen.History) }) {
                    Text("View Session History")
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Builder: Tony C", style = MaterialTheme.typography.bodySmall)
                Text(text = uiState.appVersion, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sessionsByDate: Map<LocalDate, List<Session>>,
    currentMonth: YearMonth,
    onNavigate: (AppScreen) -> Unit,
    onNextMonth: () -> Unit,
    onPreviousMonth: () -> Unit,
    onMonthScrolled: (YearMonth) -> Unit,
    onDeleteSession: (Long) -> Unit
) {
    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }
    val calendarState = rememberCalendarState(
        startMonth = currentMonth.minusMonths(100),
        endMonth = currentMonth.plusMonths(100),
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = firstDayOfWeek
    )
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val selectedDateSessions = remember(selectedDate, sessionsByDate) {
        sessionsByDate[selectedDate].orEmpty()
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<Session?>(null) }

    LaunchedEffect(currentMonth) { calendarState.animateScrollToMonth(currentMonth) }

    LaunchedEffect(calendarState.firstVisibleMonth) {
        val newMonth = calendarState.firstVisibleMonth.yearMonth
        if (newMonth != currentMonth) { onMonthScrolled(newMonth) }
    }

    if (showDeleteDialog && sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to permanently delete this session?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSession(sessionToDelete!!.id)
                        showDeleteDialog = false
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Yes, Delete") }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) { Text("No") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session History") },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(AppScreen.Home) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
            CalendarHeader(
                month = currentMonth,
                onNextMonth = onNextMonth,
                onPreviousMonth = onPreviousMonth
            )
            DaysOfWeekHeader(firstDayOfWeek = firstDayOfWeek)
            HorizontalCalendar(
                state = calendarState,
                dayContent = { day ->
                    Day(
                        day = day,
                        isSelected = selectedDate == day.date,
                        sessions = sessionsByDate[day.date].orEmpty()
                    ) { date ->
                        selectedDate = if (selectedDate == date) null else date
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (selectedDate != null) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(selectedDateSessions, key = { it.id }) { session ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart) {
                                    sessionToDelete = session
                                    showDeleteDialog = true
                                    return@rememberSwipeToDismissBoxState false
                                }
                                return@rememberSwipeToDismissBoxState false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            modifier = Modifier.animateItem(),
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete Icon",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        ) {
                            SessionDetailItem(session = session)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Day(
    day: CalendarDay,
    isSelected: Boolean,
    sessions: List<Session>,
    onClick: (LocalDate) -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .clickable(
                enabled = day.position == DayPosition.MonthDate,
                onClick = { onClick(day.date) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                color = if (day.position == DayPosition.MonthDate) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
            if (sessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}


@Composable
fun CalendarHeader(
    month: YearMonth,
    onNextMonth: () -> Unit,
    onPreviousMonth: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
        }
        Text(
            text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNextMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
        }
    }
}

@Composable
fun DaysOfWeekHeader(firstDayOfWeek: DayOfWeek) {
    Row(modifier = Modifier.fillMaxWidth()) {
        val daysOfWeek = daysOfWeek(firstDayOfWeek = firstDayOfWeek)
        for (dayOfWeek in daysOfWeek) {
            Text(
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                text = dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun SessionDetailItem(session: Session) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }
    val localZoneId = remember { ZoneId.systemDefault() }

    val startTime = remember(session.start_time, localZoneId) {
        val utcInstant = Instant.parse(session.start_time)
        val localDateTime = utcInstant.atZone(localZoneId)
        timeFormatter.format(localDateTime)
    }

    val endTime = remember(session.end_time, localZoneId) {
        session.end_time?.let {
            val utcInstant = Instant.parse(it)
            val localDateTime = utcInstant.atZone(localZoneId)
            timeFormatter.format(localDateTime)
        } ?: "In Progress"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reps: ${session.rep_count}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pressure: ${session.pressure_setting}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Time: $startTime - $endTime",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (session.notes.isNotBlank()) {
                Text(
                    text = "Notes: ${session.notes}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit,
    onUpdateRepSoundUri: (Uri?) -> Unit,
    onUpdateHapticFeedback: (Boolean) -> Unit,
    onNavigate: (AppScreen) -> Unit
) {
    var defaultReps by remember(settings.defaultReps) { mutableStateOf(settings.defaultReps.toString()) }
    var weeklyGoal by remember(settings.weeklySessionGoal) { mutableStateOf(settings.weeklySessionGoal.toString()) }
    var defaultPressure by remember(settings.defaultPressure) { mutableStateOf(settings.defaultPressure.toString()) }
    var remindersEnabled by remember(settings.remindersEnabled) { mutableStateOf(settings.remindersEnabled) }
    var appTheme by remember(settings.appTheme) { mutableStateOf(settings.appTheme) }

    val context = LocalContext.current

    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                    onUpdateRepSoundUri(uri)
                } catch (e: SecurityException) {
                    Log.e("SettingsScreen", "Failed to take persistable URI permission for $uri", e)
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = {
                        val updatedSettings = settings.copy(
                            defaultReps = defaultReps.toIntOrNull() ?: settings.defaultReps,
                            weeklySessionGoal = weeklyGoal.toIntOrNull() ?: settings.weeklySessionGoal,
                            defaultPressure = defaultPressure.toIntOrNull() ?: settings.defaultPressure,
                            remindersEnabled = remindersEnabled,
                            appTheme = appTheme,
                        )
                        onSaveSettings(updatedSettings)
                        onNavigate(AppScreen.Home)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Training Parameters", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = defaultReps,
                onValueChange = { defaultReps = it },
                label = { Text("Reps per Session") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = weeklyGoal,
                onValueChange = { weeklyGoal = it },
                label = { Text("Weekly Session Goal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = defaultPressure,
                onValueChange = { defaultPressure = it },
                label = { Text("Pressure Setting") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Sound & Haptics", style = MaterialTheme.typography.titleMedium)

            Text("Rep Counter Sound", style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = {
                    soundPickerLauncher.launch(arrayOf("audio/*"))
                }) {
                    val soundName = settings.repSoundUri?.let { uriString ->
                        try {
                            uriString.toUri().lastPathSegment?.substringAfterLast('/') ?: "Custom Sound"
                        } catch (_: Exception) { "Custom Sound (Error)" }
                    } ?: "Choose Sound"
                    Text(soundName)
                }
                if (settings.repSoundUri != null) {
                    Button(onClick = { onUpdateRepSoundUri(null) }) {
                        Text("Use Default")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Haptic Feedback on Rep Counter", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.isHapticFeedbackEnabled,
                    onCheckedChange = { isEnabled ->
                        onUpdateHapticFeedback(isEnabled)
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Application", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable Reminders", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = remindersEnabled,
                    onCheckedChange = { remindersEnabled = it }
                )
            }

            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            val themeOptions = listOf("system", "light", "dark")
            Row(modifier = Modifier.fillMaxWidth()) {
                themeOptions.forEach { theme ->
                    Row(
                        Modifier
                            .selectable(
                                selected = (theme == appTheme),
                                onClick = { appTheme = theme }
                            )
                            .padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (theme == appTheme),
                            onClick = { appTheme = theme }
                        )
                        Text(
                            text = theme.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    repCount: Long,
    settings: AppSettings,
    notes: String,
    onNotesChanged: (String) -> Unit,
    onLogRep: () -> Unit,
    onFinishSession: () -> Unit
) {
    val goalReached = repCount >= settings.defaultReps
    val counterColor = if (goalReached) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session in Progress") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(onClick = onLogRep),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$repCount",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = MaterialTheme.typography.displayLarge.fontSize * 2.0
                    ),
                    fontWeight = FontWeight.Bold,
                    color = counterColor,
                    textAlign = TextAlign.Center
                )
            }

            AnimatedVisibility(
                visible = goalReached,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Well Done! Target Reached",
                    style = MaterialTheme.typography.headlineSmall,
                    color = counterColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChanged,
                    label = { Text("Session Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = onFinishSession,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Finish Session")
                }
            }
        }
    }
}

@Composable
fun BlastEmstTheme(
    themeSetting: String = "system",
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeSetting.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
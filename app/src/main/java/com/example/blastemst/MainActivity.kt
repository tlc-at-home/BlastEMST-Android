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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.ripple.rememberRipple
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

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

            BlastEmstTheme(themeSetting = settings.appTheme) {
                MainAppRouter(
                    uiState = uiState,
                    activeSession = activeSession,
                    repCount = repCount,
                    userProfile = userProfile,
                    settings = settings,
                    sessionsByDate = sessionsByDate,
                    currentMonth = currentMonth,
                    onStartSession = { viewModel.startNewSession() },
                    onAddRep = { viewModel.addRep() },
                    onFinishSession = { notes -> viewModel.finishActiveSession(notes) },
                    onSaveProfile = { updatedProfile -> viewModel.saveProfile(updatedProfile) },
                    onSaveSettings = { newSettings -> viewModel.saveSettings(newSettings)},
                    onUpdateRepSoundUri = { uri -> viewModel.updateRepSoundUri(uri) },
                    onUpdateHapticFeedback = { isEnabled -> viewModel.updateHapticFeedback(isEnabled) },
                    onNextMonth = { viewModel.onNextMonth() },
                    onPreviousMonth = { viewModel.onPreviousMonth() },
                    onMonthScrolled = { newMonth -> viewModel.onMonthScrolled(newMonth) }
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
    onStartSession: () -> Unit,
    onAddRep: () -> Unit,
    onFinishSession: (String) -> Unit,
    onSaveProfile: (UserProfile) -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
    onUpdateRepSoundUri: (Uri?) -> Unit,
    onUpdateHapticFeedback: (Boolean) -> Unit,
    onNextMonth: () -> Unit,
    onPreviousMonth: () -> Unit,
    onMonthScrolled: (YearMonth) -> Unit
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
                onMonthScrolled = onMonthScrolled
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

                // --- CHANGE 1: Increased the spacing from 16.dp to 32.dp ---
                Spacer(modifier = Modifier.height(32.dp))

                // --- CHANGE 2: Changed TextButton to OutlinedButton ---
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sessionsByDate: Map<LocalDate, List<Session>>,
    currentMonth: YearMonth,
    onNavigate: (AppScreen) -> Unit,
    onNextMonth: () -> Unit,
    onPreviousMonth: () -> Unit,
    onMonthScrolled: (YearMonth) -> Unit
) {
    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }
    val calendarState = rememberCalendarState(
        startMonth = currentMonth.minusMonths(100),
        endMonth = currentMonth.plusMonths(100),
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = firstDayOfWeek
    )

    // This new state will track the user's selected date
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    // Get the list of sessions for the selected date
    val selectedDateSessions = remember(selectedDate, sessionsByDate) {
        sessionsByDate[selectedDate].orEmpty()
    }

    // This effect watches for swipes and updates the ViewModel
    LaunchedEffect(calendarState.firstVisibleMonth) {
        val newMonth = calendarState.firstVisibleMonth.yearMonth
        // To prevent an infinite loop, only call the update if the month is different
        if (newMonth != currentMonth) {
            onMonthScrolled(newMonth)
        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            CalendarHeader(
                month = currentMonth,
                onNextMonth = onNextMonth,
                onPreviousMonth = onPreviousMonth
            )
            DaysOfWeekHeader(firstDayOfWeek = firstDayOfWeek)
            HorizontalCalendar(
                state = calendarState,
                dayContent = { day ->
                    // Update the call to our Day composable
                    Day(
                        day = day,
                        isSelected = selectedDate == day.date,
                        sessions = sessionsByDate[day.date].orEmpty(),
                        onClick = { date ->
                            // Update the selected date, or unselect if tapped again
                            selectedDate = if (selectedDate == date) null else date
                        }
                    )
                }
            )

            // This new section displays the details for the selected date
            Spacer(modifier = Modifier.height(16.dp))

            if (selectedDate != null) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(selectedDateSessions) { session ->
                        SessionDetailItem(session = session)
                    }
                }
            }
        }
    }
}

@Composable
fun Day(
    day: CalendarDay,
    isSelected: Boolean, // parameter to know if the day is selected
    sessions: List<Session>,
    onClick: (LocalDate) -> Unit // parameter to handle clicks
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f) // Makes the day cell a square
            .clickable(
                enabled = day.position == com.kizitonwose.calendar.core.DayPosition.MonthDate, // Only days in the current month are clickable
                onClick = { onClick(day.date) }
            )
            .then(
                // Add a border if the day is selected
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else Modifier
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
                color = if (day.position == com.kizitonwose.calendar.core.DayPosition.MonthDate) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
            // Show a dot if there was at least one session on this day
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
fun DaysOfWeekHeader(firstDayOfWeek: java.time.DayOfWeek) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // CORRECTED LOGIC
        val daysOfWeek = com.kizitonwose.calendar.core.daysOfWeek(firstDayOfWeek = firstDayOfWeek)
        for (dayOfWeek in daysOfWeek) {
            Text(
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                // CORRECTED LOGIC
                text = dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall
            )
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

@Composable
fun SessionDetailItem(session: Session) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val startTime = remember {
        session.start_time.let { timeFormatter.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(it)) }
    }
    val endTime = remember {
        session.end_time?.let { timeFormatter.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(it)) } ?: "In Progress"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Time: $startTime - $endTime",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (session.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
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
    // Local states for UI elements that might be edited before saving
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
                    // Persist permission for long-term access
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                    Log.d("SettingsScreen", "Persisted read permission for URI: $uri")
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
                        // Consolidate saving logic before navigating
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
            // "Training Defaults" changed to "Training Parameters"
            Text("Training Parameters", style = MaterialTheme.typography.titleMedium)

            // "Default Reps per Session" changed to "Reps per Session"
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

            // "Default Pressure Setting" changed to "Pressure Setting"
            OutlinedTextField(
                value = defaultPressure,
                onValueChange = { defaultPressure = it },
                label = { Text("Pressure Setting") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Sound & Haptics", style = MaterialTheme.typography.titleMedium)

            // "Repetition Sound" changed to "Rep Counter Sound"
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
                // "Haptic Feedback on Rep" changed to "Haptic Feedback on Rep Counter"
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
    onLogRep: () -> Unit,
    onFinishSession: (String) -> Unit
) {
    val goalReached = repCount >= settings.defaultReps
    val counterColor = if (goalReached) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
    var notes by remember { mutableStateOf("") }
    val interactionSource = remember { MutableInteractionSource() }

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
                    .clickable(
                        interactionSource = interactionSource,
                        indication = rememberRipple(
                            bounded = false,
                            radius = 200.dp
                        ),
                        onClick = onLogRep
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$repCount",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = MaterialTheme.typography.displayLarge.fontSize * 2.0
                    ),
                    fontWeight = FontWeight.ExtraBold,
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
                    onValueChange = { notes = it },
                    label = { Text("Session Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { onFinishSession(notes) },
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
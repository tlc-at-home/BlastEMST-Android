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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri

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

            BlastEmstTheme(themeSetting = settings.appTheme) {
                MainAppRouter(
                    uiState = uiState,
                    activeSession = activeSession,
                    repCount = repCount,
                    userProfile = userProfile,
                    settings = settings,
                    onStartSession = { viewModel.startNewSession() },
                    onAddRep = { viewModel.addRep() },
                    onFinishSession = { notes -> viewModel.finishActiveSession(notes) },
                    onSaveProfile = { updatedProfile -> viewModel.saveProfile(updatedProfile) },
                    onSaveSettings = { newSettings -> viewModel.saveSettings(newSettings)},
                    onUpdateRepSoundUri = { uri -> viewModel.updateRepSoundUri(uri) },
                    onUpdateHapticFeedback = { isEnabled -> viewModel.updateHapticFeedback(isEnabled) }
                )
            }
        }
    }
}

@Composable
fun MainAppRouter(
    uiState: HomeScreenState,
    activeSession: Session?,
    repCount: Long,
    userProfile: UserProfile?,
    settings: AppSettings,
    onStartSession: () -> Unit,
    onAddRep: () -> Unit,
    onFinishSession: (String) -> Unit,
    onSaveProfile: (UserProfile) -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
    onUpdateRepSoundUri: (Uri?) -> Unit,
    onUpdateHapticFeedback: (Boolean) -> Unit
) {
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }

    // This effect handles automatic navigation when a session starts or ends
    LaunchedEffect(activeSession) {
        currentScreen = if (activeSession != null) {
            AppScreen.ActiveSession(activeSession)
        } else {
            // When finishing a session, navigate home
            if (currentScreen is AppScreen.ActiveSession) AppScreen.Home else currentScreen
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Show a loading indicator until the initial data is ready
        if (uiState.isLoading || userProfile == null) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator()
            }
            return@Surface
        }

        // The "when" block shows the correct screen based on the current navigation state
        when (val screen = currentScreen) {
            is AppScreen.Home -> HomeScreen(
                appVersion = uiState.appVersion,
                onNavigate = { newScreen -> currentScreen = newScreen },
                onStartSession = onStartSession
            )
            is AppScreen.History -> HistoryScreen(
                sessions = uiState.sessions,
                onNavigate = { newScreen -> currentScreen = newScreen }
            )
            is AppScreen.Profile -> ProfileScreen(
                userProfile = userProfile,
                onSaveProfile = onSaveProfile,
                onNavigateHome = { currentScreen = AppScreen.Home }
            )
            is AppScreen.Settings -> SettingsScreen( // THIS IS THE BLOCK TO CHECK
                settings = settings,
                onSaveSettings = onSaveSettings,
                onUpdateRepSoundUri = onUpdateRepSoundUri,
                onUpdateHapticFeedback = onUpdateHapticFeedback,
                onNavigate = { newScreen -> currentScreen = newScreen }
            )
            is AppScreen.ActiveSession -> ActiveSessionScreen(
                session = screen.session,
                repCount = repCount,
                settings = settings,
                onLogRep = onAddRep,
                onFinishSession = onFinishSession
            )
        }
    }
}


/**
 * Composable function for the home screen of the Blast EMST app.
 *
 * This screen serves as the main entry point after the app loads. It displays:
 * - A welcome message.
 * - A button to navigate to the "Session History" screen.
 * - A floating action button to start a new EMST session.
 * - A top app bar with:
 *     - The title "Blast EMST Home".
 *     - An icon button to navigate to the "Profile" screen.
 *     - An icon button to navigate to the "Settings" screen.
 * - App version and builder information at the bottom of the screen.
 *
 * @param appVersion The current version of the application, typically obtained from build configuration.
 * @param onNavigate A lambda function that is invoked when a navigation event occurs.
 *                   It takes an [AppScreen] destination as a parameter.
 * @param onStartSession A lambda function that is invoked when the user clicks the
 *                       floating action button to begin a new session.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    appVersion: String,
    onNavigate: (AppScreen) -> Unit,
    onStartSession: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blast EMST Home") },
                actions = {
                    IconButton(onClick = { onNavigate(AppScreen.Profile) }) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                    IconButton(onClick = { onNavigate(AppScreen.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = { // FAB is defined here
            FloatingActionButton(onClick = onStartSession) {
                Icon(Icons.Filled.Add, contentDescription = "Start New Session")
            }
        }
    ) { innerPadding -> // This padding is for the main content area
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) { // Apply padding here
            Column(
                modifier = Modifier
                    .fillMaxSize() // This Column will respect the padding from the Box
                    .padding(16.dp), // Additional padding for the content inside the Column
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Welcome!",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                Button(
                    onClick = { onNavigate(AppScreen.History) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Session History")
                }
            }
            // This Column is positioned at the bottom, within the padded Box
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Builder: Tony C", style = MaterialTheme.typography.bodySmall)
                Text(text = appVersion, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sessions: List<Session>,
    onNavigate: (AppScreen) -> Unit
) {
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
        if (sessions.isEmpty()) {
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No session history found.")
            }
        } else {
            SessionList(sessions = sessions, modifier = Modifier.padding(innerPadding))
        }
    }
}

// Assuming AppSettings and AppScreen are defined elsewhere and AppSettings includes:
// val repSoundUri: String? = null,
// val isHapticFeedbackEnabled: Boolean = true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit, // This will save all settings at once
    onUpdateRepSoundUri: (Uri?) -> Unit, // Specifically for updating the sound URI
    onUpdateHapticFeedback: (Boolean) -> Unit, // Specifically for haptic toggle
    onNavigate: (AppScreen) -> Unit
) {
    // Local states for UI elements that might be edited before saving
    var defaultReps by remember(settings.defaultReps) { mutableStateOf(settings.defaultReps.toString()) }
    var weeklyGoal by remember(settings.weeklySessionGoal) { mutableStateOf(settings.weeklySessionGoal.toString()) }
    var remindersEnabled by remember(settings.remindersEnabled) { mutableStateOf(settings.remindersEnabled) }
    var appTheme by remember(settings.appTheme) { mutableStateOf(settings.appTheme) }

    // For haptic feedback, we can directly use the settings value if onUpdateHapticFeedback updates the ViewModel's state immediately
    // If onUpdateHapticFeedback is deferred, you might need a local state like:
    // var hapticsEnabledLocal by remember(settings.isHapticFeedbackEnabled) { mutableStateOf(settings.isHapticFeedbackEnabled) }

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
                    onUpdateRepSoundUri(uri) // Update ViewModel immediately
                } catch (e: SecurityException) {
                    Log.e("SettingsScreen", "Failed to take persistable URI permission for $uri", e)
                    // Optionally show a toast or dialog to the user
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
                            remindersEnabled = remindersEnabled,
                            appTheme = appTheme,
                            // repSoundUri and isHapticFeedbackEnabled are already updated
                            // in the ViewModel via their specific lambdas,
                            // so settings.repSoundUri and settings.isHapticFeedbackEnabled should be current
                            // OR, if you prefer explicit save button, collect them here.
                        )
                        onSaveSettings(updatedSettings)
                        onNavigate(AppScreen.Home)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                    }
                }
                // Removed the explicit save button from TopAppBar to simplify, assuming save on back
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()), // Added for scrollability
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Training Defaults", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = defaultReps,
                onValueChange = { defaultReps = it },
                label = { Text("Default Reps per Session") },
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Sound & Haptics", style = MaterialTheme.typography.titleMedium)

            // Rep Sound Picker
            Text("Repetition Sound", style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = {
                    soundPickerLauncher.launch(arrayOf("audio/*"))
                }) {
                    // Display current sound name or "Choose Sound"
                    val soundName = settings.repSoundUri?.let { uriString ->
                        try {
                            // Basic way to get a displayable name, might need more robust solution
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

            // Haptic Feedback Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Haptic Feedback on Rep", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.isHapticFeedbackEnabled,
                    onCheckedChange = { isEnabled ->
                        onUpdateHapticFeedback(isEnabled) // Update ViewModel immediately
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

            // Consolidate saving to when navigating back
            // The explicit "Save Settings" button can be removed if saving on back navigation is preferred.
            // If you want an explicit save button, re-add it here and adjust the TopAppBar's navigationIcon's onClick.
            /*
            Spacer(modifier = Modifier.weight(1f)) // Use Spacer only if you have an explicit save button at the bottom
            Button(
                onClick = {
                    val finalSettings = settings.copy( // Start with current settings from ViewModel
                        defaultReps = defaultReps.toIntOrNull() ?: settings.defaultReps,
                        weeklySessionGoal = weeklyGoal.toIntOrNull() ?: settings.weeklySessionGoal,
                        remindersEnabled = remindersEnabled,
                        appTheme = appTheme
                        // repSoundUri and isHapticFeedbackEnabled are assumed to be updated
                        // in the ViewModel via their specific lambdas (onUpdateRepSoundUri, onUpdateHapticFeedback)
                    )
                    onSaveSettings(finalSettings)
                    onNavigate(AppScreen.Home)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }
            */
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    session: Session,
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
                .padding(horizontal = 16.dp, vertical = 24.dp), // Adjusted padding
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Spacer to push content down a bit from the TopAppBar
            Spacer(modifier = Modifier.height(32.dp))

            // Clickable area for logging reps - This is now the main rep display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Takes up available vertical space to help center
                    .clickable(
                        interactionSource = interactionSource,
                        indication = rememberRipple(
                            bounded = false,
                            radius = 200.dp
                        ), // Visual feedback for click
                        onClick = onLogRep
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$repCount",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = MaterialTheme.typography.displayLarge.fontSize * 2.0 // Even larger font
                    ),
                    fontWeight = FontWeight.ExtraBold, // Bolder
                    color = counterColor,
                    textAlign = TextAlign.Center
                )
            }

            // "Well Done" message, appears below the clickable rep count
            AnimatedVisibility(
                visible = goalReached,
                modifier = Modifier.padding(bottom = 16.dp) // Add some space before notes
            ) {
                Text(
                    text = "Well Done! Target Reached",
                    style = MaterialTheme.typography.headlineSmall,
                    color = counterColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Notes and Finish Button section
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
fun SessionList(sessions: List<Session>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(sessions) { session ->
            SessionItem(session = session)
            HorizontalDivider()
        }
    }
}

@Composable
fun SessionItem(session: Session, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val formattedTimestamp = session.start_time.substringBefore("T")
            Text(text = formattedTimestamp, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Level ${session.pressure_setting} - ${session.rep_count} reps",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (session.notes.isNotBlank()) {
            Text(
                text = session.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
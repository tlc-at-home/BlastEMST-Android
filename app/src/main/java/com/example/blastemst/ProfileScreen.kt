package com.example.blastemst

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class) // Added for TopAppBar and Scaffold
@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    onSaveProfile: (UserProfile) -> Unit,
    onNavigateHome: () -> Unit
) {
    // Local states for UI elements, initialized from userProfile
    var firstName by remember(userProfile.id) { mutableStateOf(userProfile.first_name) }
    var lastName by remember(userProfile.id) { mutableStateOf(userProfile.last_name) }
    var dob by remember(userProfile.id) { mutableStateOf(userProfile.dob) }
    var speechTherapist by remember(userProfile.id) { mutableStateOf(userProfile.speech_therapist) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Profile") },
                navigationIcon = {
                    IconButton(onClick = {
                        val updatedProfile = userProfile.copy(
                            first_name = firstName,
                            last_name = lastName,
                            dob = dob,
                            speech_therapist = speechTherapist
                        )
                        onSaveProfile(updatedProfile) // Save the profile
                        onNavigateHome()              // Navigate back
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back and Save"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // Apply padding from Scaffold
                .padding(16.dp)        // Additional padding for content
                .verticalScroll(rememberScrollState()), // Make content scrollable
            verticalArrangement = Arrangement.spacedBy(16.dp)

            // horizontalAlignment = Alignment.CenterHorizontally // Optional, if you want items centered
        ) {
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Surname") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = dob,
                onValueChange = { dob = it },
                label = { Text("Date of Birth") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = speechTherapist,
                onValueChange = { speechTherapist = it },
                label = { Text("Speech Therapist") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
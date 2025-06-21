package com.example.blastemst

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class UserProfile(
    val id: Long,
    val first_name: String,
    val last_name: String,
    val dob: String,
    val speech_therapist: String
)
package com.example.blastemst

import kotlinx.serialization.Serializable

// The @Serializable annotation tells the Kotlin compiler to automatically
// generate the code needed to convert this class to and from JSON.
@Serializable
data class Session(
    val id: Long,
    val start_time: String,
    val end_time: String?, // The question mark makes this field nullable
    val pressure_setting: Int,
    val notes: String,
    val rep_count: Int
)
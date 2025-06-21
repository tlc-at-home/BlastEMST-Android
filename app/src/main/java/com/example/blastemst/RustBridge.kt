package com.example.blastemst

object RustBridge {
    init {
        System.loadLibrary("blast_emst_core")
    }

    // General functions
    external fun initDatabase(path: String)

    // Session functions
    external fun startSession(pressure_setting: Int, notes: String): Long
    external fun getAllSessions(): String
    external fun getActiveSession(): String?
    external fun endSession(sessionId: Long, notes: String)
    external fun getSessionCountForWeek(): Int
    external fun getLastSessionEndTime(): String

    // Rep functions
    external fun addRep(sessionId: Long)
    external fun getTotalReps(sessionId: Long): Long

    // Profile functions
    external fun getProfile(): String
    external fun updateProfile(profileJson: String)

    // Settings functions
    external fun getSetting(key: String, defaultValue: String): String
    external fun setSetting(key: String, value: String)
}
use rusqlite::{Connection, Result, Row, params, OptionalExtension};
use chrono::{DateTime, Utc};
use serde::{Serialize, Deserialize};

// --- Data Models for our Entities ---

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct UserProfile {
    pub id: i64,
    pub first_name: String,
    pub last_name: String,
    pub dob: String,
    pub speech_therapist: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct Session {
    pub id: i64,
    pub start_time: DateTime<Utc>,
    pub end_time: Option<DateTime<Utc>>,
    pub pressure_setting: i32,
    pub notes: String,
    pub rep_count: i64,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct Rep {
    pub id: i64,
    pub session_id: i64,
    pub rep_timestamp: DateTime<Utc>,
}

// --- Helper to convert a database row into a Session struct ---
impl<'stmt> TryFrom<&Row<'stmt>> for Session {
    type Error = rusqlite::Error;

    fn try_from(row: &Row<'stmt>) -> Result<Self, Self::Error> {
        // Step 1: Get the timestamps as TEXT from the database.
        let start_time_str: String = row.get(1)?;
        let end_time_opt_str: Option<String> = row.get(2)?;

        // Step 2: Parse the TEXT into DateTime<Utc> objects.
        let start_time = DateTime::parse_from_rfc3339(&start_time_str)
            .map(|dt| dt.with_timezone(&Utc))
            .map_err(|e| rusqlite::Error::FromSqlConversionFailure(1, rusqlite::types::Type::Text, Box::new(e)))?;

        let end_time = match end_time_opt_str {
            Some(s) => Some(
                DateTime::parse_from_rfc3339(&s)
                    .map(|dt| dt.with_timezone(&Utc))
                    .map_err(|e| rusqlite::Error::FromSqlConversionFailure(2, rusqlite::types::Type::Text, Box::new(e)))?
            ),
            None => None,
        };

        Ok(Session {
            id: row.get(0)?,
            start_time,
            end_time,
            pressure_setting: row.get(3)?,
            notes: row.get(4)?,
            rep_count: row.get(5)?,
        })
    }
}


// --- Database Initialization Function ---
pub fn initialize_database(db_path: &str) -> Result<Connection> {
    let conn = Connection::open(db_path)?;
    conn.execute("PRAGMA foreign_keys = ON;", [])?; // Good practice to enable foreign key constraints

    // Create the user_profile table
    conn.execute(
        "CREATE TABLE IF NOT EXISTS user_profile (
            id INTEGER PRIMARY KEY,
            first_name TEXT NOT NULL DEFAULT '',
            last_name TEXT NOT NULL DEFAULT '',
            dob DATE NOT NULL DEFAULT '',
            speech_therapist TEXT NOT NULL DEFAULT ''
        )",
        (),
    )?;

    // Create the app_settings table
    conn.execute(
        "CREATE TABLE IF NOT EXISTS app_settings (
            key TEXT PRIMARY KEY NOT NULL,
            value TEXT NOT NULL
        )",
        (),
    )?;

    // Create the sessions table
    conn.execute(
        "CREATE TABLE IF NOT EXISTS sessions (
            id INTEGER PRIMARY KEY,
            start_time DATETIME NOT NULL,
            end_time DATETIME,
            pressure_setting INTEGER NOT NULL,
            notes TEXT
        )",
        (),
    )?;

    // Create the reps table
    conn.execute(
        "CREATE TABLE IF NOT EXISTS reps (
            id INTEGER PRIMARY KEY,
            session_id INTEGER NOT NULL,
            rep_timestamp DATETIME NOT NULL,
            FOREIGN KEY (session_id) REFERENCES sessions (id) ON DELETE CASCADE
        )",
        (),
    )?;

    Ok(conn)
}

// --- Database Functions ---

pub fn start_new_session(conn: &Connection, pressure_setting: i32, notes: &str) -> Result<i64> {
    let now = Utc::now().to_rfc3339();
    conn.execute(
        "INSERT INTO sessions (start_time, pressure_setting, notes) VALUES (?1, ?2, ?3)",
        (now, pressure_setting, notes),
    )?;
    Ok(conn.last_insert_rowid())
}

pub fn get_profile(conn: &Connection) -> Result<UserProfile> {
    let query_result = conn.query_row(
        "SELECT id, first_name, last_name, dob, speech_therapist FROM user_profile WHERE id = 1",
        [],
        |row| {
            Ok(UserProfile {
                id: row.get(0)?,
                first_name: row.get(1)?,
                last_name: row.get(2)?,
                dob: row.get(3)?,
                speech_therapist: row.get(4)?,
            })
        },
    );

    match query_result {
        Ok(profile) => Ok(profile),
        Err(rusqlite::Error::QueryReturnedNoRows) => {
            let default_profile = UserProfile {
                id: 1,
                ..Default::default()
            };
            conn.execute(
                "INSERT INTO user_profile (id, first_name, last_name, dob, speech_therapist) VALUES (?1, ?2, ?3, ?4, ?5)",
                (
                    &default_profile.id,
                    &default_profile.first_name,
                    &default_profile.last_name,
                    &default_profile.dob,
                    &default_profile.speech_therapist,
                ),
            )?;
            Ok(default_profile)
        }
        Err(e) => Err(e),
    }
}

pub fn update_profile(conn: &Connection, profile: &UserProfile) -> Result<()> {
    conn.execute(
        "UPDATE user_profile SET first_name = ?2, last_name = ?3, dob = ?4, speech_therapist = ?5
         WHERE id = ?1",
        (
            &profile.id,
            &profile.first_name,
            &profile.last_name,
            &profile.dob,
            &profile.speech_therapist,
        ),
    )?;
    Ok(())
}

pub fn get_all_sessions(conn: &Connection) -> Result<Vec<Session>> {
    // This SQL query now joins the sessions and reps tables to get the count for each session
    let mut stmt = conn.prepare(
        "SELECT s.id, s.start_time, s.end_time, s.pressure_setting, s.notes, COUNT(r.id) as rep_count
         FROM sessions s
         LEFT JOIN reps r ON s.id = r.session_id
         GROUP BY s.id
         ORDER BY s.start_time DESC"
    )?;
    let mut rows = stmt.query([])?;
    let mut sessions = Vec::new();
    while let Some(row) = rows.next()? {
        sessions.push(Session::try_from(row)?);
    }
    Ok(sessions)
}

pub fn get_active_session(conn: &Connection) -> Result<Option<Session>> {
    let mut stmt = conn.prepare(
        "SELECT s.id, s.start_time, s.end_time, s.pressure_setting, s.notes, COUNT(r.id) as rep_count
         FROM sessions s
         LEFT JOIN reps r ON s.id = r.session_id
         WHERE s.end_time IS NULL
         GROUP BY s.id  -- Important to group by all non-aggregated columns from sessions if there could be multiple active sessions (though LIMIT 1 should prevent this)
         ORDER BY s.start_time DESC
         LIMIT 1"
    )?;
    let mut rows = stmt.query([])?;
    if let Some(row) = rows.next()? {
        Ok(Some(Session::try_from(row)?))
    } else {
        Ok(None)
    }
}

pub fn add_rep(conn: &Connection, session_id: i64) -> Result<()> {
    let now = Utc::now().to_rfc3339();
    conn.execute(
        "INSERT INTO reps (session_id, rep_timestamp) VALUES (?1, ?2)",
        (session_id, now),
    )?;
    Ok(())
}

pub fn get_total_reps(conn: &Connection, session_id: i64) -> Result<i64> {
    conn.query_row(
        "SELECT COUNT(*) FROM reps WHERE session_id = ?1",
        [session_id],
        |row| row.get(0),
    )
}

pub fn end_session(conn: &Connection, session_id: i64, notes: &str) -> Result<()> { // Add notes parameter
    let now = Utc::now().to_rfc3339();
    conn.execute(
        "UPDATE sessions SET end_time = ?1, notes = ?2 WHERE id = ?3", // Add notes to SET clause
        params![now, notes, session_id], // Add notes to params
    )?;
    Ok(())
}

// Gets a setting's value for a given key. It returns an Option<String>
// which will be Some(value) if the key exists, and None if it doesn't.
pub fn get_setting(conn: &Connection, key: &str) -> Result<Option<String>> {
    let mut stmt = conn.prepare("SELECT value FROM app_settings WHERE key = ?1")?;
    let mut rows = stmt.query([key])?;

    if let Some(row) = rows.next()? {
        Ok(Some(row.get(0)?))
    } else {
        Ok(None)
    }
}

// Sets a setting's value for a given key.
// Uses "INSERT OR REPLACE" to create the setting if it's new or update it if it exists.
pub fn set_setting(conn: &Connection, key: &str, value: &str) -> Result<()> {
    conn.execute(
        "INSERT OR REPLACE INTO app_settings (key, value) VALUES (?1, ?2)",
        (key, value),
    )?;
    Ok(())
}

pub fn get_session_count_for_current_week(conn: &Connection) -> Result<i32> {
    conn.query_row(
        // Use end_time and ensure it's not NULL
        "SELECT COUNT(*) FROM sessions WHERE end_time IS NOT NULL AND strftime('%Y-%W', end_time) = strftime('%Y-%W', 'now', 'localtime')",
        [],
        |row| row.get(0),
    ).map_err(|e| e.into())
}

pub fn get_last_session_end_time(conn: &Connection) -> Result<Option<String>> {
    conn.query_row(
        "SELECT MAX(end_time) FROM sessions WHERE end_time IS NOT NULL",
        [],
        |row| row.get(0),
    )
    .optional() // This makes it return Ok(None) if no rows are found, instead of an Error
}

pub fn delete_session(conn: &Connection, session_id: i64) -> Result<()> {
    let rows_affected = conn.execute("DELETE FROM sessions WHERE id = ?1", [session_id])?;
    if rows_affected == 0 {
        Err(rusqlite::Error::QueryReturnedNoRows)
    } else {
        Ok(())
    }
}
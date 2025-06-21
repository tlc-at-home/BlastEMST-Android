use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jstring, jint, jlong};
use std::sync::Mutex;
use log::{info, error};

mod db;

static DB_CONNECTION: Mutex<Option<rusqlite::Connection>> = Mutex::new(None);

#[unsafe(no_mangle)]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_blastemst_RustBridge_initDatabase(
    mut env: JNIEnv,
    _class: JClass,
    db_path: JString,
) {
    android_log::init("Rust").unwrap();
    let path_str: String = env.get_string(&db_path).expect("Invalid DB path").into();
    match db::initialize_database(&path_str) {
        Ok(connection) => {
            *DB_CONNECTION.lock().unwrap() = Some(connection);
            info!("Database initialized successfully at path: {}", path_str);
        }
        Err(e) => {
            error!("Failed to initialize database: {}", e);
        }
    }
}

#[unsafe(no_mangle)]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_blastemst_RustBridge_startSession(
    mut env: JNIEnv,
    _class: JClass,
    pressure_setting: jint,
    notes: JString,
) -> jlong {
    info!("Attempting to start a new session.");
    let notes_str: String = env.get_string(&notes).expect("Invalid notes string").into();
    let db_conn_guard = DB_CONNECTION.lock().unwrap();
    if let Some(conn) = &*db_conn_guard {
        match db::start_new_session(conn, pressure_setting, &notes_str) {
            Ok(new_id) => {
                info!("Successfully started new session with id: {}", new_id);
                new_id
            }
            Err(e) => {
                error!("Failed to start new session: {}", e);
                -1
            }
        }
    } else {
        error!("Database connection not initialized.");
        -1
    }
}

#[unsafe(no_mangle)]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_blastemst_RustBridge_getAllSessions(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    info!("Attempting to get all sessions.");
    let db_conn_guard = DB_CONNECTION.lock().unwrap();
    let default_json = env.new_string("[]").expect("Couldn't create default JSON string.");
    if let Some(conn) = &*db_conn_guard {
        match db::get_all_sessions(conn) {
            Ok(sessions) => {
                let json_string = serde_json::to_string(&sessions).unwrap_or_else(|_| "[]".to_string());
                info!("Successfully retrieved {} sessions.", sessions.len());
                env.new_string(json_string).unwrap_or(default_json).into_raw()
            }
            Err(e) => {
                error!("Failed to get all sessions: {}", e);
                default_json.into_raw()
            }
        }
    } else {
        error!("Database connection not initialized.");
        default_json.into_raw()
    }
}

#[unsafe(no_mangle)]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_blastemst_RustBridge_getProfile(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    info!("Attempting to get user profile.");
    let db_conn_guard = DB_CONNECTION.lock().unwrap();
    let default_json = env.new_string("{}").expect("Couldn't create default JSON string.");
    if let Some(conn) = &*db_conn_guard {
        match db::get_profile(conn) {
            Ok(profile) => {
                let json_string = serde_json::to_string(&profile).unwrap_or_else(|_| "{}".to_string());
                info!("Successfully retrieved profile for {}", profile.first_name);
                env.new_string(json_string).unwrap_or(default_json).into_raw()
            }
            Err(e) => {
                error!("Failed to get profile: {}", e);
                default_json.into_raw()
            }
        }
    } else {
        error!("Database connection not initialized.");
        default_json.into_raw()
    }
}

#[unsafe(no_mangle)]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_blastemst_RustBridge_updateProfile(
    mut env: JNIEnv,
    _class: JClass,
    profile_json: JString,
) {
    info!("Attempting to update user profile.");
    let profile_str: String = env.get_string(&profile_json).expect("Invalid profile JSON").into();
    match serde_json::from_str::<db::UserProfile>(&profile_str) {
        Ok(profile) => {
            let db_conn_guard = DB_CONNECTION.lock().unwrap();
            if let Some(conn) = &*db_conn_guard {
                if let Err(e) = db::update_profile(conn, &profile) {
                    error!("Failed to update profile in DB: {}", e);
                }
            } else {
                error!("Database connection not initialized for update.");
            }
        }
        Err(e) => {
            error!("Failed to deserialize profile JSON: {}", e);
        }
    }
}

#[unsafe(no_mangle)]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_blastemst_RustBridge_getActiveSession(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    info!("Checking for active session.");
    let db_conn_guard = DB_CONNECTION.lock().unwrap();
    let default_json = env.new_string("").expect("Couldn't create default JSON string.");
    if let Some(conn) = &*db_conn_guard {
        match db::get_active_session(conn) {
            Ok(Some(session)) => {
                let json_string = serde_json::to_string(&session).unwrap_or_else(|_| "".to_string());
                info!("Found active session with id: {}", session.id);
                env.new_string(json_string).unwrap_or(default_json).into_raw()
            }
            Ok(None) => {
                info!("No active session found.");
                default_json.into_raw()
            }
            Err(e) => {
                error!("Failed to get active session: {}", e);
                default_json.into_raw()
            }
        }
    } else {
        error!("Database connection not initialized.");
        default_json.into_raw()
    }
}

#[unsafe(no_mangle)]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_blastemst_RustBridge_getTotalReps(
    _env: JNIEnv,
    _class: JClass,
    session_id: jlong,
) -> jlong {
    info!("Getting rep count for session id: {}", session_id);
    let db_conn_guard = DB_CONNECTION.lock().unwrap();
    if let Some(conn) = &*db_conn_guard {
        match db::get_total_reps(conn, session_id) {
            Ok(count) => count,
            Err(e) => {
                error!("Failed to get rep count: {}", e);
                0
            }
        }
    } else {
        error!("Database connection not initialized.");
        0
    }
}

#[unsafe(no_mangle)]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_blastemst_RustBridge_addRep(
    _env: JNIEnv,
    _class: JClass,
    session_id: jlong,
) {
    info!("Adding rep for session id: {}", session_id);
    let db_conn_guard = DB_CONNECTION.lock().unwrap();
    if let Some(conn) = &*db_conn_guard {
        if let Err(e) = db::add_rep(conn, session_id) {
            error!("Failed to add rep: {}", e)
        }
    } else {
        error!("Database connection not initialized.");
    }
}

#[unsafe(no_mangle)]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_blastemst_RustBridge_endSession(
    mut env: JNIEnv, // Add mut env if you didn't have it
    _class: JClass,
    session_id: jlong,
    notes: JString, // Add notes parameter (JString)
) {
    let notes_str: String = env.get_string(&notes).expect("Invalid notes string from Java").into(); // Convert JString to Rust String
    info!("Attempting to end session id: {} with notes: '{}'", session_id, notes_str); // Log notes
    let db_conn_guard = DB_CONNECTION.lock().unwrap();
    if let Some(conn) = &*db_conn_guard {
        match db::end_session(conn, session_id, &notes_str) { // Pass notes_str to db::end_session
            Ok(_) => info!("Successfully ended session."),
            Err(e) => error!("Failed to end session: {}", e),
        }
    } else {
        error!("Database connection not initialized.");
    }
}

#[unsafe(no_mangle)]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_blastemst_RustBridge_getSetting(
    mut env: JNIEnv,
    _class: JClass,
    key: JString,
    default_value: JString,
) -> jstring {
    let key_str: String = env.get_string(&key).expect("Invalid key string").into();
    let default_value_str: String = env.get_string(&default_value).expect("Invalid default value string").into();

    info!("Getting setting for key: {}", &key_str);
    let db_conn_guard = DB_CONNECTION.lock().unwrap();

    // The logic inside the function is now corrected
    let result_str = if let Some(conn) = &*db_conn_guard {
        // Correctly call get_setting with two arguments
        match db::get_setting(conn, &key_str) {
            // If we get a value, use it. If not, use the default.
            Ok(Some(value)) => value,
            Ok(None) => default_value_str,
            Err(e) => {
                error!("Failed to get setting for key {}: {}", &key_str, e);
                default_value_str // Return default on any error
            }
        }
    } else {
        error!("Database connection not initialized.");
        default_value_str
    };

    env.new_string(result_str).expect("Couldn't create Java string").into_raw()
}

#[unsafe(no_mangle)]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_blastemst_RustBridge_setSetting(
    mut env: JNIEnv,
    _class: JClass,
    key: JString,
    value: JString,
) {
    let key_str: String = env.get_string(&key).expect("Invalid key string").into();
    let value_str: String = env.get_string(&value).expect("Invalid value string").into();

    info!("Setting key '{}' to value '{}'", &key_str, &value_str);
    let db_conn_guard = DB_CONNECTION.lock().unwrap();
    if let Some(conn) = &*db_conn_guard {
        if let Err(e) = db::set_setting(conn, &key_str, &value_str) {
            error!("Failed to set setting for key {}: {}", &key_str, e);
        }
    } else {
        error!("Database connection not initialized.");
    }
}

#[unsafe(no_mangle)]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_blastemst_RustBridge_getSessionCountForWeek(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    info!("Getting session count for the week.");
    let db_conn_guard = DB_CONNECTION.lock().unwrap();
    if let Some(conn) = &*db_conn_guard {
        match db::get_session_count_for_current_week(conn) {
            Ok(count) => count,
            Err(e) => {
                error!("Failed to get weekly session count: {}", e);
                0
            }
        }
    } else {
        error!("Database connection not initialized.");
        0
    }
}

#[unsafe(no_mangle)]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_example_blastemst_RustBridge_getLastSessionEndTime(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let db_conn_guard = DB_CONNECTION.lock().unwrap();
    let result_str = if let Some(conn) = &*db_conn_guard {
        match db::get_last_session_end_time(conn) {
            Ok(Some(time_str)) => time_str,
            _ => "".to_string(), // Return empty string for errors or if no session exists
        }
    } else {
        "".to_string()
    };
    env.new_string(result_str).expect("Couldn't create Java string").into_raw()
}
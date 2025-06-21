# Blast EMST - Expiratory Muscle Strength Training App

Blast EMST is a mobile application for Android designed to help users track their Expiratory Muscle Strength Training (EMST). It allows users to log training sessions, count repetitions, view their history, and customize their experience with smart, goal-oriented reminders.

![Screenshot](https://i.imgur.com/3b395f.png) ## Core Features

- **Session Management:** Start, track, and end training sessions with detailed notes.
- **Repetition Counting:** Log individual reps during an active session with a simple tap interface.
- **Haptic & Audio Feedback:** Provides customizable sound and vibration feedback for each repetition.
- **Smart Goal-Oriented Reminders:** An intelligent notification system that reminds users to train only if they are falling behind on their user-defined weekly session goal. The reminder is triggered by 24 hours of inactivity.
- **User Profiles:** Store and edit user details.
- **Session History:** View a chronological list of all past training sessions.
- **Persistent Settings:** Customize default session values, app theme, and feedback options.

## Architecture

This project features a hybrid architecture that leverages the strengths of both Rust and Kotlin. The core business logic and database management are handled by a high-performance **Rust** library, which is called from a modern, reactive Android frontend built with **Kotlin** and **Jetpack Compose**.

```mermaid
graph TD
%% Node Definitions
    A["UI Screens (Jetpack Compose)"]
    B["ViewModel"]
    C["RustBridge"]
    D["Native Library (libblast_emst_core.so)"]
    E["JNI Bridge (lib.rs)"]
    F["Database Logic (db.rs)"]
    G[("SQLite Database")]

%% Links
    A -- "Observes State / Sends Events" --> B
    B -- "Calls Functions" --> C
    C -- "Loads" --> D
    D -- "JNI Calls" --> E
    E -- "Uses" --> F
    F -- "Interacts with" --> G

%% Styling
    classDef kotlin fill:#BDEB9A,stroke:#333
    classDef rust fill:#F9D479,stroke:#333
    class A,B,C kotlin
    class D,E,F,G rust
```

## Building the Project

The project is divided into two main parts: the Rust core and the Android app.

### 1. Build the Rust Core

The Rust core must be compiled first, as the Android app depends on its compiled libraries. The repository includes a helper script to build the Rust code for all required Android architectures.

1.  Navigate to the `blast_emst_core` directory:
    ```sh
    cd blast_emst_core
    ```
2.  Ensure the build script is executable:
    ```sh
    chmod +x build_android.sh
    ```
3.  Run the script:
    ```sh
    ./build_android.sh
    ```
This script will compile the native libraries (`.so` files) and automatically place them in the correct `app/src/main/jniLibs` directory.

### 2. Build the Android App

Once the Rust libraries have been built and placed in the `jniLibs` folder:

1.  Open the entire project's root `BlastEMST` folder in the latest version of Android Studio.
2.  Allow Gradle to sync the project dependencies.
3.  Use the standard **Build > Make Project** or click the "Run" button to build and deploy the app to an emulator or physical device.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.
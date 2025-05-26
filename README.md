[English](README.md) | [Русский](README_ru.md)

# Storog: AI-Powered Visual Monitoring Android App

Storog is an Android application that transforms your device into a smart visual monitoring system. It uses the device's camera to detect changes or movement in a designated area. When a significant change is detected, Storog leverages the Gemini AI model to analyze the scene and can send notifications, including an image and the AI's analysis, to a specified Telegram chat.

## Core Features

*   **Visual Change Detection:** Monitors the camera feed for significant visual changes compared to a baseline image.
*   **Adjustable Sensitivity:** Allows users to set a threshold for how much change is needed to trigger an alert.
*   **AI-Powered Image Analysis:** Utilizes Google's Gemini model to analyze captured images based on a user-defined prompt (e.g., "Is there a person in the image?", "Is the door open?").
*   **Telegram Notifications:** Sends alerts to a specified Telegram chat, including the captured image and the AI-generated description or analysis.
*   **Customizable AI Prompts:** Users can tailor the text prompt sent to the AI to focus the analysis on specific aspects of the image.
*   **Settings Interface:** Provides an in-app settings screen to configure the sensitivity threshold, AI prompt, and Telegram Chat ID.
*   **Camera Preview:** Displays the live camera feed within the app.
*   **Start/Stop Control:** Simple controls to activate or deactivate the monitoring process.

## Setup and Configuration

Storog is an Android application built using Gradle.

### Prerequisites

*   Android Studio (latest stable version recommended).
*   An Android device or emulator with camera capabilities.

### API Key Configuration

To use the AI analysis and Telegram notification features, you need to configure the following:

1.  **Google Gemini API Key:**
    *   Obtain an API key from [Google AI Studio](https://aistudio.google.com/app/apikey).
2.  **Telegram Bot Token:**
    *   Create a new Telegram bot by talking to the [BotFather](https://t.me/botfather) on Telegram.
    *   BotFather will provide you with an API token.
3.  **Telegram Chat ID:**
    *   This is the ID of the chat, group, or channel where you want the bot to send notifications.
    *   You can get your personal Chat ID by sending `/myid` to a bot like `@getmyid_bot` on Telegram. For groups or channels, other methods might be needed (e.g., temporarily adding a bot that reveals chat IDs).

**Configuration File:**

*   Once you have the Gemini API Key and Telegram Bot Token, create a file named `my_config.properties` in the `app/src/main/assets/` directory of the project.
*   Add your keys to this file in the following format:

    ```properties
    GEMINI_API_KEY=YOUR_GEMINI_API_KEY_HERE
    MY_BOT_TOKEN=YOUR_TELEGRAM_BOT_TOKEN_HERE
    ```

    **Note:** Replace `YOUR_GEMINI_API_KEY_HERE` and `YOUR_TELEGRAM_BOT_TOKEN_HERE` with your actual keys.

*   **Important:** Add `my_config.properties` to your `.gitignore` file to prevent accidentally committing your API keys to version control.
    ```
    # API keys
    app/src/main/assets/my_config.properties
    ```

**Setting the Target Chat ID:**

*   The Telegram Chat ID is configured within the app itself.
*   Go to the "Настройки" (Settings) screen in the app to enter and save your target Chat ID.

## Basic Usage

1.  **Install the App:** Build and run the application on your Android device or emulator.
2.  **Grant Permissions:** On the first launch, the app will request camera permission. You must grant this permission for the app to function.
3.  **Configure Settings:** 
    *   Navigate to the "Настройки" (Settings) screen.
    *   Enter your **Telegram Chat ID** where you want to receive notifications.
    *   Optionally, you can adjust the **Difference Threshold** (0-100%). A lower value means more sensitivity to changes. The default is 5%.
    *   Optionally, you can customize the **AI Prompt** to guide the image analysis. The default is "есть ли на изображении кошка?" (is there a cat in the image?).
    *   Save your settings.
4.  **Start Monitoring:** 
    *   Return to the main screen.
    *   Tap the "Старт" (Start) button.
    *   The app will capture an initial reference image. Monitoring is now active.
5.  **Monitoring in Action:**
    *   The app will continuously compare the current camera view to the reference image.
    *   The current difference percentage is displayed.
    *   If the difference exceeds your set threshold, the app will:
        *   Send the current image and your AI prompt to the Gemini AI for analysis.
        *   Send a message to your configured Telegram chat, including the image and the AI's response (unless the AI response dictates otherwise, e.g., if the response starts with "Нет" (No), or if a message limit is reached).
6.  **Stop Monitoring:** Tap the "Стоп" (Stop) button to deactivate monitoring.
7.  **Help:** Tap the "Справка" (Help) button for a brief in-app guide.

## Potential Future Improvements / Limitations

*   **More Secure API Key Management:** Instead of a properties file in `assets`, consider using the Android Keystore system or passing keys via Gradle build parameters from a `local.properties` file (which should also be in `.gitignore`).
*   **Background Operation:** Currently, monitoring is active only when the app is in the foreground. A background service could enable continuous monitoring.
*   **Advanced Scheduling:** Allow users to schedule monitoring for specific times or days.
*   **Notification Customization:** More options for notification sounds, vibration, etc.
*   **Multiple Camera/Scene Support:** Ability to configure and switch between different monitored areas or camera perspectives if the device has multiple cameras.
*   **Error Handling and Resilience:** More robust error handling for network issues, API failures, etc.
*   **Internationalization:** Translate UI elements into more languages (currently a mix of English and Russian is present in the source/UI descriptions).
*   **Message Limit:** The app currently has a hardcoded limit of 3 Telegram messages before stopping monitoring to prevent excessive notifications. This could be made configurable or use a more sophisticated rate-limiting approach.
*   **AI Response Parsing:** The logic for skipping Telegram messages (e.g., if AI says "Нет") or stopping monitoring is based on simple string prefixes. This could be made more flexible.

## Building the Project

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/premananda108/storog.git
    cd <repository-directory>
    ```
2.  **Configure API Keys:** Ensure you have set up the `my_config.properties` file in `app/src/main/assets/` as described in the "Setup and Configuration" section.
3.  **Open in Android Studio:** Open the project's root directory in Android Studio.
4.  **Sync Gradle:** Let Android Studio sync the Gradle files and download dependencies.
5.  **Build:** 
    *   You can build the project using the "Build" menu in Android Studio (e.g., "Build" > "Make Project" or "Build" > "Build Bundle(s) / APK(s)").
    *   Alternatively, you can build from the command line using Gradle Wrapper:
        *   For a debug APK: `./gradlew assembleDebug`
        *   For a release APK (requires release signing configuration): `./gradlew assembleRelease`
6.  **Run:** Deploy the app to an Android device or emulator.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

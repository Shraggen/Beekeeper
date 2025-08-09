# **Beekeeper Android App**

This is the Android client for the Beekeeper Journaling App. It allows beekeepers to interact with their hive logs using voice commands, providing a hands-free way to record notes and retrieve information while working.

This project is a fork of the official [Vosk Android Demo](https://github.com/alphacep/vosk-android-demo), adapted and upgraded for this specific use case.

## **Table of Contents**

* [Features](#bookmark=id.gne23h99x2ji)  
* [Prerequisites](#bookmark=id.ym3rzpvu0epi)  
* [Setup](#bookmark=id.qctlh42avniu)  
  * [1\. Clone the Repository](#bookmark=id.eavrm0jutald)  
  * [2\.](#bookmark=id.42xgau13yics) [Download the Vosk Speech Model](#bookmark=id.42xgau13yics)  
  * [3\. Set up Port Forwarding](#bookmark=id.uqg5b2diwet7)  
* [Running the App](#bookmark=id.fin5bztmhcye)  
* [Usage](#bookmark=id.xij2cvdra0j0)  
  * [Wake-Word](#bookmark=id.4be6xbleqmet)  
  * [Voice Commands](#bookmark=id.ogmuc4tftn5q)

## **Features**

* **Voice-Activated**: Control the app using voice commands.  
* **Background Service**: Listens for commands even when the app is in the background.  
* **Log Management**: Create new log entries and read existing ones for any hive.  
* **Text-to-Speech Feedback**: The app provides spoken responses to your commands.

## **Prerequisites**

* **Android Studio**: The latest version is recommended.  
* **Android SDK & NDK**: The project is configured with compileSdk \= 35 and minSdk \= 34\. You must have the corresponding Android SDK Platform installed. The **Android NDK** is also required for the Vosk speech recognition library to function correctly.  
* **Android Device or Emulator**: Running Android API level 34 or higher.  
* **Beekeeper Backend Server**: The Go backend must be running locally for the app to function.

## **Setup**

Follow these steps to set up the Android application.

### **1\. Clone the Repository**

git clone [Beekeeper](https://github.com/Shraggen/Beekeeper)
cd Beekeeper/apps/beekeeperMobile

Then, open the project in Android Studio. It should sync the Gradle files and download all necessary dependencies specified in build.gradle.kts.

### **2\. Download the Vosk Speech Model**

The app uses the Vosk Speech Recognition Toolkit. You need to download a language model for it to work.

1. **Download the model**: Go to the Vosk models page and download the model you wish to use (the app is configured for English by default).  
   * [**Vosk Models Page**](https://alphacephei.com/vosk/models)  
   * For English, download the "Vosk Model English (US)" (or a smaller, more lightweight version if preferred).  
2. **Place the model in the project**:  
   * After downloading, unzip the model folder.  
   * In Android Studio, switch to the **Project** view.  
   * Navigate to models/src/main/assets/.  
   * Create a new directory inside assets named model-en-us (or as specified in BeekeeperService.kt if you changed the language).  
   * Copy the contents of your unzipped model folder into this new model-en-us directory.
   * The full path should look like this: models/src/main/assets/model-en-us/"contents of zip"

### **3\. Set up Port Forwarding**

To allow the Android app (running on a device or emulator) to communicate with the backend server (running on localhost), you need to set up reverse port forwarding using the Android Debug Bridge (ADB).

1. **Ensure your backend server is running** on port 8000\.  
2. **Connect your Android device** to your computer with USB debugging enabled, or start your Android emulator.  
3. **Run the following command** in your terminal:  
   adb reverse tcp:8000 tcp:8000

   This command forwards requests from the device's port 8000 to your computer's port 8000\. You need to run this command every time you unplug and reconnect your device.

## **Running the App**

Once you've completed the setup steps, you can run the app directly from Android Studio:

1. Select your connected device or a virtual device from the toolbar.  
2. Click the **Run 'app'** button (or press Shift \+ F10).

The app will install and start. It will ask for microphone permissions, which are required for the voice commands to work.

## **Usage**

The app runs as a foreground service, which means it can listen for commands even when it's not on the screen. A persistent notification will indicate the service's current state.

### **Wake-Word**

To activate the app's command listener, say the wake-word:

**"Hey Beekeeper"**

The app will respond with "Yes?" to indicate it is ready for a command.

### **Voice Commands**

Here are the primary commands you can use:

* **Create** a new **log entry:**"Entry for beehive \[number\]"  
  *Example: "Entry for beehive twelve"*  
  The app will reply, "Okay, I'm ready to record your entry for beehive 12." You can then dictate your note.  
* **Read the last log entry for a hive:**"Read last note for beehive \[number\]"  
  *Example: "Read last note for beehive five"*  
  The app will read the content of the most recent log for that hive aloud.  
* **Get help:**"Help" or "What can I say"  
  The app will list the available commands.
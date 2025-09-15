# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

#=================================================================#
# START OF BEEKEEPER PROJECT RULES                                #
#=================================================================#

# --- Rules for Vosk's dependency: JNA (Java Native Access) ---
# Your existing rules are correct. We'll add a -dontwarn for java.awt.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    public *;
}
# JNA has optional references to desktop Java AWT classes.
# Since these don't exist on Android, we tell R8 not to treat their absence as an error.
-dontwarn java.awt.**

# --- Rules for MediaPipe LLM Inference ---
# MediaPipe uses Protocol Buffers for its internal data structures.
# R8 must not obfuscate or remove them.
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# MediaPipe uses AutoValue for generating immutable value classes.
-keep class com.google.auto.value.** { *; }

# General rule to keep the MediaPipe tasks library itself safe from obfuscation,
# as it relies on JNI and reflection.
-keep class com.google.mediapipe.** { *; }

# --- Rules for MediaPipe / AutoValue Annotation Processors ---
# The MediaPipe dependencies bundle compile-time code that references
# javax.* packages, which don't exist on Android. We tell R8 not to
# treat their absence as an error.
-dontwarn javax.**
-dontwarn com.google.mediapipe.proto.**

#=================================================================#
# END OF BEEKEEPER PROJECT RULES                                  #
#=================================================================#
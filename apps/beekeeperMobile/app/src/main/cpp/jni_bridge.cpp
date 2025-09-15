// In apps/beekeeperMobile/app/src/main/cpp/jni_bridge.cpp

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.cpp/include/whisper.h"

#define TAG "JNIBridge"

// Helper function to convert Java String to C++ string (no changes here)
std::string jstring_to_string(JNIEnv *env, jstring jstr) {
    if (!jstr) return "";
    const jclass stringClass = env->GetObjectClass(jstr);
    const jmethodID getBytes = env->GetMethodID(stringClass, "getBytes", "(Ljava/lang/String;)[B");
    const jbyteArray stringJbytes = (jbyteArray) env->CallObjectMethod(jstr, getBytes, env->NewStringUTF("UTF-8"));
    size_t length = (size_t) env->GetArrayLength(stringJbytes);
    jbyte* pBytes = env->GetByteArrayElements(stringJbytes, NULL);
    std::string ret = std::string((char *)pBytes, length);
    env->ReleaseByteArrayElements(stringJbytes, pBytes, JNI_ABORT);
    env->DeleteLocalRef(stringJbytes);
    env->DeleteLocalRef(stringClass);
    return ret;
}


extern "C" JNIEXPORT jobject JNICALL
Java_com_bachelorthesis_beekeeperMobile_speechEngine_LibWhisper_getLanguages(
        JNIEnv *env,
        jobject
) {
    // Get references to Java HashMap and its methods
    jclass mapClass = env->FindClass("java/util/HashMap");
    jmethodID mapConstructor = env->GetMethodID(mapClass, "<init>", "()V");
    jmethodID putMethod = env->GetMethodID(mapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    // Create the Java HashMap object
    jobject hashMap = env->NewObject(mapClass, mapConstructor);

    // Get the maximum language ID from the whisper.cpp API
    int n_langs = whisper_lang_max_id() + 1;

    // Loop through all languages and add them to the Java HashMap
    for (int i = 0; i < n_langs; ++i) {
        const char * short_code = whisper_lang_str(i);
        const char * full_name  = whisper_lang_str_full(i);

        // If either short or full name is not available, skip
        if (short_code == nullptr || full_name == nullptr) {
            continue;
        }

        // Convert C++ strings to Java strings
        jstring jKey = env->NewStringUTF(short_code);
        jstring jValue = env->NewStringUTF(full_name);

        // Put the key-value pair into the Java map
        env->CallObjectMethod(hashMap, putMethod, jKey, jValue);

        // Clean up local references to prevent memory leaks
        env->DeleteLocalRef(jKey);
        env->DeleteLocalRef(jValue);
    }

    return hashMap; // Return the created Java map
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_bachelorthesis_beekeeperMobile_speechEngine_LibWhisper_initContext(
        JNIEnv *env,
        jobject /* this */,
        jstring model_path_j) {
    const std::string model_path = jstring_to_string(env, model_path_j);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Initializing Whisper context with model: %s", model_path.c_str());

    // Use default context parameters, VAD is not part of this.
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *context = whisper_init_from_file_with_params(model_path.c_str(), cparams);

    if (context == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to initialize whisper context.");
        return 0;
    }
    return (jlong) context;
}

extern "C" JNIEXPORT void JNICALL
Java_com_bachelorthesis_beekeeperMobile_speechEngine_LibWhisper_releaseContext(
        JNIEnv *env,
        jobject /* this */,
        jlong context_ptr) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "Releasing Whisper context.");
    auto *context = (struct whisper_context *) context_ptr;
    whisper_free(context);
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_bachelorthesis_beekeeperMobile_speechEngine_LibWhisper_transcribe(
        JNIEnv *env,
        jobject,
        jlong context_ptr,
        jint n_threads,
        jfloatArray audio_data,
        jstring vad_model_path_j,
        jstring language) {

    auto *context = (struct whisper_context *) context_ptr;
    if (context == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Transcribe called with null context.");
        return env->NewStringUTF("");
    }

    // Get the float array directly
    jboolean is_copy = JNI_FALSE;
    auto *audio_arr = (jfloat *)env->GetFloatArrayElements(audio_data, &is_copy);
    if (audio_arr == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to get audio data from JNI.");
        return env->NewStringUTF("");
    }
    const jsize audio_len = env->GetArrayLength(audio_data);

    __android_log_print(ANDROID_LOG_INFO, TAG, "Transcribing %d audio samples.", audio_len);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.language = jstring_to_string(env, language).c_str();
    params.n_threads = n_threads;

    //VAD
    params.vad = true;
    params.vad_model_path = jstring_to_string(env, vad_model_path_j).c_str();
    params.vad_params.threshold                = 0.6f;
    params.vad_params.min_silence_duration_ms  = 1000; // Increased silence duration
    params.vad_params.max_speech_duration_s    = 30.0f;
    params.vad_params.speech_pad_ms            = 200;

    if (whisper_full(context, params, audio_arr, audio_len) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to process audio.");
        env->ReleaseFloatArrayElements(audio_data, audio_arr, JNI_ABORT);
        return env->NewStringUTF("");
    }

    env->ReleaseFloatArrayElements(audio_data, audio_arr, JNI_ABORT);

    std::string result_text;
    const int n_segments = whisper_full_n_segments(context);
    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(context, i);
        result_text += text;
    }

    return env->NewStringUTF(result_text.c_str());
}
// In apps/beekeeperMobile/app/src/main/cpp/jni_bridge.cpp

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.cpp/include/whisper.h"

#define TAG "JNIBridge"

// Helper function to convert Java String to C++ string
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

extern "C" JNIEXPORT jlong JNICALL
Java_com_bachelorthesis_beekeeperMobile_speechEngine_LibWhisper_initContext(
        JNIEnv *env,
        jobject /* this */,
        jstring model_path_j) {
    const std::string model_path = jstring_to_string(env, model_path_j);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Initializing Whisper context with model: %s", model_path.c_str());
    struct whisper_context *context = whisper_init_from_file_with_params(model_path.c_str(), whisper_context_default_params());
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
        jobject /* this */,
        jlong context_ptr,
        jfloatArray audio_data) {

    auto *context = (struct whisper_context *) context_ptr;
    if (context == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Transcribe called with null context.");
        return env->NewStringUTF("");
    }

    jfloat *audio_arr = env->GetFloatArrayElements(audio_data, nullptr);
    const jsize audio_len = env->GetArrayLength(audio_data);

    // Use default whisper parameters
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.language = "auto"; // Hardcode to Serbian as per ADR-010, can be parameterized later

    // Run transcription
    if (whisper_full(context, params, audio_arr, audio_len) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to process audio.");
        env->ReleaseFloatArrayElements(audio_data, audio_arr, JNI_ABORT);
        return env->NewStringUTF("");
    }

    env->ReleaseFloatArrayElements(audio_data, audio_arr, JNI_ABORT);

    // Concatenate results
    std::string result_text;
    const int n_segments = whisper_full_n_segments(context);
    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(context, i);
        result_text += text;
    }

    return env->NewStringUTF(result_text.c_str());
}
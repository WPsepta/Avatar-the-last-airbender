#include <jni.h>
#include <android/log.h>
#include <string>

#define LOG_TAG "PS2RecompJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global variables to store resolution and cheat states
static std::string g_resolution = "720p";
static bool g_cheatInfHealth = false;
static bool g_cheatInfChi = false;
static bool g_cheatInfCopper = false;
static bool g_cheatInfStealth = false;
static bool g_cheatDoubleDamage = false;
static bool g_cheatOneHitDishonor = false;

// Getter functions for other C++ code to read
const std::string& getResolution() { return g_resolution; }
bool isCheatEnabled(const std::string& cheatName) {
    if (cheatName == "Infinite Health") return g_cheatInfHealth;
    if (cheatName == "Infinite Chi") return g_cheatInfChi;
    if (cheatName == "Infinite Copper") return g_cheatInfCopper;
    if (cheatName == "Infinite Stealth") return g_cheatInfStealth;
    if (cheatName == "Double Damage") return g_cheatDoubleDamage;
    if (cheatName == "One Hit Dishonor") return g_cheatOneHitDishonor;
    return false;
}

extern "C" {

JNIEXPORT void JNICALL Java_com_avatar_tla_MainActivity_setResolution(JNIEnv* env, jobject thiz, jstring res) {
    const char* resStr = env->GetStringUTFChars(res, nullptr);
    if (resStr) {
        g_resolution = resStr;
        LOGI("Resolution set to %s", resStr);
        env->ReleaseStringUTFChars(res, resStr);
    }
}

JNIEXPORT void JNICALL Java_com_avatar_tla_CheatOverlayService_setCheatState(JNIEnv* env, jobject thiz, jstring cheatName, jboolean isEnabled) {
    const char* name = env->GetStringUTFChars(cheatName, nullptr);
    if (name) {
        std::string cheat(name);
        bool enabled = (isEnabled == JNI_TRUE);
        if (cheat == "Infinite Health") g_cheatInfHealth = enabled;
        else if (cheat == "Infinite Chi") g_cheatInfChi = enabled;
        else if (cheat == "Infinite Copper") g_cheatInfCopper = enabled;
        else if (cheat == "Infinite Stealth") g_cheatInfStealth = enabled;
        else if (cheat == "Double Damage") g_cheatDoubleDamage = enabled;
        else if (cheat == "One Hit Dishonor") g_cheatOneHitDishonor = enabled;
        LOGI("Cheat %s: %s", name, enabled ? "true" : "false");
        env->ReleaseStringUTFChars(cheatName, name);
    }
}

} // extern "C"
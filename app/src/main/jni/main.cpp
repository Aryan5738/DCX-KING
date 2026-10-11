#include <jni.h>
#include <string>
#include <cstring>
#include <cctype>
#include <android/log.h>
#include <curl/curl.h>
#include <openssl/evp.h>
#include <openssl/pem.h>
#include <openssl/rsa.h>
#include <openssl/err.h>
#include <openssl/md5.h>
#include <openssl/aes.h>
#include <json.hpp>
#include <obfuscate.h>
#include "oxorany.h"
#include <openssl/sha.h> 
#define LOG_TAG "SignatureCheck"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using json = nlohmann::ordered_json;

time_t rng = 0;
std::string Enc;
static char ZENINOP[64];
static std::string exdate = oxorany ("NULL");

std::string g_Token, g_Auth;
bool bValid = false;


extern "C"
JNIEXPORT jstring JNICALL
Java_com_bgmi_MAct_exdate(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(exdate.c_str());
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_bgmi_MAct_ZENINOP(JNIEnv *env, jobject activityObject) {
    return env->NewStringUTF(ZENINOP);
}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_bgmi_LogAct_GetKey(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF(oxorany("https://t.me/PRIVATE_SRC_FILES")); // Link Channel
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_bgmi_utils_Downtwo_Version(JNIEnv *env, jclass clazz) {
    // return URL to version file
    std::string versionUrl = oxorany("https://github.com/BGMILITEOP558/Op098/releases/download/Op773/version.txt");
    return env->NewStringUTF(versionUrl.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_bgmi_utils_Downtwo_Link(JNIEnv *env, jclass clazz) {
    std::string downloadUrl = oxorany("https://github.com/BGMILITEOP558/Op098/releases/download/Op773/PRIVATE_SRC_FILES.zip");
    return env->NewStringUTF(downloadUrl.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_bgmi_BoxApplication_getSdkKey(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(oxorany("PRIVATE_SRC_FILES_OWNER"));//sdk key Don't Change
}


// Expected SHA-256 signature
static const char *EXPECTED_SIGNATURE ="77f05d53ce8bf1855caef38ce87f13a8bb2b1b2cdd2d48da9d3ba897eac4549e";

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_bgmi_LogAct_nativeVerifySignature(JNIEnv *env, jobject thiz, jobject context) {
    jclass contextClass = env->GetObjectClass(context);

    // Get PackageManager
    jmethodID midGetPM = env->GetMethodID(contextClass, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jobject pm = env->CallObjectMethod(context, midGetPM);

    // Get package name
    jmethodID midGetPkg = env->GetMethodID(contextClass, "getPackageName", "()Ljava/lang/String;");
    jstring pkgName = (jstring) env->CallObjectMethod(context, midGetPkg);

    // Get package info with signatures (flag = 64)
    jclass pmClass = env->GetObjectClass(pm);
    jmethodID midGetInfo = env->GetMethodID(pmClass, "getPackageInfo",
                                            "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    jobject pkgInfo = env->CallObjectMethod(pm, midGetInfo, pkgName, 64);

    // Get signatures[]
    jclass pkgInfoClass = env->GetObjectClass(pkgInfo);
    jfieldID fidSignatures = env->GetFieldID(pkgInfoClass, "signatures", "[Landroid/content/pm/Signature;");
    jobjectArray sigArray = (jobjectArray) env->GetObjectField(pkgInfo, fidSignatures);

    if (sigArray == nullptr) return JNI_FALSE;

    jsize sigCount = env->GetArrayLength(sigArray);
    for (jsize i = 0; i < sigCount; i++) {
        jobject sig = env->GetObjectArrayElement(sigArray, i);

        jclass sigClass = env->GetObjectClass(sig);
        jmethodID midToBytes = env->GetMethodID(sigClass, "toByteArray", "()[B");
        jbyteArray sigBytes = (jbyteArray) env->CallObjectMethod(sig, midToBytes);

        jsize len = env->GetArrayLength(sigBytes);
        jbyte *buf = env->GetByteArrayElements(sigBytes, nullptr);

        // Hash with SHA-256
        unsigned char hash[SHA256_DIGEST_LENGTH];
        SHA256((unsigned char *) buf, len, hash);

        env->ReleaseByteArrayElements(sigBytes, buf, 0);

        // Convert hash to hex string
        char hexHash[SHA256_DIGEST_LENGTH * 2 + 1];
        for (int j = 0; j < SHA256_DIGEST_LENGTH; j++) {
            sprintf(&hexHash[j * 2], "%02x", hash[j]);
        }
        hexHash[SHA256_DIGEST_LENGTH * 2] = '\0';

        if (strcasecmp(hexHash, EXPECTED_SIGNATURE) == 0) {
            return JNI_TRUE;
        } else {
            LOGE("Invalid signature: %s", hexHash);
        }
    }
    return JNI_FALSE;
}

const char *GetAndroidID(JNIEnv *env, jobject context) {
    jclass contextClass = env->FindClass("android/content/Context");
    jmethodID getContentResolverMethod = env->GetMethodID(contextClass,"getContentResolver","()Landroid/content/ContentResolver;");
    jclass settingSecureClass = env->FindClass("android/provider/Settings$Secure");
    jmethodID getStringMethod = env->GetStaticMethodID(settingSecureClass,"getString", "(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;");

    auto obj = env->CallObjectMethod(context, getContentResolverMethod);
    auto str = (jstring) env->CallStaticObjectMethod(settingSecureClass, getStringMethod, obj,env->NewStringUTF("android_id"));
    return env->GetStringUTFChars(str, 0);
}

const char *GetDeviceModel(JNIEnv *env) {
    jclass buildClass = env->FindClass("android/os/Build");
    jfieldID modelId = env->GetStaticFieldID(buildClass, "MODEL","Ljava/lang/String;");

    auto str = (jstring) env->GetStaticObjectField(buildClass, modelId);
    return env->GetStringUTFChars(str, 0);
}

const char *GetDeviceBrand(JNIEnv *env) {
    jclass buildClass = env->FindClass("android/os/Build");
    jfieldID modelId = env->GetStaticFieldID(buildClass, "BRAND","Ljava/lang/String;");

    auto str = (jstring) env->GetStaticObjectField(buildClass, modelId);
    return env->GetStringUTFChars(str, 0);
}

const char *GetPackageName(JNIEnv *env, jobject context) {
    jclass contextClass = env->FindClass("android/content/Context");
    jmethodID getPackageNameId = env->GetMethodID(contextClass, "getPackageName","()Ljava/lang/String;");

    auto str = (jstring) env->CallObjectMethod(context, getPackageNameId);
    return env->GetStringUTFChars(str, 0);
}

const char *GetDeviceUniqueIdentifier(JNIEnv *env, const char *uuid) {
    jclass uuidClass = env->FindClass("java/util/UUID");

    auto len = strlen(uuid);

    jbyteArray myJByteArray = env->NewByteArray(len);
    env->SetByteArrayRegion(myJByteArray, 0, len, (jbyte *) uuid);

    jmethodID nameUUIDFromBytesMethod = env->GetStaticMethodID(uuidClass,"nameUUIDFromBytes","([B)Ljava/util/UUID;");
    jmethodID toStringMethod = env->GetMethodID(uuidClass, "toString","()Ljava/lang/String;");

    auto obj = env->CallStaticObjectMethod(uuidClass, nameUUIDFromBytesMethod, myJByteArray);
    auto str = (jstring) env->CallObjectMethod(obj, toStringMethod);
    return env->GetStringUTFChars(str, 0);
}

struct MemoryStruct {
    char *memory;
    size_t size;
};

static size_t WriteMemoryCallback(void *contents, size_t size, size_t nmemb, void *userp) {
    size_t realsize = size * nmemb;
    struct MemoryStruct *mem = (struct MemoryStruct *) userp;

    mem->memory = (char *) realloc(mem->memory, mem->size + realsize + 1);
    if (mem->memory == NULL) {
        return 0;
    }

    memcpy(&(mem->memory[mem->size]), contents, realsize);
    mem->size += realsize;
    mem->memory[mem->size] = 0;

    return realsize;
}
std::string CalcMD5(std::string s) {
    std::string result;

    unsigned char hash[MD5_DIGEST_LENGTH];
    char tmp[4];

    MD5_CTX md5;
    MD5_Init(&md5);
    MD5_Update(&md5, s.c_str(), s.length());
    MD5_Final(hash, &md5);
    for (unsigned char i : hash) {
        sprintf(tmp, "%02x", i);
        result += tmp;
    }
    return result;
}

std::string CalcSHA256(std::string s) {
    std::string result;

    unsigned char hash[SHA256_DIGEST_LENGTH];
    char tmp[4];

    SHA256_CTX sha256;
    SHA256_Init(&sha256);
    SHA256_Update(&sha256, s.c_str(), s.length());
    SHA256_Final(hash, &sha256);
    for (unsigned char i : hash) {
        sprintf(tmp, "%02x", i);
        result += tmp;
    }
    return result;
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_bgmi_LogAct_Check(JNIEnv *env, jclass clazz, jobject mContext, jstring mUserKey) {
    bValid = false;

    auto user_key = env->GetStringUTFChars(mUserKey, 0);
    // DCX KING - Local master key bypass (offline, no panel needed)
    if (user_key != nullptr && (strcmp(user_key, "DCX_OK") == 0 || strcmp(user_key, "dcx_ok") == 0 ||
        strcmp(user_key, "Dcx_Ok") == 0 || strcmp(user_key, "DCX_ok") == 0)) {
        exdate = oxorany("VIP Lifetime");
        env->ReleaseStringUTFChars(mUserKey, user_key);
        return env->NewStringUTF("OK");
    }
    // Case-insensitive exact check for any variation
    {
        std::string k = user_key ? user_key : "";
        for (auto &c : k) c = toupper(c);
        if (k == "DCX_OK") {
            exdate = oxorany("VIP Lifetime");
            env->ReleaseStringUTFChars(mUserKey, user_key);
            return env->NewStringUTF("OK");
        }
    }
    std::string hwid = user_key;
    hwid += GetAndroidID(env, mContext);
    hwid += GetDeviceModel(env);
    hwid += GetDeviceBrand(env);
    std::string UUID = GetDeviceUniqueIdentifier(env, hwid.c_str());
    std::string errMsg = "Unknown error";

    struct MemoryStruct chunk{};
    chunk.memory = (char *) malloc(1);
    if (chunk.memory) {
        chunk.memory[0] = '\0';
    }
    chunk.size = 0;

    CURL *curl;
    CURLcode res;
    curl = curl_easy_init();
    if (curl) {
        std::string panelUrl = oxorany("https://connect");//Apna Pannel Yha Per Dalo
        curl_easy_setopt(curl, CURLOPT_URL, panelUrl.c_str());
        curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, "POST");
        curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
        curl_easy_setopt(curl, CURLOPT_POSTREDIR, CURL_REDIR_POST_ALL);
        curl_easy_setopt(curl, CURLOPT_DEFAULT_PROTOCOL, "https");

        struct curl_slist *headers = NULL;
        headers = curl_slist_append(headers, "Accept: application/json");
        headers = curl_slist_append(headers, "Content-Type: application/x-www-form-urlencoded");
        headers = curl_slist_append(headers, "Charset: UTF-8");
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);

        char data[4096];
        snprintf(data, sizeof(data), "game=PUBG&user_key=%s&serial=%s", user_key, UUID.c_str());
        curl_easy_setopt(curl, CURLOPT_POST, 1L);
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, data);
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteMemoryCallback);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, (void *) &chunk);
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYSTATUS, 0L);
        curl_easy_setopt(curl, CURLOPT_USERAGENT, "Mozilla/5.0 (Linux; Android 10; Mobile)");
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 15L);
        curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT, 10L);

        res = curl_easy_perform(curl);
        if (headers) {
            curl_slist_free_all(headers);
        }

        if (res == CURLE_OK) {
            long http_code = 0;
            curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);
            if (http_code != 200) {
                errMsg = "Server returned error (HTTP " + std::to_string(http_code) + ")";
            } else if (chunk.size == 0 || chunk.memory == nullptr) {
                errMsg = "Empty response from server";
            } else {
                const char *json_str = chunk.memory;
                while (*json_str && (*json_str == ' ' || *json_str == '\t' || *json_str == '\r' || *json_str == '\n')) {
                    json_str++;
                }

                if (*json_str != '{' && *json_str != '[') {
                    errMsg = "Invalid server response (non-JSON)";
                } else {
                    try {
                        json result = json::parse(json_str);
                        bool is_success = false;
                        if (result.contains("status")) {
                            if (result["status"].is_boolean()) {
                                is_success = result["status"].get<bool>();
                            } else if (result["status"].is_string()) {
                                std::string s = result["status"].get<std::string>();
                                is_success = (s == "true" || s == "1" || s == "success" || s == "ok");
                            } else if (result["status"].is_number()) {
                                is_success = (result["status"].get<int>() == 1);
                            }
                        }

                        if (is_success) {
                            std::string token;
                            if (result.contains("data") && result["data"].is_object()) {
                                auto &d = result["data"];
                                if (d.contains("token") && d["token"].is_string()) {
                                    token = d["token"].get<std::string>();
                                }
                                if (d.contains("Enc") && d["Enc"].is_string()) {
                                    Enc = d["Enc"].get<std::string>();
                                }
                                if (d.contains("EXP") && d["EXP"].is_string()) {
                                    exdate = d["EXP"].get<std::string>();
                                }
                                if (d.contains("rng")) {
                                    if (d["rng"].is_number()) {
                                        rng = d["rng"].get<time_t>();
                                    } else if (d["rng"].is_string()) {
                                        try {
                                            rng = std::stol(d["rng"].get<std::string>());
                                        } catch (...) {}
                                    }
                                }
                            }

                            std::string auth = "PUBG";
                            auth += "-";
                            auth += user_key;
                            auth += "-";
                            auth += UUID;
                            auth += "-";
                            std::string license = oxorany("Vm8Lk7Uj2JmsjCPVPVjrLa7zgfx3uz9E");
                            auth += license.c_str();
                            std::string outputAuth = CalcMD5(auth);
                            g_Token = token;
                            g_Auth = outputAuth;
                            bValid = (g_Token == g_Auth);
                            if (bValid) {
                                if (!Enc.empty()) {
                                    strncpy(ZENINOP, Enc.c_str(), sizeof(ZENINOP) - 1);
                                    ZENINOP[sizeof(ZENINOP) - 1] = '\0';
                                }
                                printf(oxorany("Login Success \n"));
                            } else {
                                errMsg = "Authentication failed (token mismatch)";
                            }
                        } else {
                            if (result.contains("reason") && result["reason"].is_string()) {
                                errMsg = result["reason"].get<std::string>();
                            } else if (result.contains("message") && result["message"].is_string()) {
                                errMsg = result["message"].get<std::string>();
                            } else if (result.contains("msg") && result["msg"].is_string()) {
                                errMsg = result["msg"].get<std::string>();
                            } else {
                                errMsg = "Key not registered or expired";
                            }
                        }
                    } catch (const json::exception &e) {
                        errMsg = std::string("JSON error: ") + e.what();
                    }
                }
            }
        } else {
            errMsg = std::string("Connection error: ") + curl_easy_strerror(res);
        }
        curl_easy_cleanup(curl);
    } else {
        errMsg = "Failed to initialize network client";
    }

    if (chunk.memory) {
        free(chunk.memory);
    }
    env->ReleaseStringUTFChars(mUserKey, user_key);

    return bValid ? env->NewStringUTF("OK") : env->NewStringUTF(errMsg.c_str());
}
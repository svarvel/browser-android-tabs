/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
 #include "shields_config.h"
 #include "base/android/jni_android.h"
 #include "base/android/jni_string.h"
 #include "jni/ShieldsConfig_jni.h"
 #include "net/base/registry_controlled_domains/registry_controlled_domain.h"

namespace net {
namespace blockers {

ShieldsConfig* gShieldsConfig = nullptr;

ShieldsConfig::ShieldsConfig(JNIEnv* env, jobject obj):
  weak_java_shields_config_(env, obj) {
}

ShieldsConfig::~ShieldsConfig() {
}

std::string ShieldsConfig::getHostSettings(const bool &incognitoTab, const std::string& host) {
  JNIEnv* env = base::android::AttachCurrentThread();
  base::android::ScopedJavaLocalRef<jstring> jhost(base::android::ConvertUTF8ToJavaString(env, host));
  return base::android::ConvertJavaStringToUTF8(
    Java_ShieldsConfig_getHostSettings(env, weak_java_shields_config_.get(env),
    incognitoTab, jhost));
}

void ShieldsConfig::setBlockedCountInfo(const std::string& url, int trackersBlocked, int adsBlocked, int httpsUpgrades,
        int scriptsBlocked, int fingerprintingBlocked) {
  JNIEnv* env = base::android::AttachCurrentThread();
  base::android::ScopedJavaLocalRef<jstring> jurl(base::android::ConvertUTF8ToJavaString(env, url));
  Java_ShieldsConfig_setBlockedCountInfo(env, weak_java_shields_config_.get(env),
    jurl, trackersBlocked, adsBlocked, httpsUpgrades, scriptsBlocked, fingerprintingBlocked);
}

bool ShieldsConfig::needUpdateAdBlocker() {
  JNIEnv* env = base::android::AttachCurrentThread();

  return Java_ShieldsConfig_needUpdateAdBlocker(env, weak_java_shields_config_.get(env));
}

void ShieldsConfig::resetUpdateAdBlockerFlag() {
  JNIEnv* env = base::android::AttachCurrentThread();
  Java_ShieldsConfig_resetUpdateAdBlockerFlag(env, weak_java_shields_config_.get(env));
}

ShieldsConfig* ShieldsConfig::getShieldsConfig() {
    return gShieldsConfig;
}

bool ShieldsConfig::shouldSetReferrer(bool allow_referrers, bool shields_up,
    const GURL& original_referrer, const GURL& tab_origin,
    const GURL& target_url, const GURL& new_referrer_url,
    blink::WebReferrerPolicy policy, content::Referrer *output_referrer) {
  if (!output_referrer ||
      allow_referrers ||
      !shields_up ||
      original_referrer.is_empty() ||
      net::registry_controlled_domains::SameDomainOrHost(target_url, original_referrer,
          net::registry_controlled_domains::INCLUDE_PRIVATE_REGISTRIES)) {
    return false;
  }
  *output_referrer = content::Referrer::SanitizeForRequest(target_url,
      content::Referrer(new_referrer_url, policy));
  return true;
}

static void JNI_ShieldsConfig_Clear(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj) {
    delete gShieldsConfig;
    gShieldsConfig = nullptr;
}

static void JNI_ShieldsConfig_Init(JNIEnv* env, const base::android::JavaParamRef<jobject>& obj) {
  // This will automatically bind to the Java object and pass ownership there.
  gShieldsConfig = new ShieldsConfig(env, obj);
}

// static
/*bool ShieldsConfig::RegisterShieldsConfig(JNIEnv* env) {
  return RegisterNativesImpl(env);
}*/

}
}

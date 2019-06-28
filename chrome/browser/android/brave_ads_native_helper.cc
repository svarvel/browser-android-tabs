#include "base/android/jni_android.h"
#include "base/android/jni_string.h"
#include "chrome/browser/profiles/profile_android.h"
#include "brave/components/brave_ads/browser/ads_service.h"
#include "brave/components/brave_ads/browser/ads_service_factory.h"
#include "chrome/browser/profiles/profile.h"
#include "jni/BraveAdsNativeHelper_jni.h"

using base::android::JavaParamRef;
using base::android::ScopedJavaLocalRef;

namespace chrome {

namespace android {

// static

jboolean JNI_BraveAdsNativeHelper_IsBraveAdsEnabled(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& j_profile_android) {
  Profile* profile = ProfileAndroid::FromProfileAndroid(j_profile_android);
  auto* ads_service_ = brave_ads::AdsServiceFactory::GetForProfile(profile);
  if (!ads_service_) {
    return false;
  }

  return ads_service_->IsAdsEnabled();
}

jboolean JNI_BraveAdsNativeHelper_IsLocaleValid(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& j_profile_android) {
  Profile* profile = ProfileAndroid::FromProfileAndroid(j_profile_android);
  auto* ads_service_ = brave_ads::AdsServiceFactory::GetForProfile(profile);
  if (!ads_service_) {
    return false;
  }

  return ads_service_->IsSupportedRegion();
}

void JNI_BraveAdsNativeHelper_SetAdsEnabled(
    JNIEnv* env,
    const base::android::JavaParamRef<jobject>& j_profile_android) {
  Profile* profile = ProfileAndroid::FromProfileAndroid(j_profile_android);
  auto* ads_service_ = brave_ads::AdsServiceFactory::GetForProfile(profile);
  if (!ads_service_) {
    return;
  }

  ads_service_->SetAdsEnabled(true);
}

}  // namespace android

}  // namespace chrome

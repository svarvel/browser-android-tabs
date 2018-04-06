 #include "stats_updater.h"
 #include "base/android/jni_android.h"
 #include "base/android/jni_string.h"
 #include "jni/StatsUpdater_jni.h"

namespace stats_updater {

std::string GetCustomHeadersForHost(const std::string& host) {
  JNIEnv* env = base::android::AttachCurrentThread();
  base::android::ScopedJavaLocalRef<jstring> jhost(base::android::ConvertUTF8ToJavaString(env, host));
  return base::android::ConvertJavaStringToUTF8(Java_StatsUpdater_GetCustomHeadersForHost(env, jhost));
}

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

#ifndef BRAVE_SYNC_STORAGE_H_
#define BRAVE_SYNC_STORAGE_H_

#include <jni.h>
#include "../../../../base/android/jni_weak_ref.h"

namespace brave_sync_storage {

class BraveSyncWorker {
public:
    BraveSyncWorker(JNIEnv* env, jobject obj);
    ~BraveSyncWorker();

    // Register the BraveSyncStorage's native methods through JNI.
    static bool RegisterBraveSyncStorage(JNIEnv* env);

private:
    JavaObjectWeakGlobalRef weak_java_shields_config_;
};
}

#endif //BRAVE_SYNC_STORAGE_H_

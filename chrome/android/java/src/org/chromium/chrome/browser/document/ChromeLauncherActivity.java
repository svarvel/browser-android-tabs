// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.document;

import android.app.Activity;
import android.os.Bundle;
import android.os.StrictMode;

import org.chromium.base.ApiCompatibilityUtils;
import org.chromium.base.TraceEvent;
import org.chromium.chrome.browser.LaunchIntentDispatcher;
import org.chromium.chrome.browser.vr.VrModuleProvider;


// [MV] extra import needed to enable writing to setting option 
// required to manipulate screen settings 
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.app.Activity;
import android.provider.Settings;
import android.util.Log;
import android.os.Build;

/**
 * Dispatches incoming intents to the appropriate activity based on the current configuration and
 * Intent fired.
 */
public class ChromeLauncherActivity extends Activity {

    // [MV] tag used for logging 
    private String TAG = "MATTEO";
    private int SDK_INT;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Third-party code adds disk access to Activity.onCreate. http://crbug.com/619824
        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        TraceEvent.begin("ChromeLauncherActivity.onCreate");
        try {
            super.onCreate(savedInstanceState);

            // [MV] request settings write permission            
            // get SDK info
            SDK_INT = (Build.VERSION.SDK_INT == 25 && Build.VERSION.CODENAME.charAt(0) == 'O') ? 26 : Build.VERSION.SDK_INT;
            Log.d(TAG, "SDK: " + SDK_INT); 

            // deal with settings write permission
            boolean status = maybeRequestPermission(this);
            Log.d(TAG, "Request permission status: " + status);
            ////////////

            if (VrModuleProvider.getIntentDelegate().isVrIntent(getIntent())) {
                // We need to turn VR mode on as early as possible in the intent handling flow to
                // avoid brightness flickering when handling VR intents.
                VrModuleProvider.getDelegate().setVrModeEnabled(this, true);
            }

            @LaunchIntentDispatcher.Action
            int dispatchAction = LaunchIntentDispatcher.dispatch(this, getIntent());
            switch (dispatchAction) {
                case LaunchIntentDispatcher.Action.FINISH_ACTIVITY:
                    finish();
                    break;
                case LaunchIntentDispatcher.Action.FINISH_ACTIVITY_REMOVE_TASK:
                    ApiCompatibilityUtils.finishAndRemoveTask(this);
                    break;
                default:
                    assert false : "Intent dispatcher finished with action " + dispatchAction
                                   + ", finishing anyway";
                    finish();
                    break;
            }
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
            TraceEvent.end("ChromeLauncherActivity.onCreate");
        }
    }

    // [MV] handle request for writing permission 
    //  Q: better spot where to do this? 
    //  Q: permission acceptance does not become visible 
    public boolean maybeRequestPermission(Activity activity) {
        
        // nothing to do for old SDKs
        if (SDK_INT < 23) {
            Log.d(TAG, "SDK < 23" + SDK_INT);
            return false;
        }
        else if (Settings.System.canWrite(this)) {
            Log.d(TAG, "Permission WRITE_SETTINGS already accepted"); 
            return true;
        } else {
            Log.d(TAG, "Permission WRITE_SETTINGS missing. Asking to accept");
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + getApplicationInfo().packageName));
            startActivity(intent);
            return true;
        }
    }
}

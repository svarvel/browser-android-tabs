// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.upgrade;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.chromium.base.Log;
import org.chromium.base.task.AsyncTask;
import org.chromium.base.task.BackgroundOnlyAsyncTask;
import org.chromium.chrome.browser.init.StatsUpdater;
import org.chromium.chrome.browser.notifications.channels.ChannelsUpdater;
import org.chromium.chrome.browser.vr.VrModuleProvider;

/**
 * Triggered when Chrome's package is replaced (e.g. when it is upgraded).
 *
 * Before changing this class, you must understand both the Receiver and Process Lifecycles:
 * http://developer.android.com/reference/android/content/BroadcastReceiver.html#ReceiverLifecycle
 *
 * - This process runs in the foreground as long as {@link #onReceive} is running.  If there are no
 *   other application components running, Android will aggressively kill it.
 *
 * - Because this runs in the foreground, don't add any code that could cause jank or ANRs.
 *
 * - This class immediately cullable by Android as soon as {@link #onReceive} returns. To kick off
 *   longer tasks, you must start a Service.
 */
public final class PackageReplacedBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        updateChannelsIfNecessary(context);
        VrModuleProvider.maybeRequestModuleIfDaydreamReady();
        //try {
        //    NotificationIntent.fireNotificationIfNecessary(context);
        //} catch (Exception exc) {
            // Just ignore if we could not send a notification
        //    Log.i("TAG", "notification error " + exc.getMessage());
        //}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return;
        UpgradeIntentService.startMigrationIfNecessary(context);
    }

    private void updateChannelsIfNecessary(final Context context) {
        if (!ChannelsUpdater.getInstance().shouldUpdateChannels()) {
            //StatTask(context);

            return;
        }

        /*final PendingResult result = goAsync();
        new BackgroundOnlyAsyncTask<Void>() {
            @Override
            protected Void doInBackground() {
                ChannelsUpdater.getInstance().updateChannels();
                try {
                    StatsUpdater.UpdateStats(context);
                } catch (Exception exc) {
                    // Just ignore if we could not update stats
                    Log.e("TAG", "update stats error " + exc.getMessage());
                } catch (UnsatisfiedLinkError exc) {
                    // Just ignore if we could not update stats
                    Log.e("TAG", "update stats error " + exc.getMessage());
                }
                result.finish();
                return null;
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);*/
    }

    private void StatTask(final Context context) {
        final PendingResult result = goAsync();
        new AsyncTask<Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    StatsUpdater.UpdateStats(context);
                } catch (Exception exc) {
                    // Just ignore if we could not update stats
                    Log.e("TAG", "update stats error " + exc.getMessage());
                } catch (UnsatisfiedLinkError exc) {
                    // Just ignore if we could not update stats
                    Log.e("TAG", "update stats error " + exc.getMessage());
                }
                result.finish();
                return null;
            }
        }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }
}

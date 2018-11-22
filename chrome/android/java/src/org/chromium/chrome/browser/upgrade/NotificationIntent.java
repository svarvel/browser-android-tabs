/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.upgrade;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.view.View;

import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.R;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Fires notification about new features.
 */
public class NotificationIntent {
    private static final String TAG = "NotificationIntent";
    private static final String NOTIFICATION_CHANNEL_ID = "notification_update_channel";
    private static final String PREF_NAME = "NotificationUpdateTimeStampPreferences";
    private static final String MILLISECONDS_NAME = "Milliseconds";
    private static final String URL = "https://brave.com/new-brave-22-percent-faster/";
    private static final List<String> mWhitelistedRegionalLocales = Arrays.asList("en", "ru", "uk", "be", "pt", "fr");
    //private static final String NOTIFICATION_TITLE = "Brave update";
    //private static final String NOTIFICATION_TEXT = "The new Brave browser is 22% faster";

    public static void fireNotificationIfNecessary(Context context) {
        Log.i("TAG", "!!!fireNotificationIfNecessary begin");
        String notification_text = String.format(context.getString(R.string.update_notification_text),
                                     "22%");
        Log.i(TAG, "!!!title == " + context.getString(R.string.update_notification_title));
        Log.i(TAG, "!!!text == " + notification_text);
        if (!ShouldNotify(context)) {
            Log.i(TAG, "!!!ShouldNotify == false");
            return;
        }
        NotificationManager mNotificationManager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.i("TAG", "!!!before set channel");
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    "Channel for notifications",
                    NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationManager.createNotificationChannel(channel);
            Log.i("TAG", "!!!after set channel");
        }

        NotificationCompat.Builder mBuilder =
            new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);

        //Create the intent that’ll fire when the user taps the notification//
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(URL));
        intent.setPackage(context.getPackageName());
        Log.i(TAG, "!!!sendNotification packageName == " + context.getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        mBuilder.setContentIntent(pendingIntent);
        mBuilder.setAutoCancel(true);

        mBuilder.setSmallIcon(R.drawable.ic_chrome);
        mBuilder.setContentTitle(context.getString(R.string.update_notification_title));
        mBuilder.setContentText(notification_text);

        mNotificationManager.notify(001, mBuilder.build());
        SetPreferences(context);
        Log.i("TAG", "!!!fireNotificationIfNecessary end");
    }

    public static boolean ShouldNotify(Context context) {
        String deviceLanguage = Locale.getDefault().getLanguage();
        Log.i(TAG, "!!!deviceLanguage == " + deviceLanguage);
        if (GetPreferences(context) != 0
              || !mWhitelistedRegionalLocales.contains(new Locale(deviceLanguage).getLanguage())) {
            return false;
        }
        Log.i(TAG, "!!!ShouldNotify == true");

        return true;
    }

    public static void SetPreferences(Context context) {
        Calendar currentTime = Calendar.getInstance();
        long milliSeconds = currentTime.getTimeInMillis();

        SharedPreferences sharedPref = context.getSharedPreferences(PREF_NAME, 0);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putLong(MILLISECONDS_NAME, milliSeconds);
        editor.apply();
    }

    public static long GetPreferences(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREF_NAME, 0);

        return sharedPref.getLong(MILLISECONDS_NAME, 0);
    }
}
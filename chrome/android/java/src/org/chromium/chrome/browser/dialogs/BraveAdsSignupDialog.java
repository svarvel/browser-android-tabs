/** Copyright (c) 2019 The Brave Authors. All rights reserved.
  * This Source Code Form is subject to the terms of the Mozilla Public
  * License, v. 2.0. If a copy of the MPL was not distributed with this file,
  * You can obtain one at http://mozilla.org/MPL/2.0/.
  */

package org.chromium.chrome.browser.dialogs;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.widget.ImageView;
import android.view.View;
import java.lang.System;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.BraveAdsNativeHelper;
import org.chromium.chrome.browser.BraveRewardsNativeWorker;
import org.chromium.chrome.browser.BraveRewardsPanelPopup;
import org.chromium.chrome.browser.notifications.BraveAdsOobeEducationNotification;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.util.PackageUtils;

public class BraveAdsSignupDialog {

    private static String SHOULD_SHOW_DIALOG_COUNTER = "should_show_dialog_counter";
    private static final long TWENTY_FOUR_HOURS = 86_400_000;
    private static final long MOMENT_LATER = 2_500;

    public static boolean shouldShowNewUserDialog(Context context) {
        boolean shouldShow =
          PackageUtils.isFirstInstall(context)
          && !BraveAdsNativeHelper.nativeIsBraveAdsEnabled(Profile.getLastUsedProfile())
          && hasElapsed24Hours(context)
          && BraveAdsNativeHelper.nativeIsLocaleValid(Profile.getLastUsedProfile());

        boolean shouldShowForViewCount = shouldShowForViewCount();
        if (shouldShow) updateViewCount();

        return shouldShow && shouldShowForViewCount;
    }

    public static boolean shouldShowForUserWhoNeverTurnedOnRewards(Context context) {
        boolean shouldShow =
          !PackageUtils.isFirstInstall(context)
          && !BraveAdsNativeHelper.nativeIsBraveAdsEnabled(Profile.getLastUsedProfile())
          && !BraveRewardsPanelPopup.wasBraveRewardsExplicitlyTurnedOff()
          && BraveAdsNativeHelper.nativeIsLocaleValid(Profile.getLastUsedProfile());
        boolean shouldShowForViewCount = shouldShowForViewCount();
        if (shouldShow) updateViewCount();

        return shouldShow && shouldShowForViewCount;
    }

    public static boolean shouldShowExistingUserDialog(Context context) {
        boolean shouldShow =
          !PackageUtils.isFirstInstall(context)
          && (!BraveAdsNativeHelper.nativeIsBraveAdsEnabled(Profile.getLastUsedProfile())
          && BraveRewardsPanelPopup.isBraveRewardsEnabled())
          && BraveAdsNativeHelper.nativeIsLocaleValid(Profile.getLastUsedProfile());

        boolean shouldShowForViewCount = shouldShowForViewCount();
        if (shouldShow) updateViewCount();

        return shouldShow && shouldShowForViewCount;
    }

    @CalledByNative
    public static void enqueueOobeNotificationNative() {
        enqueueOobeNotification(ContextUtils.getApplicationContext());
    }

    private static void enqueueOobeNotification(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, BraveAdsOobeEducationNotification.class);
        am.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + MOMENT_LATER,
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        );
    }

    public static void showNewUserDialog(Context context) {
        AlertDialog alertDialog = new AlertDialog.Builder(context, R.style.BraveDialogTheme)
        .setView(R.layout.brave_ads_new_user_dialog_layout)
        .setPositiveButton(R.string.brave_ads_offer_positive, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BraveRewardsNativeWorker braveRewardsNativeWorker = BraveRewardsNativeWorker.getInstance();
                braveRewardsNativeWorker.GetRewardsMainEnabled();
                braveRewardsNativeWorker.CreateWallet();

                // Enable ads
                BraveAdsNativeHelper.nativeSetAdsEnabled(Profile.getLastUsedProfile());
            }
        }).create();
        alertDialog.show();

        ImageView closeButton = alertDialog.findViewById(R.id.close_button);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.cancel();
            }
        });
    }

    public static void showExistingUserDialog(Context context) {
        AlertDialog alertDialog = new AlertDialog.Builder(context, R.style.BraveDialogTheme)
        .setView(R.layout.brave_ads_existing_user_dialog_layout)
        .setPositiveButton(R.string.brave_ads_offer_positive, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Enable ads
                BraveAdsNativeHelper.nativeSetAdsEnabled(Profile.getLastUsedProfile());
            }
        }).create();
        alertDialog.show();

        ImageView closeButton = alertDialog.findViewById(R.id.close_button);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.cancel();
            }
        });
    }

    private static boolean hasElapsed24Hours(Context context) {
        boolean result = false;
        try {
            result = System.currentTimeMillis() >= context.getPackageManager().getPackageInfo(context.getPackageName(), 0).firstInstallTime + TWENTY_FOUR_HOURS;
        } catch (NameNotFoundException e) {}
        return result;
    }

    private static boolean shouldShowForViewCount() {
        SharedPreferences sharedPref = ContextUtils.getAppSharedPreferences();
        int viewCount = sharedPref.getInt(SHOULD_SHOW_DIALOG_COUNTER, 0);
        return 0 == viewCount || 20 == viewCount || 40 == viewCount;
    }

    private static void updateViewCount() {
        SharedPreferences sharedPref = ContextUtils.getAppSharedPreferences();
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(SHOULD_SHOW_DIALOG_COUNTER, sharedPref.getInt(SHOULD_SHOW_DIALOG_COUNTER, 0) + 1);
        editor.apply();
    }
}

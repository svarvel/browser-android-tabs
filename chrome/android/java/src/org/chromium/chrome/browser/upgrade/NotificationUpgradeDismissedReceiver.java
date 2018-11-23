/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser.upgrade;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.chromium.base.Log;
import org.chromium.chrome.browser.util.IntentUtils;

import java.util.Calendar;

public class NotificationUpgradeDismissedReceiver extends BroadcastReceiver {
  private static final int NOTIFICATION_ID = 632;
  private static final String NOTIFICATION_DISMISS_EXTRA = "org.chromium.chrome.browser.upgrade.NotificationUpgrade";
  private static final String PREF_NAME = "org.chromium.chrome.browser.upgrade.NotificationUpdateTimeStampPreferences";
  private static final String MILLISECONDS_NAME = "org.chromium.chrome.browser.upgrade.Milliseconds";

  @Override
  public void onReceive(Context context, Intent intent) {
      try {
          int notificationId = IntentUtils.safeGetIntExtra(intent, NOTIFICATION_DISMISS_EXTRA, 0);
          if (notificationId != NOTIFICATION_ID) {
              return;
          }
          SetUpdatePreferences(context);
      } catch (Exception exc) {
          // Just ignore if we could not send a notification
          Log.i("TAG", "NotificationUpgradeDismissedReceiver error " + exc.getMessage());
      }
  }

  private void SetUpdatePreferences(Context context) {
      Calendar currentTime = Calendar.getInstance();
      long milliSeconds = currentTime.getTimeInMillis();

      SharedPreferences sharedPref = context.getSharedPreferences(PREF_NAME, 0);
      SharedPreferences.Editor editor = sharedPref.edit();

      editor.putLong(MILLISECONDS_NAME, milliSeconds);
      editor.apply();
  }
}
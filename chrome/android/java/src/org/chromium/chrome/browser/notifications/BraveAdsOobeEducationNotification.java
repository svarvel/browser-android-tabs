/** Copyright (c) 2019 The Brave Authors. All rights reserved.
  * This Source Code Form is subject to the terms of the Mozilla Public
  * License, v. 2.0. If a copy of the MPL was not distributed with this file,
  * You can obtain one at http://mozilla.org/MPL/2.0/.
  */

package org.chromium.chrome.browser.notifications;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.BraveRewardsHelper;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.notifications.BraveAdsNotificationBuilder;
import org.chromium.chrome.browser.notifications.ChromeNotification;
import org.chromium.chrome.browser.notifications.NotificationBuilderBase;
import org.chromium.chrome.browser.notifications.NotificationManagerProxyImpl;
import org.chromium.chrome.browser.notifications.NotificationMetadata;
import org.chromium.chrome.browser.notifications.NotificationUmaTracker;

public class BraveAdsOobeEducationNotification extends BroadcastReceiver {
    public Context mContext;
    private Intent mIntent;

    private static final int BRAVE_ADS_OOBE_NOTIFICATION_ID = -2;
    private static String BRAVE_ADS_OOBE_NOTIFICATION_TAG = "brave_ads_oobe_notification_tag";
    private static String BRAVE_ADS_OOBE_ORIGIN = "https://www.brave.com/my-first-ad";
    private static final String DEEP_LINK = "deep_link";

    private void showOobeNotification(Context context) {
        NotificationManagerProxyImpl notificationManager = new NotificationManagerProxyImpl(context);

        NotificationBuilderBase notificationBuilder =
          new BraveAdsNotificationBuilder(context)
              .setTitle(context.getString(R.string.brave_ads_oobe_education_notification_title))
              .setBody(context.getString(R.string.brave_ads_oobe_education_notification_body))
              .setSmallIconId(R.drawable.ic_chrome)
              .setPriority(Notification.PRIORITY_HIGH)
              .setContentIntent(getDeepLinkIntent(context))
              .setOrigin(BRAVE_ADS_OOBE_ORIGIN);

        ChromeNotification notification = notificationBuilder.build(
            new NotificationMetadata(
                NotificationUmaTracker.SystemNotificationType.UNKNOWN /* Underlying code doesn't track UNKNOWN */,
                BRAVE_ADS_OOBE_NOTIFICATION_TAG /* notificationTag */,
                BRAVE_ADS_OOBE_NOTIFICATION_ID /* notificationId */
            )
        );
        notificationManager.notify(notification);
    }

    private PendingIntentProvider getDeepLinkIntent(Context context) {
        Intent intent = new Intent(context, BraveAdsOobeEducationNotification.class);
        intent.setAction(DEEP_LINK);
        return new PendingIntentProvider(PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT), 0);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(DEEP_LINK)) {
            ChromeTabbedActivity activity = BraveRewardsHelper.GetChromeTabbedActivity();
            activity.openNewOrSelectExistingTab(BRAVE_ADS_OOBE_ORIGIN);
        } else {
            showOobeNotification(context);
        }
    }
}

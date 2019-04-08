package org.chromium.chrome.browser.notifications;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import org.chromium.chrome.R;
import org.chromium.base.ContextUtils;
import org.chromium.chrome.browser.BraveRewardsPanelPopup;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeActivitySessionTracker;
import org.chromium.chrome.browser.ChromeFeatureList;

import java.util.Random;

public class BraveSetDefaultBrowserNotificationService extends BroadcastReceiver {
    public Context mContext;
    private Intent mIntent;
    
    public static final String HAS_ASKED_AT_FIFTEEN_MINUTES = "brave_set_default_browser_has_asked_at_fifteen_minutes";
    public static final String HAS_SET_ALARMS_FOR_DAYS_LATER = "brave_set_default_browser_has_set_alarm";

    public static final int FIVE_MINUTES = 300_000;
    public static final int FIFTEEN_MINUTES = 900_000;
    public static final int FIVE_DAYS = 432_000_000;
    public static final int TEN_DAYS = 864_000_000;
    public static final int FIFTEEN_DAYS = 1_296_000_000;
    public static final int NOTIFICATION_ID = 10;

    // Deep links
    public static final String DEEP_LINK = "deep_link";
    public static final String SHOW_DEFAULT_APP_SETTINGS = "SHOW_DEFAULT_APP_SETTINGS";

    // Intent types
    public static final String INTENT_TYPE = "intent_type";
    public static final String BROWSER_UPDATED = "browser_updated";
    public static final String FIFTEEN_MINUTES_LATER = "fifteen_minutes_later";
    public static final String FIVE_DAYS_LATER = "five_days_later";
    public static final String TEN_DAYS_LATER = "ten_days_later";
    public static final String FIFTEEN_DAYS_LATER = "fifteen_days_later";

    public static final String CANCEL_NOTIFICATION = "cancel_notification";

    //Startup notification data
    private static int rewards_live_notification_id; //generated randomly
    private static String FIRST_TIME_RUN = "first_time_run";
    public static final String REWARDS_LEARN_MORE_URL = "https://brave.com/faq/#what-is-brave-rewards";
    public static final String BRAVE_REWARDS_INTERNAL_URL = "chrome://rewards";
    public static final String BRAVE_REWARDS_SUBSTITUTE_URL = "brave_rewards_substitute_url";
    public static final String NOTIFICATION_ID_EXTRA = "notification_id_extra";


    public static boolean isBraveSetAsDefaultBrowser(Context context) {
        Intent browserIntent = new Intent("android.intent.action.VIEW", Uri.parse("http://"));
        ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(browserIntent, supportsDefault() ? PackageManager.MATCH_DEFAULT_ONLY : 0);
        return resolveInfo.activityInfo.packageName.equals(ChromeActivity.BRAVE_PRODUCTION_PACKAGE_NAME) || resolveInfo.activityInfo.packageName.equals(ChromeActivity.BRAVE_DEVELOPMENT_PACKAGE_NAME);
    }

    private boolean shouldShowNotification() {
        SharedPreferences sharedPref = ContextUtils.getAppSharedPreferences();
        if (isBraveSetAsDefaultBrowser(mContext) || mIntent.getStringExtra(INTENT_TYPE) == null)
            return false;

        if (mIntent.getStringExtra(INTENT_TYPE).equals(FIFTEEN_MINUTES_LATER))
            return sharedPref.getBoolean(HAS_ASKED_AT_FIFTEEN_MINUTES, false) ? false : hasRanForPresetDuration();

        if (mIntent.getStringExtra(INTENT_TYPE).equals(FIVE_DAYS_LATER) ||
            mIntent.getStringExtra(INTENT_TYPE).equals(TEN_DAYS_LATER) ||
            mIntent.getStringExtra(INTENT_TYPE).equals(FIFTEEN_DAYS_LATER)
          )
            return true;

        if (mIntent.getStringExtra(INTENT_TYPE).equals(BROWSER_UPDATED))
            return true;

        // Shouldn't reach here. Just out of safety, don't annoy users
        return false;
    }

    private static boolean supportsDefault() {
        return Build.VERSION.SDK_INT >= 24;
    }

    private boolean hasAlternateDefaultBrowser() {
        Intent browserIntent = new Intent("android.intent.action.VIEW", Uri.parse("http://"));
        ResolveInfo resolveInfo = mContext.getPackageManager().resolveActivity(browserIntent, supportsDefault() ? PackageManager.MATCH_DEFAULT_ONLY : 0);
        return !(resolveInfo.activityInfo.packageName.equals("com.google.android.setupwizard") || resolveInfo.activityInfo.packageName.equals("android"));
    }

    private boolean hasRanForPresetDuration() {
        SharedPreferences sharedPref = ContextUtils.getAppSharedPreferences();
        return sharedPref.getLong(ChromeActivitySessionTracker.TOTAL_LIFETIME_USAGE, 0) + SystemClock.uptimeMillis() - sharedPref.getLong(ChromeActivitySessionTracker.SESSION_START_TIME, SystemClock.uptimeMillis()) >= FIFTEEN_MINUTES;
    }

    private void showNotification() {
        SharedPreferences sharedPref = ContextUtils.getAppSharedPreferences();
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(HAS_ASKED_AT_FIFTEEN_MINUTES, true);
        editor.apply();
        NotificationCompat.Builder b = new NotificationCompat.Builder(mContext, ChromeActivity.CHANNEL_ID);

        b.setSmallIcon(R.drawable.ic_chrome)
         .setAutoCancel(false)
         .setContentText(mContext.getString(R.string.brave_default_browser_notification_body))
         .setStyle(new NotificationCompat.BigTextStyle().bigText(mContext.getString(R.string.brave_default_browser_notification_body)))
         .setPriority(NotificationCompat.PRIORITY_DEFAULT)
         .setCategory(NotificationCompat.CATEGORY_MESSAGE);

        if (supportsDefault()) {
            b.setContentText(mContext.getString(R.string.brave_default_browser_existing_notification_body));
            b.setStyle(new NotificationCompat.BigTextStyle().bigText(mContext.getString(R.string.brave_default_browser_existing_notification_body)));
            PendingIntent intentYes = getDefaultAppSettingsIntent(mContext);
            NotificationCompat.Action actionYes = new NotificationCompat.Action.Builder(0, mContext.getString(R.string.ddg_offer_positive), intentYes).build();
            NotificationCompat.Action actionNo = new NotificationCompat.Action.Builder(0, mContext.getString(R.string.ddg_offer_negative), getDismissIntent(mContext, NOTIFICATION_ID)).build();
            b.addAction(actionYes);
            b.addAction(actionNo);
            b.setContentIntent(intentYes);
        }

        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, b.build());
    }

    public static PendingIntent getDefaultAppSettingsIntent(Context context) {
        Intent intent = new Intent(context, BraveSetDefaultBrowserNotificationService.class);
        intent.setAction(DEEP_LINK);
        intent.putExtra(DEEP_LINK, SHOW_DEFAULT_APP_SETTINGS);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static PendingIntent getDismissIntent(Context context, int notification_id) {
        Intent intent = new Intent(context, BraveSetDefaultBrowserNotificationService.class);
        intent.setAction(CANCEL_NOTIFICATION);
        intent.putExtra(NOTIFICATION_ID_EXTRA, notification_id);

        return PendingIntent.getBroadcast(context, notification_id, intent, 0);
    }

    private boolean shouldNotifyLater() {
      // Don't notify if it's default
      return !(isBraveSetAsDefaultBrowser(mContext));
    }

    private void notifyLater() {
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(mContext, BraveSetDefaultBrowserNotificationService.class);
        intent.putExtra(INTENT_TYPE, FIFTEEN_MINUTES_LATER);
        intent.setAction(FIFTEEN_MINUTES_LATER);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        am.set( AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + FIVE_MINUTES, pendingIntent );
    }

    private boolean hasSetAlarmsForDaysLater() {
        SharedPreferences sharedPref = ContextUtils.getAppSharedPreferences();
        // Check to see if we already set alarms for 5, 10, and 30 days later
        return !sharedPref.getBoolean(HAS_SET_ALARMS_FOR_DAYS_LATER, false);
    }

    private void setAlarmsForDaysLater() {
        SharedPreferences sharedPref = ContextUtils.getAppSharedPreferences();
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(HAS_SET_ALARMS_FOR_DAYS_LATER, true);
        editor.apply();

        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(mContext, BraveSetDefaultBrowserNotificationService.class);
        intent.putExtra(INTENT_TYPE, FIVE_DAYS_LATER);
        intent.setAction(FIVE_DAYS_LATER);
        am.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + FIVE_DAYS,
            PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        );

        intent.putExtra(INTENT_TYPE, TEN_DAYS_LATER);
        intent.setAction(TEN_DAYS_LATER);
        am.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + TEN_DAYS,
            PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        );

        intent.putExtra(INTENT_TYPE, FIFTEEN_DAYS_LATER);
        intent.setAction(FIFTEEN_DAYS_LATER);
        am.set(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + FIFTEEN_DAYS,
            PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        );
    }

    private void handleBraveSetDefaultBrowserDeepLink(Intent intent) {
         Bundle bundle = intent.getExtras();
         if (bundle.getString(BraveSetDefaultBrowserNotificationService.DEEP_LINK).equals(BraveSetDefaultBrowserNotificationService.SHOW_DEFAULT_APP_SETTINGS)) {
             Intent settingsIntent = hasAlternateDefaultBrowser() ?
                new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS) :
                new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.brave.com/blog"));
             settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
             mContext.startActivity(settingsIntent);
         }
     }

    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        mIntent = intent;
        boolean deepLinkIsHandled = false;
        if (intent != null && intent.hasExtra(BraveSetDefaultBrowserNotificationService.DEEP_LINK)) {
            handleBraveSetDefaultBrowserDeepLink(intent);
            deepLinkIsHandled = true;
        }

        if (deepLinkIsHandled ||
            (intent != null && intent.getAction() != null && intent.getAction().equals(CANCEL_NOTIFICATION))) {
            int notification_id = intent.getIntExtra(NOTIFICATION_ID_EXTRA, 0);
            NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(notification_id);
            return;
        }

        if (shouldShowNotification()) {
            showNotification();
        } else if (shouldNotifyLater()) {
            notifyLater();
            if (hasSetAlarmsForDaysLater()) {
                setAlarmsForDaysLater();
            }
        }
    }

    public static void NotifyRewardsLive(){
        SharedPreferences sharedPreferences = ContextUtils.getAppSharedPreferences();
        boolean rewardsIsOn = sharedPreferences.getBoolean(
            BraveRewardsPanelPopup.PREF_WAS_BRAVE_REWARDS_TURNED_ON, false);
        if (ChromeFeatureList.isEnabled(ChromeFeatureList.BRAVE_REWARDS) &&
            !rewardsIsOn){
            SharedPreferences sharedPref = ContextUtils.getAppSharedPreferences();
            boolean first_time_run = sharedPref.getBoolean(FIRST_TIME_RUN, true);
            if (first_time_run) {

                //save FIRST_TIME_RUN flag
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean(FIRST_TIME_RUN, false);
                editor.apply();

                Context context = ContextUtils.getApplicationContext();
                rewards_live_notification_id = new Random().nextInt();



                //intent that will fire when the user taps the Learn More
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(REWARDS_LEARN_MORE_URL));
                intent.setPackage(context.getPackageName());
                intent.putExtra(BRAVE_REWARDS_SUBSTITUTE_URL, BRAVE_REWARDS_INTERNAL_URL);
                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
                NotificationCompat.Action learnAction =
                         new NotificationCompat.Action.Builder(0, context.getString(R.string.brave_rewards_get_started),
                                pendingIntent)
                                .build();

                //Dismiss intent
                PendingIntent dismissIntent = getDismissIntent(context,rewards_live_notification_id);
                NotificationCompat.Action dismissAction =
                        new NotificationCompat.Action.Builder(0, context.getString(R.string.brave_rewards_dismiss),
                                dismissIntent)
                                .build();

                //select small and large icons for devices < 21 or higher
                int smallIconId =  (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) ? R.drawable.brave_logo_19 : R.drawable.ic_chrome;
                int largeIconId =  (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) ? R.drawable.bat_logo : R.drawable.bat_icon;
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ChromeActivity.CHANNEL_ID);
                builder.setSmallIcon(smallIconId);
                builder.setContentTitle(context.getString(R.string.brave_rewards_intro_title));
                builder.setContentText(context.getString(R.string.brave_rewards_intro_text));
                builder.setContentIntent(pendingIntent);
                builder.setLargeIcon(BitmapFactory.decodeResource(
                                context.getResources(),
                                largeIconId));
                builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
                builder.setAutoCancel(true);
                builder.addAction(learnAction);
                builder.addAction(dismissAction);

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
                notificationManager.notify(rewards_live_notification_id, builder.build());
            }
        }
    }
}

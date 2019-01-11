/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;


import org.chromium.base.ApplicationStatus;
import org.chromium.base.Log;
import org.chromium.base.SysUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelImpl;
import org.chromium.base.AsyncTask;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.math.BigInteger;
import java.util.Calendar;

public class BraveRewardsHelper {
  static public ChromeTabbedActivity GetChromeTabbedActivity() {
    for (WeakReference<Activity> ref : ApplicationStatus.getRunningActivities()) {
        if (!(ref.get() instanceof ChromeTabbedActivity)) continue;

        return (ChromeTabbedActivity)ref.get();
    }

    return null;
  }

  static public String getCurrentMonth(Resources resources) {
    Calendar currentTime = Calendar.getInstance();
    String month = resources.getString(R.string.brave_ui_month_jan);
    switch (currentTime.get(Calendar.MONTH)) {
      case Calendar.JANUARY:
        month = resources.getString(R.string.brave_ui_month_jan);
        break;
      case Calendar.FEBRUARY:
        month = resources.getString(R.string.brave_ui_month_feb);
        break;
      case Calendar.MARCH:
        month = resources.getString(R.string.brave_ui_month_mar);
        break;
      case Calendar.APRIL:
        month = resources.getString(R.string.brave_ui_month_apr);
        break;
      case Calendar.MAY:
        month = resources.getString(R.string.brave_ui_month_may);
        break;
      case Calendar.JUNE:
        month = resources.getString(R.string.brave_ui_month_jun);
        break;
      case Calendar.JULY:
        month = resources.getString(R.string.brave_ui_month_jul);
        break;
      case Calendar.AUGUST:
        month = resources.getString(R.string.brave_ui_month_aug);
        break;
      case Calendar.SEPTEMBER:
        month = resources.getString(R.string.brave_ui_month_sep);
        break;
      case Calendar.OCTOBER:
        month = resources.getString(R.string.brave_ui_month_oct);
        break;
      case Calendar.NOVEMBER:
        month = resources.getString(R.string.brave_ui_month_nov);
        break;
      case Calendar.DECEMBER:
        month = resources.getString(R.string.brave_ui_month_dec);
        break;
    }

    return month;
  }

  static public String getCurrentYear(Resources resources) {
    Calendar currentTime = Calendar.getInstance();
    return Integer.toString(currentTime.get(Calendar.YEAR));
  }

  public static Tab currentActiveTab() {
    ChromeTabbedActivity activity = BraveRewardsHelper.GetChromeTabbedActivity();
    if (activity == null || activity.getTabModelSelector() == null){
      return null;
    }
    return activity.getActivityTab();
  }

  static public void retrieveFavIcon( FaviconHelper favIconHelper, FaviconHelper.FaviconImageCallback parent, String publisherURL, String publisherFavIconURL){
    (new Runnable() {
      @Override
      public void run() {

        if (publisherFavIconURL.isEmpty()) {
          Tab currentActiveTab = currentActiveTab();
          if (currentActiveTab != null) {
            favIconHelper.getLocalFaviconImageForURL(currentActiveTab.getProfile(),
                    publisherURL, 64, parent);
          }
        } else {
          new AsyncTask<Void>() {
            @Override
            protected Void doInBackground() {
              try {
                URL url = new URL(publisherFavIconURL);
                Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                parent.onFaviconAvailable(bmp, publisherFavIconURL);
              } catch (MalformedURLException exc) {
              } catch (IOException exc) {
              }

              return null;
            }
          }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
      }
    }).run();
  }

  static String probiToNumber(String probi) {
    if (probi.equals("0")) {
      return probi;
    }

    BigInteger probiInteger = new BigInteger(probi, 10);
    BigInteger dividerInteger = new BigInteger("1000000000000000000", 10);

    return probiInteger.divide(dividerInteger).toString();
  }
}
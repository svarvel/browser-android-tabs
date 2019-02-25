/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.View;


import org.chromium.base.ApplicationStatus;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.Log;
import org.chromium.base.SysUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.favicon.IconType;
import org.chromium.chrome.browser.favicon.LargeIconBridge;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelImpl;
import org.chromium.chrome.browser.widget.RoundedIconGenerator;


import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.math.BigInteger;
import java.util.Calendar;


public class BraveRewardsHelper implements LargeIconBridge.LargeIconCallback{
    private static final int FAVICON_CIRCLE_MEASUREMENTS = 70; // dp
    private static final int FAVICON_TEXT_SIZE = 50; // dp
    private static final int FAVICON_FETCH_INTERVAL = 1500; // In milliseconds
    private static final int FAVICON_DESIRED_SIZE = 64; // px
    private static LargeIconBridge mLargeIconBridge;

    private String mFaviconUrl;
    private LargeIconReadyCallback mCallback;
    private final Handler mHandler = new Handler();
    private int mFetchCount;
    private final int MAX_FAVICON_FETCH_COUNT = 20;


    public interface LargeIconReadyCallback {
        void onLargeIconReady(Bitmap icon);
    }

    public BraveRewardsHelper (){
        if (mLargeIconBridge == null) {
            mLargeIconBridge = new LargeIconBridge(currentActiveTab().getProfile());
        }
    }

    /**
     *  we don't destroy mLargeIconBridge sisnce it's static
     */
    private void destroy(){
        if (mLargeIconBridge != null) {
            mLargeIconBridge.destroy();
            mLargeIconBridge = null;
        }
        mCallback =  null;
    }


    public void detach(){
        mCallback =  null;
    }

    public void retrieveLargeIcon(String favIconURL, LargeIconReadyCallback callback){
        mCallback = callback;
        mFaviconUrl = favIconURL;
        retrieveLargeIconInternal();
    }

    private void retrieveLargeIconInternal(){
        mFetchCount ++;

        //favIconURL (or content URL) is still not available, try to read it again
        if (mFaviconUrl == null || mFaviconUrl.isEmpty() || mFaviconUrl.equals("clear") ){
            Tab tab  = currentActiveTab();
            if (tab != null){
                mFaviconUrl = tab.getUrl();
            }

            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    retrieveLargeIconInternal();
                }
            }, FAVICON_FETCH_INTERVAL);

            return;
        }

        //get the icon
        if (mLargeIconBridge!= null && mCallback != null && !mFaviconUrl.isEmpty() ){
            mLargeIconBridge.getLargeIconForUrl(mFaviconUrl,FAVICON_DESIRED_SIZE, this);
        }
    }

    @Override
    @CalledByNative("LargeIconCallback")
    public void onLargeIconAvailable(@Nullable Bitmap icon, int fallbackColor,
                                     boolean isFallbackColorDefault, @IconType int iconType){
        if (mFaviconUrl.isEmpty()){
            return;
        }

        if (  mFetchCount == MAX_FAVICON_FETCH_COUNT  || (icon == null && false == isFallbackColorDefault) ){
            RoundedIconGenerator mIconGenerator = new RoundedIconGenerator(Resources.getSystem(),
                    FAVICON_CIRCLE_MEASUREMENTS, FAVICON_CIRCLE_MEASUREMENTS,
                    FAVICON_CIRCLE_MEASUREMENTS, fallbackColor, FAVICON_TEXT_SIZE);

            mIconGenerator.setBackgroundColor(fallbackColor);
            icon = mIconGenerator.generateIconForUrl(mFaviconUrl);
        }
        else if (icon == null && true == isFallbackColorDefault) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    retrieveLargeIconInternal();
                }
            }, FAVICON_FETCH_INTERVAL);
            return;
        }
        //else: icon is available

        if (mCallback != null) {
            mCallback.onLargeIconReady(icon);
        }
    }


    public static Bitmap getCircularBitmap(Bitmap bitmap) {
        Bitmap output;

        if (bitmap.getWidth() > bitmap.getHeight()) {
            output = Bitmap.createBitmap(bitmap.getHeight(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        } else {
            output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getWidth(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        float r = 0;

        if (bitmap.getWidth() > bitmap.getHeight()) {
            r = bitmap.getHeight() / 2;
        } else {
            r = bitmap.getWidth() / 2;
        }

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawCircle(r, r, r, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }


    static public ChromeTabbedActivity GetChromeTabbedActivity() {
    for (WeakReference<Activity> ref : ApplicationStatus.getRunningActivities()) {
        if (!(ref.get() instanceof ChromeTabbedActivity)) continue;

        return (ChromeTabbedActivity)ref.get();
    }

    return null;
  }

  static public String getCurrentMonth(Calendar currentTime,
      Resources resources, boolean upper_case) {
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
    if (!upper_case && !month.isEmpty()) {
      return month.substring(0, 1) + month.substring(1).toLowerCase();
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

  /**
   *
   * @param fadeout: can be null
   * @param fadein: can be null
   * @param fade_out_visibility: View.INVISIBLE or View.GONE
   */
  public static void crossfade(final View fadeout, final View fadein, int fade_out_visibility, float fadeInAlpha) {
    final int FADE_OUT_TIME = 2000; //ms

    if (fadeInAlpha < 0 || fadeInAlpha > 1  ){
        fadeInAlpha= 1f;
    }

    final int fade_out_visibility_local =
    (fade_out_visibility != View.GONE && fade_out_visibility != View.INVISIBLE) ?
      View.GONE : fade_out_visibility;

    // Set the content view to 0% opacity but visible, so that it is visible
    // (but fully transparent) during the animation.
    if (fadein != null) {
      fadein.setAlpha(0f);
      fadein.setVisibility(View.VISIBLE);

      // Animate the content view to 100% opacity, and clear any animation
      // listener set on the view.
      fadein.animate()
              .alpha(fadeInAlpha)
              .setDuration(FADE_OUT_TIME)
              .setListener(null);
    }

    // Animate the loading view to 0% opacity. After the animation ends,
    // set its visibility to GONE as an optimization step (it won't
    // participate in layout passes, etc.)
    if (fadeout != null) {
      fadeout.animate()
              .alpha(0f)
              .setDuration(FADE_OUT_TIME)
              .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                  fadeout.setVisibility(fade_out_visibility_local);
                }
              });
    }
  }


  static double probiToDouble(String probi) {
      final String PROBI_POWER = "1000000000000000000";
      double val = Double.NaN;
      try {
          BigDecimal probiNumber = new BigDecimal(probi);
          BigDecimal dividerNumber = new BigDecimal(PROBI_POWER);
          val = probiNumber.divide(dividerNumber).doubleValue();
      }
      catch(NumberFormatException e){
          val = Double.NaN;
      }
      return val;
  }
}
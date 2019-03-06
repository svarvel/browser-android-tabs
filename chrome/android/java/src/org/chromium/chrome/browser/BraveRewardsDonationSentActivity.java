/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;

import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.BraveRewardsHelper;
import org.chromium.chrome.browser.BraveRewardsNativeWorker;
import org.chromium.chrome.browser.BraveRewardsSiteBannerActivity;

public class BraveRewardsDonationSentActivity extends Activity implements BraveRewardsHelper.LargeIconReadyCallback {

    private final int SLIDE_UP_DURATION = 2000;
    private final int PUBLISHER_ICON_SIDE_LEN= 70;
    private int currentTabId_ = -1;
    private BraveRewardsNativeWorker mBraveRewardsNativeWorker;
    private BraveRewardsHelper mIconFetcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.brave_rewards_donation_sent);
        currentTabId_ = IntentUtils.safeGetIntExtra(getIntent(), BraveRewardsSiteBannerActivity.TAB_ID_EXTRA, -1);


        mBraveRewardsNativeWorker = BraveRewardsNativeWorker.getInstance();
        String publisherFavIconURL = mBraveRewardsNativeWorker.GetPublisherFavIconURL(currentTabId_);
        Tab currentActiveTab = BraveRewardsHelper.currentActiveTab();
        String url = currentActiveTab.getUrl();
        String favicon_url = (publisherFavIconURL.isEmpty()) ? url : publisherFavIconURL;
        mIconFetcher = new org.chromium.chrome.browser.BraveRewardsHelper();
        mIconFetcher.retrieveLargeIcon(favicon_url, this);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int height = metrics.heightPixels;
        View floater = findViewById(R.id.floater);

        //slide up animation
        TranslateAnimation animate = new TranslateAnimation(0,0,height,0);
        animate.setDuration(SLIDE_UP_DURATION);
        animate.setFillAfter(true);
        animate.setAnimationListener(new Animation.AnimationListener(){
            @Override
            public void onAnimationStart(Animation arg0) { }
            @Override
            public void onAnimationRepeat(Animation arg0) {}
            @Override
            public void onAnimationEnd(Animation arg0) {

                //fade out animation
                Animation fadeOut = new AlphaAnimation(1, 0);
                fadeOut.setInterpolator(new AccelerateInterpolator());
                fadeOut.setStartOffset(0);
                fadeOut.setDuration(BraveRewardsHelper.CROSS_FADE_DURATION);

                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation arg0) {}
                    @Override
                    public void onAnimationRepeat(Animation arg0) {}
                    @Override
                    public void onAnimationEnd(Animation arg0) {
                        finish();
                    }
                });

                findViewById(R.id.floater).setAnimation(fadeOut);
            }
        });
        floater.startAnimation(animate);
    }


    private void SetFavIcon(Bitmap bmp) {
        if (bmp != null){
            runOnUiThread(
                    new Runnable(){
                        @Override
                        public void run() {
                            ImageView iv = (ImageView) findViewById(R.id.publisher_favicon);
                            int nPx = BraveRewardsHelper.dp2px(PUBLISHER_ICON_SIDE_LEN);
                            Bitmap resized = Bitmap.createScaledBitmap(bmp, nPx, nPx, true);

                            View fadeout  = findViewById(R.id.publisher_favicon_update);
                            BraveRewardsHelper.crossfade(fadeout, iv, View.GONE, 1f, BraveRewardsHelper.CROSS_FADE_DURATION);
                            iv.setImageBitmap(BraveRewardsHelper.getCircularBitmap(resized));
                        }
                    });
        }
    }

    @Override
    public void onLargeIconReady(Bitmap icon){
        SetFavIcon(icon);
    }
}

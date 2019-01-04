/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.BraveRewardsHelper;
import org.chromium.chrome.browser.BraveRewardsNativeWorker;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import android.graphics.Bitmap;
import android.widget.ImageView;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import java.net.URL;
import org.chromium.base.AsyncTask;
import android.graphics.BitmapFactory;
import org.chromium.base.annotations.CalledByNative;
import java.io.IOException;
import java.net.MalformedURLException;

public class BraveRewardsSiteBannerActivity extends Activity {

    private  ToggleButton radio_tip_amount[] = new ToggleButton [3];
    private final int TIP_SENT_REQUEST_CODE = 2;
    private final int FADE_OUT_DURATION = 750;
    private final float LANDSCAPE_HEADER_WEIGHT = 2.0f;
    public static final String TAB_ID_EXTRA = "currentTabId";
    private int currentTabId_ = -1;
    private FaviconHelper mFavIconHelper;
    private BraveRewardsNativeWorker mBraveRewardsNativeWorker;
    private final int PUBLISHER_ICON_SIDE_LEN= 56; //56 dp fits into 80 dp radius circle


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        //inflate
        super.onCreate(savedInstanceState);
        setContentView(R.layout.brave_rewards_site_banner);

        //change weight of the footer in landscape mode
        int orientation = this.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    LANDSCAPE_HEADER_WEIGHT
            );
            findViewById(R.id.site_banner_header).setLayoutParams(param);
        }

        //bind tip amount custom radio buttons
        radio_tip_amount[0] = findViewById(R.id.one_bat_option);
        radio_tip_amount[1] = findViewById(R.id.five_bat_option);
        radio_tip_amount[2] = findViewById(R.id.ten_bat_option);


        //radio buttons behaviour
        View.OnClickListener radio_clicker = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int id = view.getId();
                for (ToggleButton tb : radio_tip_amount){
                    if (tb.getId() == id){
                        continue;
                    }
                    tb.setChecked(false);
                }
            }
        };

        findViewById(R.id.one_bat_option).setOnClickListener(radio_clicker);
        findViewById(R.id.five_bat_option).setOnClickListener(radio_clicker);
        findViewById(R.id.ten_bat_option).setOnClickListener(radio_clicker);


        //set tip button onClick
        View.OnClickListener send_tip_clicker = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean enough_funds = true;

                //proceed to tipping
                if (true == enough_funds) {
                    Intent intent = new Intent(getApplicationContext(), BraveRewardsDonationSentActivity.class);
                    startActivityForResult(intent,TIP_SENT_REQUEST_CODE);
                }
                //not enough funds
                else {
                    TextView send_tip = (TextView) findViewById(R.id.send_donation_button);
                    send_tip.setVisibility(View.GONE);
                    View animatedView = findViewById(R.id.not_enough_funds_text);
                    TranslateAnimation animate = new TranslateAnimation(0,0,findViewById(R.id.send_donation_button).getHeight(),0);
                    animate.setDuration(FADE_OUT_DURATION);
                    animate.setFillAfter(true);
                    animatedView.startAnimation(animate);
                    animatedView.setVisibility(View.VISIBLE);
                }
            }
        };

        findViewById(R.id.send_donation_button).setOnClickListener (send_tip_clicker);

        mFavIconHelper = new FaviconHelper();
        currentTabId_ = IntentUtils.safeGetIntExtra(getIntent(), TAB_ID_EXTRA, -1);
        ChromeTabbedActivity activity = BraveRewardsHelper.GetChromeTabbedActivity();
        mBraveRewardsNativeWorker = activity.getBraveRewardsNativeWorker();
        retrieveFavIcon();

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if(TIP_SENT_REQUEST_CODE == requestCode)
        {
            //disable the activity while fading it out
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            finish();
            overridePendingTransition(R.anim.activity_fade_in, R.anim.activity_fade_out);

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing()){
            overridePendingTransition(R.anim.activity_fade_in, R.anim.activity_fade_out);
        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if (mFavIconHelper != null) {
            mFavIconHelper.destroy();
        }
    }

    private void retrieveFavIcon() {
        (new Runnable() {
            @Override
            public void run() {
                String faviconURL = mBraveRewardsNativeWorker.GetPublisherFavIconURL(currentTabId_);
                if (faviconURL.isEmpty()) {
                    Tab currentActiveTab = currentActiveTab();
                    mFavIconHelper.getLocalFaviconImageForURL(currentActiveTab.getProfile(),
                            mBraveRewardsNativeWorker.GetPublisherURL(currentTabId_),
                            64, new FaviconFetcher());
                } else {
                    new AsyncTask<Void>() {
                        @Override
                        protected Void doInBackground() {
                            try {
                                Log.i("TAG", "!!!faviconURL == " + faviconURL);
                                URL url = new URL(faviconURL);
                                Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                                ChromeTabbedActivity activity = BraveRewardsHelper.GetChromeTabbedActivity();
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.i("TAG", "!!!setting the icon");
                                        SetFavIcon(bmp);
                                    }
                                });
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

    private Tab currentActiveTab() {
        ChromeTabbedActivity activity = BraveRewardsHelper.GetChromeTabbedActivity();
        if (activity == null || activity.getTabModelSelector() == null) return null;
        return activity.getActivityTab();
    }


    private void SetFavIcon(Bitmap bmp) {
        ImageView iv = (ImageView)findViewById(R.id.publisher_favicon);

        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        float px = PUBLISHER_ICON_SIDE_LEN * (metrics.densityDpi / 160f);
        int dp = Math.round(px);

        Bitmap resized = Bitmap.createScaledBitmap(bmp, dp, dp, true);
        iv.setImageBitmap(resized);
    }

    public class FaviconFetcher implements FaviconHelper.FaviconImageCallback {
        @CalledByNative
        @Override
        public void onFaviconAvailable(Bitmap image, String iconUrl) {
            final Bitmap bmp = image;
            ChromeTabbedActivity activity = BraveRewardsHelper.GetChromeTabbedActivity();
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    SetFavIcon(bmp);
                }
            });
        }
    }

}

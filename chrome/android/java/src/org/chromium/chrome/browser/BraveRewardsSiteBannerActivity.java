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
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.text.Html;
import android.text.Spanned;
import android.content.Context;
import android.os.Build;
import android.view.MotionEvent;
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
import org.chromium.base.ContextUtils;

public class BraveRewardsSiteBannerActivity extends Activity implements FaviconHelper.FaviconImageCallback {

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


        mFavIconHelper = new FaviconHelper();
        currentTabId_ = IntentUtils.safeGetIntExtra(getIntent(), TAB_ID_EXTRA, -1);
        mBraveRewardsNativeWorker = BraveRewardsNativeWorker.getInstance();

        String publisherURL = mBraveRewardsNativeWorker.GetPublisherURL(currentTabId_);
        String publisherFavIconURL = mBraveRewardsNativeWorker.GetPublisherFavIconURL(currentTabId_);
        BraveRewardsHelper.retrieveFavIcon(mFavIconHelper, this, publisherURL, publisherFavIconURL);

        double balance = mBraveRewardsNativeWorker.GetWalletBalance();
        String walletAmount = String.format("%.2f", balance) + " BAT";
        ((TextView)findViewById(R.id.wallet_amount_text)).setText(walletAmount);

        double usdValue = mBraveRewardsNativeWorker.GetWalletRate("USD");
        double fiveBat = 5 * usdValue;
        double tenBat = 10 * usdValue;
        String oneBatRate = String.format("%.2f", usdValue) + " USD";
        String fiveBatRate = String.format("%.2f", fiveBat) + " USD";
        String tenBatRate = String.format("%.2f", tenBat) + " USD";
        ((TextView)findViewById(R.id.one_bat_rate)).setText(oneBatRate);
        ((TextView)findViewById(R.id.five_bat_rate)).setText(fiveBatRate);
        ((TextView)findViewById(R.id.ten_bat_rate)).setText(tenBatRate);

        //set tip button onClick
        View.OnClickListener send_tip_clicker = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                double balance = mBraveRewardsNativeWorker.GetWalletBalance();
                int amount = 0;
                for (ToggleButton tb : radio_tip_amount){
                    if (tb.isChecked()) {
                        int id = tb.getId();
                        if (id == R.id.one_bat_option) {
                            amount = 1;
                        } else if (id == R.id.five_bat_option) {
                            amount = 5;
                        } else if (id == R.id.ten_bat_option) {
                            amount = 10;
                        } 

                        break;
                    }
                }
                boolean enough_funds = ((balance - amount) > 0);

                //proceed to tipping
                if (true == enough_funds) {
                    CheckBox monthly = (CheckBox)findViewById(R.id.make_monthly_checkbox);
                    mBraveRewardsNativeWorker.Donate(mBraveRewardsNativeWorker.GetPublisherId(currentTabId_),
                        amount, monthly.isChecked());
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


        //set 'add funds' button onClick
        View.OnClickListener add_funds_clicker = new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent returnIntent = new Intent();
		        setResult(ChromeTabbedActivity.SITE_BANNER_ADD_FUNDS_RESULT_CODE, returnIntent);
		        finish();
            }
        };

        TextView not_enough_funds_btn = (TextView)findViewById(R.id.not_enough_funds_text);
        not_enough_funds_btn.setOnClickListener (add_funds_clicker);

        String part1 = getResources().getString(R.string.brave_ui_site_banner_not_enough_tokens);
        String part2 = getResources().getString(R.string.brave_ui_please);
        String part3 = getResources().getString(R.string.brave_ui_add_funds);

        part1 = part1.substring(0,1).toUpperCase() + part1.substring(1);
        part2 = part2.substring(0,1).toUpperCase() + part2.substring(1);

        StringBuilder sb = new StringBuilder();
        sb.append(part1);
        sb.append(". ");
        sb.append(part2);
        sb.append(" <u>");
        sb.append(part3);
        sb.append("</u>.");

        Spanned toInsert;
        Context appContext = ContextUtils.getApplicationContext();
        if (appContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            toInsert = Html.fromHtml(sb.toString(), Html.FROM_HTML_MODE_LEGACY);
        } else {
            toInsert = Html.fromHtml(sb.toString());
        }
        not_enough_funds_btn.setText(toInsert);

        ///////////////////////////////////////////////////////////////////////////////////////
        boolean verified = mBraveRewardsNativeWorker.GetPublisherVerified(currentTabId_);
        if (!verified) {
            findViewById(R.id.not_verified_warning_layout ).setVisibility(View.VISIBLE);

            part1 = getResources().getString(R.string.brave_ui_site_banner_not_verified);
            part2 = getResources().getString(R.string.learn_more);

            final StringBuilder sb1 = new StringBuilder();
            sb1.append(part1);
            sb1.append(" <font color=#00afff>");
            sb1.append(part2);
            sb1.append("</font>");

            if (appContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                toInsert = Html.fromHtml(sb1.toString(), Html.FROM_HTML_MODE_LEGACY);
            } else {
                toInsert = Html.fromHtml(sb1.toString());
            }
            TextView not_verified_warning_text = (TextView )findViewById(R.id.not_verified_warning_text );
            not_verified_warning_text.setText(toInsert);
            final int learn_more_start_index = toInsert.length() - part2.length();

            not_verified_warning_text.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        int offset = not_verified_warning_text.getOffsetForPosition(
                                motionEvent.getX(), motionEvent.getY());
                        if (offset >= learn_more_start_index){
                            // We are on learn more
                            Intent returnIntent = new Intent();
                            setResult(ChromeTabbedActivity.SITE_BANNER_NOT_VERIFIED_LEARN_MORE_RESULT_CODE, returnIntent);
                            finish();
                        }
                    }
                    return false;
                }
            });
        }
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


    private void SetFavIcon(Bitmap bmp) {
        ImageView iv = (ImageView)findViewById(R.id.publisher_favicon);

        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        float px = PUBLISHER_ICON_SIDE_LEN * (metrics.densityDpi / 160f);
        int dp = Math.round(px);

        Bitmap resized = Bitmap.createScaledBitmap(bmp, dp, dp, true);
        iv.setImageBitmap(resized);
    }

    //onFaviconAvailable implementation of FaviconHelper.FaviconImageCallback
    //it has to set up the received icon on UI thread
    @CalledByNative
    @Override
    public void onFaviconAvailable(Bitmap image, String iconUrl) {
        final Bitmap bmp = image;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SetFavIcon(bmp);
            }
        });
    }

}

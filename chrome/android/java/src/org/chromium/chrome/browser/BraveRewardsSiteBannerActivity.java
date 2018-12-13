package org.chromium.chrome.browser;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ToggleButton;

public class BraveRewardsSiteBannerActivity extends Activity {

    private  ToggleButton radio_tip_amount[] = new ToggleButton [3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        //inflate
        super.onCreate(savedInstanceState);
        setContentView(R.layout.brave_rewards_site_banner);

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


        //Send donation tip button onClick
        View.OnClickListener send_tip_clicker = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView send_tip = (TextView) findViewById(R.id.send_donation_button);
                send_tip.setVisibility(View.GONE);

                TextView not_enough_funds_text = (TextView) findViewById(R.id.not_enough_funds_text);
                TranslateAnimation animate = new TranslateAnimation(0,0,not_enough_funds_text.getHeight(),0);
                animate.setDuration(500);
                animate.setFillAfter(true);
                not_enough_funds_text.startAnimation(animate);
                not_enough_funds_text.setVisibility(View.VISIBLE);
            }
        };
        findViewById(R.id.send_donation_button).setOnClickListener (send_tip_clicker);
    }
}

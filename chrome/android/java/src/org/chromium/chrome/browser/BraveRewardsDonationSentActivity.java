package org.chromium.chrome.browser;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;

public class BraveRewardsDonationSentActivity extends Activity implements View.OnClickListener{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.brave_rewards_donation_sent);
        findViewById(R.id.donation_sent_transparent_view).setOnClickListener(this);        
    }

   @Override
   public void onClick(View v) {
      finish();
   }
}

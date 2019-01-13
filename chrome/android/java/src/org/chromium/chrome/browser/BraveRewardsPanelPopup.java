/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;


import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.AsyncTask;
import org.chromium.base.Log;
import org.chromium.base.SysUtils;
import org.chromium.chrome.browser.BraveRewardsHelper;
import org.chromium.chrome.browser.BraveRewardsObserver;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelImpl;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorImpl;
import org.chromium.chrome.R;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.base.ContextUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;


public class BraveRewardsPanelPopup implements BraveRewardsObserver, FaviconHelper.FaviconImageCallback {
    private static final int UPDATE_BALANCE_INTERVAL = 60000;  // In milliseconds
    private static final String YOUTUBE_TYPE = "youtube#";
    private static final String TWITCH_TYPE = "twitch#";

    protected final View anchor;
    private final PopupWindow window;
    private final BraveRewardsPanelPopup thisObject;
    private final ChromeTabbedActivity mActivity;
    private View root;
    private Button btJoinRewards;
    private Button btAddFunds;
    private Button btRewardsSettings;
    private Switch btAutoContribute;
    private TextView tvLearnMore;
    private BraveRewardsNativeWorker mBraveRewardsNativeWorker;
    private Timer balanceUpdater;
    private FaviconHelper mFavIconHelper;
    private int currentTabId;
    private OnCheckedChangeListener autoContributeSwitchListener;
    private Button btRewardsSummary;
    private boolean publisherExist;

    public BraveRewardsPanelPopup(View anchor) {
        publisherExist = false;
        currentTabId = -1;
        this.anchor = anchor;
        this.window = new PopupWindow(anchor.getContext());
        thisObject = this;

        this.window.setTouchInterceptor(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    thisObject.dismiss();
                    return true;
                }
                return false;
            }
        });
        this.window.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                if (balanceUpdater != null) {
                  balanceUpdater.cancel();
                }

                if (mBraveRewardsNativeWorker != null) {
                  mBraveRewardsNativeWorker.RemoveObserver(thisObject);
                }
                if (mFavIconHelper != null) {
                  mFavIconHelper.destroy();
                }
                if (currentTabId != -1 && mBraveRewardsNativeWorker != null) {
                  mBraveRewardsNativeWorker.RemovePublisherFromMap(currentTabId);
                }
            }
        });
        mActivity = BraveRewardsHelper.GetChromeTabbedActivity();
        mBraveRewardsNativeWorker = BraveRewardsNativeWorker.getInstance();
        if (mBraveRewardsNativeWorker != null) {
          mBraveRewardsNativeWorker.AddObserver(thisObject);
        }
        balanceUpdater = new Timer();
        mFavIconHelper = new FaviconHelper();

        onCreate();
    }

    private void CreateUpdateBalanceTask() {
      balanceUpdater.schedule(new TimerTask() {
        @Override
        public void run() {
          if (thisObject.mBraveRewardsNativeWorker == null) {
            return;
          }
          mActivity.runOnUiThread(new Runnable() {
              @Override
              public void run() {
                  thisObject.mBraveRewardsNativeWorker.GetWalletProperties();
              }
          });
        }
      }, 0, 60000);
    }

    protected void onCreate() {
        LayoutInflater inflater =
                (LayoutInflater) this.anchor.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.brave_rewards_panel, null);
        setContentView(root);
        if (mBraveRewardsNativeWorker != null && mBraveRewardsNativeWorker.WalletExist()) {
          ShowWebSiteView();
        }
        btJoinRewards = (Button)root.findViewById(R.id.join_rewards_id);
        if (btJoinRewards != null) {
          btJoinRewards.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBraveRewardsNativeWorker != null) {
                  mBraveRewardsNativeWorker.CreateWallet();
                }
                Button btJoinRewards = (Button)BraveRewardsPanelPopup.this.root.findViewById(R.id.join_rewards_id);
                btJoinRewards.setText(BraveRewardsPanelPopup.this.root.getResources().getString(R.string.brave_ui_rewards_creating_text));
                btJoinRewards.setClickable(false);
                btJoinRewards.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.brave_rewards_loader, 0);
                AnimationDrawable drawable = (AnimationDrawable)btJoinRewards.getCompoundDrawables()[2];
                if (drawable != null) {
                  drawable.start();
                }
            }
          }));
        }
        tvLearnMore = (TextView)root.findViewById(R.id.learn_more_id);
        if (tvLearnMore != null) {
          tvLearnMore.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchTabInRunningTabbedActivity(new LoadUrlParams("chrome://rewards"));
                dismiss();
            }
          }));
        }

        btAddFunds = (Button)root.findViewById(R.id.br_add_funds);
        if (btAddFunds != null) {
          btAddFunds.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchTabInRunningTabbedActivity(new LoadUrlParams("chrome://rewards"));
                dismiss();
            }
          }));
        }
        btRewardsSettings = (Button)root.findViewById(R.id.br_rewards_settings);
        if (btRewardsSettings != null) {
          btRewardsSettings.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchTabInRunningTabbedActivity(new LoadUrlParams("chrome://rewards"));
                dismiss();
            }
          }));
        }

        btAutoContribute = (Switch)root.findViewById(R.id.brave_ui_auto_contribute);

        if (btAutoContribute != null) {
          autoContributeSwitchListener = new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
              boolean isChecked) {
                thisObject.mBraveRewardsNativeWorker.IncludeInAutoContribution(currentTabId, !isChecked);
                Log.i("TAG", "!!!isChecked == " + isChecked);
            }
          };
          btAutoContribute.setOnCheckedChangeListener(autoContributeSwitchListener);
        }

        btRewardsSummary = (Button)root.findViewById(R.id.rewards_summary);
        if (btRewardsSummary != null) {
          btRewardsSummary.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GridLayout gl = (GridLayout)thisObject.root.findViewById(R.id.website_summary_grid);
                LinearLayout ll = (LinearLayout)thisObject.root.findViewById(R.id.br_central_layout);
                if (gl == null || ll == null) {
                    return;
                }
                if (gl.getVisibility() == View.VISIBLE) {
                    gl.setVisibility(View.GONE);
                    ll.setBackgroundColor(((ColorDrawable)btRewardsSummary.getBackground()).getColor());
                    SetRewardsSummaryMonthYear();
                    ShowRewardsSummary();
                } else {
                    TextView tv = (TextView)thisObject.root.findViewById(R.id.br_no_activities_yet);
                    GridLayout glActivities = (GridLayout)thisObject.root.findViewById(R.id.br_activities);
                    if (publisherExist) {
                        gl.setVisibility(View.VISIBLE);
                        ll.setBackgroundColor(Color.WHITE);
                        RemoveRewardsSummaryMonthYear();
                        if (tv != null && glActivities != null) {
                          tv.setVisibility(View.GONE);
                          glActivities.setVisibility(View.GONE);
                        }
                    } else if (tv != null) {
                      if (tv.getVisibility() == View.VISIBLE || 
                          glActivities.getVisibility() == View.VISIBLE) {
                        tv.setVisibility(View.GONE);
                        glActivities.setVisibility(View.GONE);
                        btRewardsSummary.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.slide_up, 0);
                      } else {
                        ShowRewardsSummary();
                        btRewardsSummary.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.slide_down, 0);
                      }
                    }
                }
            }
          }));
        }

        SetRewardsSummaryMonthYear();
        // Starts Send a tip Activity
        Button btSendATip = (Button)root.findViewById(R.id.send_a_tip);
        if (btSendATip != null) {
          btSendATip.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChromeApplication app = (ChromeApplication)ContextUtils.getApplicationContext();
                Intent intent = new Intent(app, BraveRewardsSiteBannerActivity.class);
                intent.putExtra(BraveRewardsSiteBannerActivity.TAB_ID_EXTRA, currentTabId);
                mActivity.startActivity(intent);
            }
          }));
        }
    }

    private void SetRewardsSummaryMonthYear() {
        if (btRewardsSummary == null) {
            return;
        }
        String currentMonth = BraveRewardsHelper.getCurrentMonth(this.root.getResources());
        String currentYear = BraveRewardsHelper.getCurrentYear(this.root.getResources());
        String rewardsText = this.root.getResources().getString(R.string.brave_ui_rewards_summary);
        rewardsText += "\n" + String.format(this.root.getResources().getString(R.string.brave_ui_month_year), currentMonth,
            currentYear);
        btRewardsSummary.setText(rewardsText);
        btRewardsSummary.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.slide_down, 0);
    }

    private void RemoveRewardsSummaryMonthYear() {
        if (btRewardsSummary == null) {
            return;
        }
        btRewardsSummary.setText(this.root.getResources().getString(R.string.brave_ui_rewards_summary));
        btRewardsSummary.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.slide_up, 0);
    }

    protected void onShow() {}

    private void preShow() {
        if(this.root == null) {
            throw new IllegalStateException("setContentView was not called with a view to display.");
        }
        onShow();

        this.window.setTouchable(true);
        this.window.setFocusable(true);
        this.window.setOutsideTouchable(true);

        this.window.setContentView(this.root);
    }

    public void setContentView(View root) {
        this.root = root;
        this.window.setContentView(root);
    }

    public void showLikePopDownMenu() {
        this.showLikePopDownMenu(0, 0);
    }

    public void showLikePopDownMenu(int xOffset, int yOffset) {
        this.preShow();

        this.window.setAnimationStyle(R.style.OverflowMenuAnim);

        if (SysUtils.isLowEndDevice()) {
            this.window.setAnimationStyle(0);
        }

        this.window.showAsDropDown(this.anchor, xOffset, yOffset);
    }

    public void dismiss() {
        this.window.dismiss();
    }

    private Tab launchTabInRunningTabbedActivity(LoadUrlParams loadUrlParams) {
        if (mActivity == null || mActivity.getTabModelSelector() == null) return null;

        TabModelSelectorImpl tabbedModeTabModelSelector = (TabModelSelectorImpl) mActivity.getTabModelSelector();
        Tab tab = tabbedModeTabModelSelector.openNewTab(
                loadUrlParams, TabLaunchType.FROM_BROWSER_ACTIONS, null, false);
        assert tab != null;

        return tab;
    }



    public void ShowRewardsSummary() {
      if (mBraveRewardsNativeWorker != null) {
        mBraveRewardsNativeWorker.GetCurrentBalanceReport();
      }
    }

    public void ShowWebSiteView() {
      ((TextView)this.root.findViewById(R.id.br_bat_wallet)).setText(String.format("%.2f", 0.0));
      String usdText = String.format(this.root.getResources().getString(R.string.brave_ui_usd), "0.00");
      ((TextView)this.root.findViewById(R.id.br_usd_wallet)).setText(usdText);

      ScrollView sv = (ScrollView)this.root.findViewById(R.id.activity_brave_rewards_panel);
      sv.setVisibility(View.GONE);
      ScrollView sv_new = (ScrollView)this.root.findViewById(R.id.sv_no_website);
      sv_new.setVisibility(View.VISIBLE);
      CreateUpdateBalanceTask();
      ShowRewardsSummary();
      Tab currentActiveTab = BraveRewardsHelper.currentActiveTab();
      if (currentActiveTab != null && !currentActiveTab.isIncognito()) {
        String url = currentActiveTab.getUrl();
        if (URLUtil.isValidUrl(url)) {
            mBraveRewardsNativeWorker.GetPublisherInfo(currentActiveTab.getId(), url);
        }
      }
    }

    @Override
    public void OnWalletInitialized(int error_code) {
      if (error_code == 0) {
        // Wallet created
        ShowWebSiteView();
      } else if (error_code == 1) {
        // TODO error handling
      }
    }

    @Override
    public void OnWalletProperties(int error_code) {
      if (error_code == 0) {
        if (mBraveRewardsNativeWorker != null) {
          double balance = mBraveRewardsNativeWorker.GetWalletBalance();
          ((TextView)this.root.findViewById(R.id.br_bat_wallet)).setText(
            String.format("%.2f", balance));
          double usdValue = balance * mBraveRewardsNativeWorker.GetWalletRate("USD");
          String usdText = String.format(this.root.getResources().getString(R.string.brave_ui_usd), 
            String.format("%.2f", usdValue));
          ((TextView)this.root.findViewById(R.id.br_usd_wallet)).setText(usdText);
        }
      } else if (error_code == 1) {
        // TODO error handling
      } 
    }


    //onFaviconAvailable implementation of FaviconHelper.FaviconImageCallback
    //it has to set up the received icon on UI thread
    @CalledByNative
    @Override
    public void onFaviconAvailable(Bitmap image, String iconUrl) {
      final Bitmap bmp = image;
      mActivity.runOnUiThread(new Runnable() {
        @Override
        public void run() {
            ImageView iv = (ImageView)thisObject.root.findViewById(R.id.publisher_favicon);
            iv.setImageBitmap(bmp);
        }
      });
    }


    @Override
    public void OnPublisherInfo(int tabId) {
        publisherExist = true;
        currentTabId = tabId;
        RemoveRewardsSummaryMonthYear();

        String publisherURL = mBraveRewardsNativeWorker.GetPublisherURL(currentTabId);
        String publisherFavIconURL = mBraveRewardsNativeWorker.GetPublisherFavIconURL(currentTabId);
        BraveRewardsHelper.retrieveFavIcon(mFavIconHelper, this, publisherURL, publisherFavIconURL);


        //LinearLayout ll = (LinearLayout)this.root.findViewById(R.id.no_website_summary);
        //ll.setVisibility(View.GONE);
        GridLayout gl = (GridLayout)this.root.findViewById(R.id.website_summary_grid);
        gl.setVisibility(View.VISIBLE);
        LinearLayout ll = (LinearLayout)this.root.findViewById(R.id.br_central_layout);
        ll.setBackgroundColor(Color.WHITE);

        String pubName = thisObject.mBraveRewardsNativeWorker.GetPublisherName(currentTabId);
        String pubId = thisObject.mBraveRewardsNativeWorker.GetPublisherId(currentTabId);
        String pubSuffix = "";
        if (pubId.startsWith(YOUTUBE_TYPE)) {
            pubSuffix = thisObject.root.getResources().getString(R.string.brave_ui_on_youtube);
        } else if (pubName.startsWith(TWITCH_TYPE)) {
            pubSuffix = thisObject.root.getResources().getString(R.string.brave_ui_on_twitch);
        }
        pubName = "<b>" + pubName + "</b> " + pubSuffix;
        TextView tv = (TextView)thisObject.root.findViewById(R.id.publisher_name);
        tv.setText(Html.fromHtml(pubName));
        tv = (TextView)thisObject.root.findViewById(R.id.publisher_attention);
        String percent = Integer.toString(thisObject.mBraveRewardsNativeWorker.GetPublisherPercent(currentTabId)) + "%";
        tv.setText(percent);
        if (btAutoContribute != null) {
            btAutoContribute.setOnCheckedChangeListener(null);
            btAutoContribute.setChecked(!thisObject.mBraveRewardsNativeWorker.GetPublisherExcluded(currentTabId));
            btAutoContribute.setOnCheckedChangeListener(autoContributeSwitchListener);
        }
        if (thisObject.mBraveRewardsNativeWorker.GetPublisherVerified(currentTabId)) {
            tv = (TextView)root.findViewById(R.id.publisher_verified);
            tv.setVisibility(View.VISIBLE);
        }
        tv = (TextView)root.findViewById(R.id.br_no_activities_yet);
        gl = (GridLayout)thisObject.root.findViewById(R.id.br_activities);
        if (tv != null && gl != null) {
          tv.setVisibility(View.GONE);
          gl.setVisibility(View.GONE);
        }
    }

    @Override
    public void OnGetCurrentBalanceReport(String[] report) {
        boolean no_activity = true;
        for (int i = 0; i < report.length; i++) {
          TextView tv = null;
          TextView tvUSD = null;
          String text = "";
          String textUSD = "";
          String value = BraveRewardsHelper.probiToNumber(report[i]);
          double usdValue = Double.parseDouble(value) * mBraveRewardsNativeWorker.GetWalletRate("USD");
          switch (i) {
            case 0:
            case 1:
            case 2:
              break;
            case 3:
              tv = (TextView)root.findViewById(R.id.br_grants_claimed_bat);
              tvUSD = (TextView)root.findViewById(R.id.br_grants_claimed_usd);
              text = "<font color=#8E2995>" + value + "</font><font color=#000000> BAT</font>";
              textUSD = String.format("%.2f", usdValue) + " USD";
              break;
            case 4:
              tv = (TextView)root.findViewById(R.id.br_earnings_ads_bat);
              tvUSD = (TextView)root.findViewById(R.id.br_earnings_ads_usd);
              text = "<font color=#8E2995>" + value + "</font><font color=#000000> BAT</font>";
              textUSD = String.format("%.2f", usdValue) + " USD";
              break;
            case 5:
              tv = (TextView)root.findViewById(R.id.br_auto_contribute_bat);
              tvUSD = (TextView)root.findViewById(R.id.br_auto_contribute_usd);
              text = "<font color=#6537AD>" + value + "</font><font color=#000000> BAT</font>";
              textUSD = String.format("%.2f", usdValue) + " USD";
              break;
            case 6:
              tv = (TextView)root.findViewById(R.id.br_recurring_donation_bat);
              tvUSD = (TextView)root.findViewById(R.id.br_recurring_donation_usd);
              text = "<font color=#392DD1>" + value + "</font><font color=#000000> BAT</font>";
              textUSD = String.format("%.2f", usdValue) + " USD";
              break;
            case 7:
              tv = (TextView)root.findViewById(R.id.br_one_time_donation_bat);
              tvUSD = (TextView)root.findViewById(R.id.br_one_time_donation_usd);
              text = "<font color=#392DD1>" + value + "</font><font color=#000000> BAT</font>";
              textUSD = String.format("%.2f", usdValue) + " USD";
              break;
            case 8:
              break;
          }
          if (tv != null && tvUSD != null &&
              !text.isEmpty() && !textUSD.isEmpty()) {
            Context appContext = ContextUtils.getApplicationContext();
            Spanned toInsert;
            if (appContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                toInsert = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY);
            } else {
                toInsert = Html.fromHtml(text);
            }
            tv.setText(toInsert);
            tvUSD.setText(textUSD);
          }
          if (!report[i].equals("0")) {
            no_activity = false;
          }
        }
        TextView tv = (TextView)root.findViewById(R.id.br_no_activities_yet);
        GridLayout gr= (GridLayout)root.findViewById(R.id.br_activities);
        if (tv != null && gr != null) {
          if (no_activity) {
              tv.setVisibility(View.VISIBLE);
              gr.setVisibility(View.GONE);
          } else {
              tv.setVisibility(View.GONE);
              gr.setVisibility(View.VISIBLE);
          }
        }
    }

    @Override
    public void OnNotificationAdded(String id, int type, int timestamp,
          String[] args) { 
        // TODO show it in the panel
    }

    @Override
    public void OnNotificationsCount(int count) {}
}

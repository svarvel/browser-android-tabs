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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.widget.Toast;


import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.ApplicationStatus;
import org.chromium.base.task.AsyncTask;
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
import java.util.Calendar;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;


public class BraveRewardsPanelPopup implements BraveRewardsObserver, FaviconHelper.FaviconImageCallback {
    private static final int UPDATE_BALANCE_INTERVAL = 60000;  // In milliseconds
    private static final int FAVICON_FETCH_COUNT = 3;
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
    private String currentNotificationId;
    private TextView tvPublisherNotVerified;
    private TextView tvPublisherNotVerifiedSummary;
    private int currentFavIconFetch;
    private boolean walletInitialized;
    AnimationDrawable wallet_init_animation;

    public BraveRewardsPanelPopup(View anchor) {
        currentNotificationId = "";
        publisherExist = false;
        currentTabId = -1;
        this.anchor = anchor;
        this.window = new PopupWindow(anchor.getContext());
        this.window.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        this.window.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
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

                mActivity.OnRewardsPanelDismiss();
            }
        });
        mActivity = BraveRewardsHelper.GetChromeTabbedActivity();
        mBraveRewardsNativeWorker = BraveRewardsNativeWorker.getInstance();
        if (mBraveRewardsNativeWorker != null) {
          mBraveRewardsNativeWorker.AddObserver(thisObject);
        }
        balanceUpdater = new Timer();
        mFavIconHelper = new FaviconHelper();
        currentFavIconFetch = 0;

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
        tvPublisherNotVerifiedSummary = (TextView)root.findViewById(R.id.publisher_not_verified_summary);
        tvPublisherNotVerifiedSummary.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    int offset = tvPublisherNotVerifiedSummary.getOffsetForPosition(
                      motionEvent.getX(), motionEvent.getY());
                    findWord(tvPublisherNotVerifiedSummary.getText().toString(), offset);
                }
                return false;
            }

            private void findWord(String text, int offset) {
                int firstOffset = text.indexOf(". ");
                if (offset > firstOffset) {
                    // We are on learn more
                    launchTabInRunningTabbedActivity(new LoadUrlParams(ChromeTabbedActivity.REWARDS_LEARN_MORE_URL));
                    dismiss();
                }
            }
        });
        if (mBraveRewardsNativeWorker != null && mBraveRewardsNativeWorker.WalletExist()) {
            walletInitialized = true;
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
                wallet_init_animation = (AnimationDrawable)btJoinRewards.getCompoundDrawables()[2];
                if (wallet_init_animation != null) {
                    wallet_init_animation.start();
                }
            }
          }));
        }
        tvLearnMore = (TextView)root.findViewById(R.id.learn_more_id);
        if (tvLearnMore != null) {
          tvLearnMore.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchTabInRunningTabbedActivity(new LoadUrlParams(ChromeTabbedActivity.REWARDS_SETTINGS_URL));
                dismiss();
            }
          }));
        }

        btAddFunds = (Button)root.findViewById(R.id.br_add_funds);
        if (btAddFunds != null) {
          btAddFunds.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchTabInRunningTabbedActivity(new LoadUrlParams(ChromeTabbedActivity.ADD_FUNDS_URL));
                dismiss();
            }
          }));
        }
        btRewardsSettings = (Button)root.findViewById(R.id.br_rewards_settings);
        if (btRewardsSettings != null) {
            btRewardsSettings.setOnClickListener((new View.OnClickListener() {
              @Override
              public void onClick(View v) {
                  launchTabInRunningTabbedActivity(new LoadUrlParams(ChromeTabbedActivity.REWARDS_SETTINGS_URL));
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

        Button btGrants = (Button)this.root.findViewById(R.id.grants_dropdown);
        if (btGrants != null) {
            btGrants.setOnClickListener((new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ListView listView = (ListView)thisObject.root.findViewById(R.id.grants_listview);
                    Button btGrants = (Button)thisObject.root.findViewById(R.id.grants_dropdown);
                    if (listView == null || btGrants == null) {
                      return;
                    }
                    if (listView.getVisibility() == View.VISIBLE) {
                      btGrants.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.down_icon, 0);
                      listView.setVisibility(View.GONE);
                    } else {
                      btGrants.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.up_icon, 0);
                      listView.setVisibility(View.VISIBLE);
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
                mActivity.startActivityForResult(intent,ChromeTabbedActivity.SITE_BANNER_REQUEST_CODE);
            }
          }));
        }
        tvPublisherNotVerified = (TextView)root.findViewById(R.id.publisher_not_verified);
        tvPublisherNotVerified.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    int offset = tvPublisherNotVerified.getOffsetForPosition(
                      motionEvent.getX(), motionEvent.getY());
                    findWord(tvPublisherNotVerified.getText().toString(), offset);
                }
                return false;
            }

            private void findWord(String text, int offset) {
                int firstOffset = text.indexOf(".\n");
                int secondOffset = text.indexOf(". ");
                if (offset > firstOffset) {
                    // We are on change auto-contribute settings
                    launchTabInRunningTabbedActivity(new LoadUrlParams(ChromeTabbedActivity.REWARDS_SETTINGS_URL));
                    dismiss();
                } else if (offset <= firstOffset && offset > secondOffset) {
                    // We are on learn more
                    launchTabInRunningTabbedActivity(new LoadUrlParams(ChromeTabbedActivity.REWARDS_LEARN_MORE_URL));
                    dismiss();
                }
            }
        });

        SetupNotificationsControls();
    }

    private void SetupNotificationsControls() {
        // Check for notifications
        if (mBraveRewardsNativeWorker != null) {
            mBraveRewardsNativeWorker.GetAllNotifications();
        }

        TextView tvCloseNotification = (TextView)root.findViewById(R.id.br_notification_close);
        if (tvCloseNotification != null) {
            tvCloseNotification.setOnClickListener((new View.OnClickListener() {
              @Override
              public void onClick(View v) {
                  if (currentNotificationId.isEmpty()) {
                    assert false;

                    return;
                  }
                  if (mBraveRewardsNativeWorker != null) {
                      mBraveRewardsNativeWorker.DeleteNotification(currentNotificationId);
                  }
              }
            }));
        }
        Button btClaimOk = (Button)root.findViewById(R.id.br_claim_button);
        if (btClaimOk != null) {
          btClaimOk.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              Button claimOk = (Button)v;
              if (claimOk.getText().toString().equals(
                root.getResources().getString(R.string.ok))) {
                  if (mBraveRewardsNativeWorker != null) {
                      mBraveRewardsNativeWorker.DeleteNotification(currentNotificationId);
                  }
              }
              else if (mBraveRewardsNativeWorker != null) {
                  mBraveRewardsNativeWorker.GetGrant();
              }
            }
          }));
        }
    }

    private void SetRewardsSummaryMonthYear() {
        if (btRewardsSummary == null) {
            return;
        }
        String currentMonth = BraveRewardsHelper.getCurrentMonth(Calendar.getInstance(),
          this.root.getResources(), true);
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

    private void ShowNotification(String id, int type, long timestamp,
            String[] args) {
        currentNotificationId = id;
        LinearLayout ll = (LinearLayout)root.findViewById(R.id.header_layout);
        ll.setBackgroundResource(R.drawable.notification_header);
        GridLayout gl = (GridLayout)root.findViewById(R.id.wallet_info_gridlayout);
        gl.setVisibility(View.GONE);
        ll = (LinearLayout)root.findViewById(R.id.notification_info_layout);
        ll.setVisibility(View.VISIBLE);
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Calendar calTime = Calendar.getInstance(utc);
        calTime.setTimeInMillis(timestamp * 1000);
        String currentMonth = BraveRewardsHelper.getCurrentMonth(calTime,
          root.getResources(), false);
        String currentDay = Integer.toString(calTime.get(Calendar.DAY_OF_MONTH));
        String notificationTime = currentMonth + " " + currentDay;
        String title = "";
        String description = "";
        Button btClaimOk = (Button)root.findViewById(R.id.br_claim_button);
        ImageView notification_icon = (ImageView)root.findViewById(R.id.br_notification_icon);
        // TODO other types of notifications
        switch (type) {
          case 1:
            // Auto contribution successful
            btClaimOk.setText(root.getResources().getString(R.string.ok));
            notification_icon.setImageResource(R.drawable.contribute_icon);
            title = root.getResources().getString(R.string.brave_ui_rewards_contribute);
            if (args.length >= 4) {
              String result = args[1];
              // Results
              // 0 - success
              // 1 - general error
              // 15 - not enough funds
              // 16 - error while tipping
              switch (result) {
                case "0":
                  description = String.format(
                    root.getResources().getString(R.string.brave_ui_rewards_contribute_description),
                    BraveRewardsHelper.probiToNumber(args[3]), args[2]);
                  break;
                case "15":
                  description = 
                    root.getResources().getString(R.string.brave_ui_notification_desc_no_funds);
                  break;
                case "16":
                  description = 
                    root.getResources().getString(R.string.brave_ui_notification_desc_tip_error);
                  break;
                default:
                  description = 
                    root.getResources().getString(R.string.brave_ui_notification_desc_contr_error);
              }
            } else {
              assert false;
            }
            break;
          case 2:
            // Grants notification
            btClaimOk.setText(root.getResources().getString(R.string.brave_ui_claim));
            notification_icon.setImageResource(R.drawable.grant_icon);
            title = root.getResources().getString(R.string.brave_ui_new_token_grant);
            description = root.getResources().getString(R.string.brave_ui_new_grant);
            break;
        }
        String stringToInsert = "<b>" + title + "</b>" + " | " + description + 
          "  <font color=#a9aab4>" + notificationTime + "</font>";
        Spanned toInsert;
        Context appContext = ContextUtils.getApplicationContext();
        if (appContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            toInsert = Html.fromHtml(stringToInsert, Html.FROM_HTML_MODE_LEGACY);
        } else {
            toInsert = Html.fromHtml(stringToInsert);
        }
        TextView tv = (TextView)root.findViewById(R.id.br_notification_description);
        tv.setText(toInsert);
    }

    private void DismissNotification(String id) {
        if (!currentNotificationId.equals(id)) {
          return;
        }
        LinearLayout ll = (LinearLayout)root.findViewById(R.id.header_layout);
        ll.setBackgroundResource(R.drawable.header);
        GridLayout gl = (GridLayout)root.findViewById(R.id.wallet_info_gridlayout);
        gl.setVisibility(View.VISIBLE);
        ll = (LinearLayout)root.findViewById(R.id.notification_info_layout);
        ll.setVisibility(View.GONE);
        currentNotificationId = "";
        if (mBraveRewardsNativeWorker != null) {
            mBraveRewardsNativeWorker.GetAllNotifications();
        }
    }

    @Override
    public void OnWalletInitialized(int error_code) {
      if (error_code == 0) {
        // Wallet created
          walletInitialized = true;
          ShowWebSiteView();
      }
      else {
          Button btJoinRewards = (Button)BraveRewardsPanelPopup.this.root.findViewById(R.id.join_rewards_id);
          btJoinRewards.setText(BraveRewardsPanelPopup.this.root.getResources().getString(R.string.brave_ui_welcome_button_text_two));
          btJoinRewards.setClickable(true);
          btJoinRewards.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
          if (wallet_init_animation != null) {
              wallet_init_animation.stop();
          }

          Context context = ContextUtils.getApplicationContext();
          Toast toast = Toast.makeText(context, root.getResources().getString(R.string.brave_ui_error_init_wallet), Toast.LENGTH_SHORT);
          toast.show();

      }
        wallet_init_animation = null;
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

          int currentGrantsCount = mBraveRewardsNativeWorker.GetCurrentGrantsCount();
          Button btGrants = (Button)this.root.findViewById(R.id.grants_dropdown);
          if (currentGrantsCount != 0) {
              btGrants.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.down_icon, 0);
              btGrants.setVisibility(View.VISIBLE);

              ListView listView = (ListView)this.root.findViewById(R.id.grants_listview);
        
              ArrayAdapter<Spanned> adapter = new ArrayAdapter<Spanned>(
                ContextUtils.getApplicationContext(), R.layout.brave_rewards_grants_list_item);
              for (int i = 0; i < currentGrantsCount; i++) {
                  String[] grant = mBraveRewardsNativeWorker.GetCurrentGrant(i);
                  if (grant.length < 2) {
                    continue;
                  }
                  String toInsert = "<b><font color=#ffffff>" + BraveRewardsHelper.probiToNumber(grant[0]) + " BAT</font></b> ";
                  Log.i("TAG", "!!!grant[1] == " + grant[1]);
                  TimeZone utc = TimeZone.getTimeZone("UTC");
                  Calendar calTime = Calendar.getInstance(utc);
                  calTime.setTimeInMillis(Long.parseLong(grant[1]) * 1000);
                  String date = Integer.toString(calTime.get(Calendar.MONTH) + 1) + "/" +
                      Integer.toString(calTime.get(Calendar.DAY_OF_MONTH)) + "/" +
                      Integer.toString(calTime.get(Calendar.YEAR));
                  toInsert += String.format(this.root.getResources().getString(R.string.brave_ui_grant_info), 
                    date);

                  Context appContext = ContextUtils.getApplicationContext();
                  if (appContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                      adapter.add(Html.fromHtml(toInsert, Html.FROM_HTML_MODE_LEGACY));
                  } else {
                      adapter.add(Html.fromHtml(toInsert));
                  }                  
              }
              listView.setAdapter(adapter);
          } else {
            btGrants.setVisibility(View.GONE);
          }
        }
      } else if (error_code == 1) {
        // TODO error handling
      } 
    }


    // onFaviconAvailable implementation of FaviconHelper.FaviconImageCallback
    // it has to set up the received icon on UI thread
    @CalledByNative
    @Override
    public void onFaviconAvailable(Bitmap image, String iconUrl) {
      final Bitmap bmp = image;
      mActivity.runOnUiThread(new Runnable() {
        @Override
        public void run() {
            if (bmp != null) {
                ImageView iv = (ImageView)thisObject.root.findViewById(R.id.publisher_favicon);
                iv.setImageBitmap(bmp);
            } else if (currentFavIconFetch < FAVICON_FETCH_COUNT) {
                currentFavIconFetch++;
                String publisherURL = mBraveRewardsNativeWorker.GetPublisherURL(currentTabId);
                String publisherFavIconURL = mBraveRewardsNativeWorker.GetPublisherFavIconURL(currentTabId);
                BraveRewardsHelper.retrieveFavIcon(mFavIconHelper, thisObject, publisherURL, publisherFavIconURL);
            }
        }
      });
    }


    @Override
    public void OnPublisherInfo(int tabId) {
        currentFavIconFetch = 0;
        publisherExist = true;
        currentTabId = tabId;
        RemoveRewardsSummaryMonthYear();

        String publisherURL = mBraveRewardsNativeWorker.GetPublisherURL(currentTabId);
        String publisherFavIconURL = mBraveRewardsNativeWorker.GetPublisherFavIconURL(currentTabId);
        BraveRewardsHelper.retrieveFavIcon(mFavIconHelper, this, publisherURL, publisherFavIconURL);

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
        String verified_text = "";
        TextView tvVerified = (TextView)root.findViewById(R.id.publisher_verified);
        if (thisObject.mBraveRewardsNativeWorker.GetPublisherVerified(currentTabId)) {
            verified_text = root.getResources().getString(R.string.brave_ui_verified_publisher);
        } else {
            verified_text = root.getResources().getString(R.string.brave_ui_not_verified_publisher);
            tv = (TextView)root.findViewById(R.id.publisher_not_verified);
            String verified_description = 
                root.getResources().getString(R.string.brave_ui_not_verified_publisher_description);
            verified_description += " <font color=#73CBFF>" + 
              root.getResources().getString(R.string.learn_more) + ".</font><br/>";
            verified_description += "<b>" + 
              root.getResources().getString(R.string.brave_ui_change_auto_contribution) + "</b>";
            Context appContext = ContextUtils.getApplicationContext();
            Spanned toInsert;
            if (appContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                toInsert = Html.fromHtml(verified_description, Html.FROM_HTML_MODE_LEGACY);
            } else {
                toInsert = Html.fromHtml(verified_description);
            }
            tv.setText(toInsert);
            tv.setVisibility(View.VISIBLE);
            tvVerified.setCompoundDrawablesWithIntrinsicBounds(R.drawable.icn_unverified, 0, 0, 0);
        }
        tvVerified.setText(verified_text);
        tvVerified.setVisibility(View.VISIBLE);

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
        // TODO debug, get real value of BAT designated to non verified publishers
        String non_verified_summary = 
          String.format(root.getResources().getString(
            R.string.brave_ui_not_verified_publisher_summary), "52") + 
          " <font color=#73CBFF>" + root.getResources().getString(R.string.learn_more) + 
          ".</font>";;
        Context appContext = ContextUtils.getApplicationContext();
        Spanned toInsert;
        if (appContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            toInsert = Html.fromHtml(non_verified_summary, Html.FROM_HTML_MODE_LEGACY);
        } else {
            toInsert = Html.fromHtml(non_verified_summary);
        }
        tvPublisherNotVerifiedSummary.setText(toInsert);
        //
        TextView tv = (TextView)root.findViewById(R.id.br_no_activities_yet);
        GridLayout gr = (GridLayout)root.findViewById(R.id.br_activities);
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
    public void OnNotificationAdded(String id, int type, long timestamp,
          String[] args) { 
        // Do nothing here as we will receive the most recent notification
        // in OnGetLatestNotification
    }

    @Override
    public void OnNotificationsCount(int count) {}

    @Override
    public void OnGetLatestNotification(String id, int type, long timestamp,
            String[] args) {
        ShowNotification(id, type, timestamp, args);
    }

    @Override
    public void OnNotificationDeleted(String id) {
        DismissNotification(id);
        mBraveRewardsNativeWorker.GetWalletProperties();
    }
}

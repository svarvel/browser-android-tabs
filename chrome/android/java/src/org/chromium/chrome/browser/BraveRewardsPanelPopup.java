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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

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

import java.io.IOException;
import java.net.MalformedURLException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;


//TODO: test imports/////////////////////////////
import org.chromium.chrome.browser.BraveRewardsDonationSentActivity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import org.chromium.base.ContextUtils;
//////////////////////////////////////////////



public class BraveRewardsPanelPopup implements BraveRewardsObserver {
    private static final int UPDATE_BALANCE_INTERVAL = 60000;  // In milliseconds
    private static final String FAV_ICON_URL = "chrome://favicon/size/48@2x/";

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

    public BraveRewardsPanelPopup(View anchor) {
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
                if (mBraveRewardsNativeWorker != null) {
                  mBraveRewardsNativeWorker.RemoveObserver(thisObject);
                }
                if (mFavIconHelper != null) {
                  mFavIconHelper.destroy();
                }
                if (currentTabId != -1) {
                  mBraveRewardsNativeWorker.RemovePublisherFromMap(currentTabId);
                }
            }
        });
        mActivity = BraveRewardsHelper.GetChromeTabbedActivity();
        mBraveRewardsNativeWorker = mActivity.getBraveRewardsNativeWorker();
        if (mBraveRewardsNativeWorker != null) {
          mBraveRewardsNativeWorker.Init();
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

       //TODO: test buttons onClick handlers/////////////////////////////////////////////
       /*Button btTestTipSent = (Button)root.findViewById(R.id.brave_ui_tip_sent_test_button);
        if (btTestTipSent != null) {
          btTestTipSent.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChromeApplication app = (ChromeApplication)ContextUtils.getApplicationContext();
                Intent intent = new Intent(app, BraveRewardsDonationSentActivity.class);
                mActivity.startActivity(intent);
            }
          }));
        }

        Button btTestSiteBanner = (Button)root.findViewById(R.id.brave_ui_site_banner_test_button);
        if (btTestSiteBanner != null) {
          btTestSiteBanner.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChromeApplication app = (ChromeApplication)ContextUtils.getApplicationContext();
                Intent intent = new Intent(app, BraveRewardsSiteBannerActivity.class);
                mActivity.startActivity(intent);
            }
          }));
        }*/
       ///////////////////////////////////////////////////////////////////////////////////////

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

    private Tab currentActiveTab() {
      if (mActivity == null || mActivity.getTabModelSelector() == null) return null;

      return mActivity.getActivityTab();
        //TabModelSelectorImpl tabbedModeTabModelSelector = (TabModelSelectorImpl) mActivity.getTabModelSelector();
        //TabModelImpl model = (TabModelImpl)tabbedModeTabModelSelector.getModel();
    }

    public void ShowWebSiteView() {
      ((TextView)this.root.findViewById(R.id.br_bat_wallet)).setText(String.format("%.2f", 0.0));
      String usdText = String.format(this.root.getResources().getString(R.string.brave_ui_usd), "0.00");
      ((TextView)this.root.findViewById(R.id.br_usd_wallet)).setText(usdText);
      String currentMonth = BraveRewardsHelper.getCurrentMonth(this.root.getResources());
      String currentYear = BraveRewardsHelper.getCurrentYear(this.root.getResources());
      ((TextView)this.root.findViewById(R.id.rewards_summary_month)).setText(
        String.format(this.root.getResources().getString(R.string.brave_ui_month_year), currentMonth,
          currentYear));

      ScrollView sv = (ScrollView)this.root.findViewById(R.id.activity_brave_rewards_panel);
      sv.setVisibility(View.GONE);
      ScrollView sv_new = (ScrollView)this.root.findViewById(R.id.sv_no_website);
      sv_new.setVisibility(View.VISIBLE);
      CreateUpdateBalanceTask();
      Tab currentActiveTab = currentActiveTab();
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

    public class FaviconFetcher implements FaviconHelper.FaviconImageCallback {
        @CalledByNative
        @Override
        public void onFaviconAvailable(Bitmap image, String iconUrl) {
          final Bitmap bmp = image;
          mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SetFavIcon(bmp);
            }
          });
        }
    }

    private void SetFavIcon(Bitmap bmp) {
        ImageView iv = (ImageView)thisObject.root.findViewById(R.id.publisher_favicon);
        iv.setImageBitmap(bmp);
    }

    @Override
    public void OnPublisherInfo(int tabId) {
        currentTabId = tabId;
        (new Runnable() {
            @Override
            public void run() {
                String faviconURL = thisObject.mBraveRewardsNativeWorker.GetPublisherFavIconURL(currentTabId);
                if (faviconURL.isEmpty()) {
                    Tab currentActiveTab = currentActiveTab();
                    mFavIconHelper.getLocalFaviconImageForURL(currentActiveTab.getProfile(),
                      thisObject.mBraveRewardsNativeWorker.GetPublisherURL(currentTabId),
                      64, new FaviconFetcher());
                } else {
                    new AsyncTask<Void>() {
                      @Override
                      protected Void doInBackground() {
                          try {
                              Log.i("TAG", "!!!faviconURL == " + faviconURL);
                              URL url = new URL(faviconURL);
                              Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
                              mActivity.runOnUiThread(new Runnable() {
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

        LinearLayout ll = (LinearLayout)this.root.findViewById(R.id.no_website_summary);
        ll.setVisibility(View.GONE);
        ll = (LinearLayout)this.root.findViewById(R.id.website_summary);
        ll.setVisibility(View.VISIBLE);
        ll = (LinearLayout)this.root.findViewById(R.id.br_central_layout);
        ll.setBackgroundColor(Color.WHITE);

        String pubName = thisObject.mBraveRewardsNativeWorker.GetPublisherName(currentTabId);
        TextView tv = (TextView)thisObject.root.findViewById(R.id.publisher_name);
        tv.setText(pubName);
        tv = (TextView)thisObject.root.findViewById(R.id.publisher_attention);
        String percent = Integer.toString(thisObject.mBraveRewardsNativeWorker.GetPublisherPercent(currentTabId)) + "%";
        tv.setText(percent);
        if (btAutoContribute != null) {
            btAutoContribute.setOnCheckedChangeListener(null);
            btAutoContribute.setChecked(!thisObject.mBraveRewardsNativeWorker.GetPublisherExcluded(currentTabId));
            btAutoContribute.setOnCheckedChangeListener(autoContributeSwitchListener);
        }
    }
}

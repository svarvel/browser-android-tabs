/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import org.chromium.base.ApplicationStatus;
import org.chromium.base.Log;
import org.chromium.base.SysUtils;
import org.chromium.chrome.browser.BraveRewardsHelper;
import org.chromium.chrome.browser.BraveRewardsObserver;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorImpl;
import org.chromium.chrome.R;
import org.chromium.content_public.browser.LoadUrlParams;

import java.lang.ref.WeakReference;


public class BraveRewardsPanelPopup implements BraveRewardsObserver {
    protected final View anchor;
    private final PopupWindow window;
    private final BraveRewardsPanelPopup thisObject;
    private View root;
    private Button btJoinRewards;
    private Button btAddFunds;
    private Button btRewardsSettings;
    private TextView tvLearnMore;
    private BraveRewardsNativeWorker mBraveRewardsNativeWorker;

    public BraveRewardsPanelPopup(View anchor) {
        this.anchor = anchor;
        this.window = new PopupWindow(anchor.getContext());
        thisObject = this;

        this.window.setTouchInterceptor(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    BraveRewardsPanelPopup.this.window.dismiss();
                    return true;
                }
                return false;
            }
        });
        this.window.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                if (mBraveRewardsNativeWorker == null) {
                  return;
                }
                mBraveRewardsNativeWorker.RemoveObserver(thisObject);
            }
        });
        ChromeTabbedActivity activity = BraveRewardsHelper.GetChromeTabbedActivity();
        mBraveRewardsNativeWorker = activity.getBraveRewardsNativeWorker();
        if (mBraveRewardsNativeWorker != null) {
          mBraveRewardsNativeWorker.Init();
          mBraveRewardsNativeWorker.AddObserver(thisObject);
        }

        onCreate();
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
        ChromeTabbedActivity activity = BraveRewardsHelper.GetChromeTabbedActivity();
        if (activity == null || activity.getTabModelSelector() == null) return null;

        TabModelSelectorImpl tabbedModeTabModelSelector = (TabModelSelectorImpl) activity.getTabModelSelector();
        Tab tab = tabbedModeTabModelSelector.openNewTab(
                loadUrlParams, TabLaunchType.FROM_BROWSER_ACTIONS, null, false);
        assert tab != null;

        return tab;
    }

    public void ShowWebSiteView() {
      ((TextView)this.root.findViewById(R.id.br_bat_wallet)).setText("0.0");
      String usdText = String.format(this.root.getResources().getString(R.string.brave_ui_usd), "0.0");
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
    }

    @Override
    public void OnWalletInitialized(int error_code) {
      Log.i("TAG", "!!!in observer OnWalletInitialized error_code == " + error_code);

      if (error_code == 0) {
        // Wallet created
        ShowWebSiteView();
      } else if (error_code == 1) {
        // TODO error handling
      }
    }
}

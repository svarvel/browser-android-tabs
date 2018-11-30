/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupWindow;
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
        btJoinRewards = (Button)root.findViewById(R.id.join_rewards_id);
        if (btJoinRewards != null) {
          btJoinRewards.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("TAG", "!!!onClick bt");
                if (mBraveRewardsNativeWorker != null) {
                  mBraveRewardsNativeWorker.CreateWallet();
                }
            }
          }));
        }
        tvLearnMore = (TextView)root.findViewById(R.id.learn_more_id);
        if (tvLearnMore != null) {
          tvLearnMore.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("TAG", "!!!onClick tv");
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

    @Override
    public void OnWalletInitialized(int error_code) {
      Log.i("TAG", "!!!in observer OnWalletInitialized error_code == " + error_code);
    }
}

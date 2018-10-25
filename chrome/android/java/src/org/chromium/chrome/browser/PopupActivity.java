// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser;

import android.content.Intent;
import android.provider.Browser;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;

import org.chromium.base.Log;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.compositor.layouts.LayoutManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.util.IntentUtils;
import org.chromium.chrome.browser.widget.ControlContainer;
import org.chromium.content_public.browser.LoadUrlParams;

/**
 * An Activity used to display popup html page.
 */
public class PopupActivity extends SingleTabActivity {
    private static final String TAG = "PopupActivity";
    private static final String EXTRA_POPUP_Y_SHIFT = "com.android.brave.popup_y_shift";
    private static final int DEFAULT_POPUP_Y_SHIFT = 100;

    @Override
    public void preInflationStartup() {
        if (getWindow() != null) {
            WindowManager.LayoutParams windowParams = getWindow().getAttributes();
            windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
            windowParams.gravity = android.view.Gravity.END | android.view.Gravity.FILL_VERTICAL;
            getWindow().setAttributes(windowParams);
        }
        super.preInflationStartup();
    }

    @Override
    public void postInflationStartup() {
        super.postInflationStartup();

        if (getCompositorViewHolder() == null || getCompositorViewHolder().getCompositorView() == null) return;

        ViewParent compositor =  getCompositorViewHolder().getCompositorView().getParent();
        if (compositor instanceof ViewGroup) {
            setTransparentBackground((View)compositor);
            int y_shift = IntentUtils.safeGetIntExtra(getIntent(), EXTRA_POPUP_Y_SHIFT, DEFAULT_POPUP_Y_SHIFT);
            ((View)compositor).setPadding(0, y_shift, 10, 10);
            View parent = (View) compositor.getParent();
            if (parent != null) {
                parent.setClickable(true);
                parent.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        finish();
                    }
                });
            }
        }
    }

    private void setTransparentBackground(View v) {
        if (v == null) return;
        v.setAlpha(0f);
        ViewParent parent = v.getParent();
        if (parent instanceof View) {
            setTransparentBackground((View)parent);
        }
    }

    @Override
    protected Tab createTab() {
        Tab tab = new Tab(Tab.INVALID_TAB_ID, Tab.INVALID_TAB_ID, false, getWindowAndroid(),
                            TabModel.TabLaunchType.FROM_CHROME_UI, null, null);

        tab.initialize(null, getTabContentManager(), createTabDelegateFactory(), false, false);
        tab.loadUrl(new LoadUrlParams("chrome://rewards"));
        return tab;
    }

    public static void show(ChromeActivity activity) {
        int y_shift = DEFAULT_POPUP_Y_SHIFT;
        View anchor = activity.findViewById(R.id.toolbar_shadow);
        if (anchor != null) {
            int xy[] = new int[2];
            anchor.getLocationOnScreen(xy);
            y_shift = xy[1];
        }
        Intent intent = new Intent();
        intent.setClass(activity, PopupActivity.class);
        intent.putExtra(EXTRA_POPUP_Y_SHIFT, y_shift);
        intent.putExtra(IntentHandler.EXTRA_PARENT_COMPONENT, activity.getComponentName());
        intent.putExtra(Browser.EXTRA_APPLICATION_ID, activity.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        activity.startActivity(intent);
    }

    @Override
    protected void initializeToolbar() {}

    @Override
    protected int getControlContainerLayoutId() {
        return R.layout.fullscreen_control_container;
    }

    @Override
    public int getControlContainerHeightResource() {
        return R.dimen.fullscreen_activity_control_container_height;
    }

    @Override
    public void finishNativeInitialization() {
        ControlContainer controlContainer = (ControlContainer) findViewById(R.id.control_container);
        initializeCompositorContent(new LayoutManager(getCompositorViewHolder()),
                (View) controlContainer, (ViewGroup) findViewById(android.R.id.content),
                controlContainer);

        if (getFullscreenManager() != null) getFullscreenManager().setTab(getActivityTab());
        super.finishNativeInitialization();
    }
}

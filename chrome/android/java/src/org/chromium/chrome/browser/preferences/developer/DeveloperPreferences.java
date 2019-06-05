// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.developer;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.ChromeVersionInfo;
import org.chromium.chrome.browser.BraveRewardsNativeWorker;
import org.chromium.chrome.browser.BraveRewardsObserver;
import org.chromium.chrome.browser.BraveRewardsPanelPopup;
import org.chromium.chrome.browser.ConfigAPIs;
import org.chromium.chrome.browser.preferences.ChromeSwitchPreference;
import org.chromium.chrome.browser.preferences.PreferenceUtils;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.RestartWorker;
import org.chromium.components.version_info.Channel;
import org.chromium.components.version_info.VersionConstants;

import org.chromium.base.Log;
/**
 * Settings fragment containing preferences aimed at Chrome and web developers.
 */
public class DeveloperPreferences extends PreferenceFragment
        implements OnPreferenceChangeListener, BraveRewardsObserver {
    private static final String UI_PREF_BETA_STABLE_HINT = "beta_stable_hint";
    private static final String PREF_DEVELOPER_ENABLED = "developer";
    private static final String PREF_USE_REWARDS_STAGING_SERVER = "use_rewards_staging_server";

    // Non-translated strings:
    private static final String MSG_DEVELOPER_OPTIONS_TITLE = "Developer options";

    public static boolean shouldShowDeveloperPreferences() {
        // Always enabled on canary, dev and local builds, otherwise can be enabled by tapping the
        // Chrome version in Settings>About multiple times.
        // if (VersionConstants.CHANNEL <= Channel.DEV) return true;
        return ContextUtils.getAppSharedPreferences().getBoolean(PREF_DEVELOPER_ENABLED, false);
    }

    public static void setDeveloperPreferencesEnabled() {
        ContextUtils.getAppSharedPreferences()
                .edit()
                .putBoolean(PREF_DEVELOPER_ENABLED, true)
                .apply();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(MSG_DEVELOPER_OPTIONS_TITLE);
        PreferenceUtils.addPreferencesFromResource(this, R.xml.developer_preferences);

        if (ChromeVersionInfo.isBetaBuild() || ChromeVersionInfo.isStableBuild()) {
            getPreferenceScreen().removePreference(findPreference(UI_PREF_BETA_STABLE_HINT));
        }

        ChromeSwitchPreference stagingServer =
                (ChromeSwitchPreference) findPreference(PREF_USE_REWARDS_STAGING_SERVER);
        stagingServer.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onStart() {
        BraveRewardsNativeWorker.getInstance().AddObserver(this);
        super.onStart();
    }

    @Override
    public void onStop() {
        BraveRewardsNativeWorker.getInstance().RemoveObserver(this);
        super.onStop();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        checkQACode(preference, newValue);
        return true;
    }

    private void checkQACode(Preference preference, Object newValue) {
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.qa_code_check, null);
        final EditText input = (EditText) view.findViewById(R.id.qa_code);

        DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int button) {
                if (button == AlertDialog.BUTTON_POSITIVE &&
                    input.getText().toString().equals(ConfigAPIs.QA_CODE)) {
                    if (PREF_USE_REWARDS_STAGING_SERVER.equals(preference.getKey())) {
                        PrefServiceBridge.getInstance().setUseRewardsStagingServer((boolean) newValue);
                        BraveRewardsNativeWorker.getInstance().ResetTheWholeState();
                        RestartWorker.AskForRelaunch(getActivity());
                    }
                } else {
                    if (preference instanceof ChromeSwitchPreference) {
                        ((ChromeSwitchPreference)preference).setChecked(!(boolean)newValue);
                    }
                }
            }
        };

        AlertDialog.Builder alert = new AlertDialog.Builder(getActivity(), R.style.Theme_Chromium_AlertDialog);
        if (null == alert) {
            return;
        }
        AlertDialog.Builder alertDialog = alert
                .setTitle("Enter QA code")
                .setView(view)
                .setPositiveButton(R.string.ok, onClickListener)
                .setNegativeButton(R.string.cancel, onClickListener)
                .setCancelable(false);
        Dialog dialog = alertDialog.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    @Override
    public void OnWalletInitialized(int error_code) {}

    @Override
    public void OnWalletProperties(int error_code) {}

    @Override
    public void OnPublisherInfo(int tabId) {}

    @Override
    public void OnGetCurrentBalanceReport(String[] report) {}

    @Override
    public void OnNotificationAdded(String id, int type, long timestamp,
          String[] args) {}

    @Override
    public void OnNotificationsCount(int count) {}

    @Override
    public void OnGetLatestNotification(String id, int type, long timestamp,
              String[] args) {}

    @Override
    public void OnNotificationDeleted(String id) {}

    @Override
    public void OnIsWalletCreated(boolean created) {}

    @Override
    public void OnGetPendingContributionsTotal(double amount) {}

    @Override
    public void OnGetRewardsMainEnabled(boolean enabled) {
    }

    @Override
    public void OnGetAutoContributeProps() {}

    @Override
    public void OnGetReconcileStamp(long timestamp) {}

    @Override
    public void OnRecurringDonationUpdated() {}

    @Override
    public void OnResetTheWholeState(boolean success) {
        if (success) {
            SharedPreferences sharedPreferences = ContextUtils.getAppSharedPreferences();
            SharedPreferences.Editor sharedPreferencesEditor = sharedPreferences.edit();
            sharedPreferencesEditor.putBoolean(BraveRewardsPanelPopup.PREF_GRANTS_NOTIFICATION_RECEIVED, false);
            sharedPreferencesEditor.putBoolean(BraveRewardsPanelPopup.PREF_WAS_BRAVE_REWARDS_TURNED_ON, false);
            sharedPreferencesEditor.apply();
            PrefServiceBridge.getInstance().setSafetynetCheckFailed(false);
            RestartWorker.AskForRelaunch(getActivity());
        } else {
            RestartWorker.AskForRelaunchCustom(getActivity());
        }
    }

    @Override
    public void OnRewardsMainEnabled(boolean enabled) {}
}

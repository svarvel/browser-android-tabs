// Copyright 2019 The Brave Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences.developer;

import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.chromium.base.ContextUtils;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.BraveRewardsNativeWorker;
import org.chromium.chrome.browser.BraveRewardsObserver;
import org.chromium.chrome.browser.BraveRewardsPanelPopup;
import org.chromium.chrome.browser.ConfigAPIs;
import org.chromium.chrome.browser.preferences.ChromeSwitchPreference;
import org.chromium.chrome.browser.preferences.PreferenceUtils;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.RestartWorker;

import org.chromium.base.Log;
/**
 * Settings fragment containing preferences for QA team.
 */
public class QAPreferences extends PreferenceFragment
        implements OnPreferenceChangeListener, OnPreferenceClickListener, BraveRewardsObserver {
    private static final String PREF_USE_REWARDS_STAGING_SERVER = "use_rewards_staging_server";
    private static final String PREF_WALLET_BAT = "wallet_bat";
    private static final String PREF_WALLET_BTC = "wallet_btc";
    private static final String PREF_WALLET_ETH = "wallet_eth";
    private static final String PREF_WALLET_LTC = "wallet_ltc";

    private static final String BAT_ADDRESS_NAME = "BAT";
    private static final String BTC_ADDRESS_NAME = "BTC";
    private static final String ETH_ADDRESS_NAME = "ETH";
    private static final String LTC_ADDRESS_NAME = "LTC";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceUtils.addPreferencesFromResource(this, R.xml.qa_preferences);

        ChromeSwitchPreference stagingServer =
                (ChromeSwitchPreference) findPreference(PREF_USE_REWARDS_STAGING_SERVER);
        if (stagingServer != null) {
            stagingServer.setOnPreferenceChangeListener(this);
        }

        Preference walletBAT = findPreference(PREF_WALLET_BAT);
        if (walletBAT != null) {
            walletBAT.setOnPreferenceClickListener(this);
        }

        Preference walletBTC = findPreference(PREF_WALLET_BTC);
        if (walletBTC != null) {
            walletBTC.setOnPreferenceClickListener(this);
        }

        Preference walletETH = findPreference(PREF_WALLET_ETH);
        if (walletETH != null) {
            walletETH.setOnPreferenceClickListener(this);
        }

        Preference walletLTC = findPreference(PREF_WALLET_LTC);
        if (walletLTC != null) {
            walletLTC.setOnPreferenceClickListener(this);
        }
    }

    @Override
    public void onStart() {
        BraveRewardsNativeWorker.getInstance().AddObserver(this);
        checkQACode();
        Preference walletBAT = findPreference(PREF_WALLET_BAT);
        if (walletBAT != null) {
            walletBAT.setSummary(BraveRewardsNativeWorker.getInstance().GetAddress(BAT_ADDRESS_NAME));
        }
        Preference walletBTC = findPreference(PREF_WALLET_BTC);
        if (walletBTC != null) {
            walletBTC.setSummary(BraveRewardsNativeWorker.getInstance().GetAddress(BTC_ADDRESS_NAME));
        }
        Preference walletETH = findPreference(PREF_WALLET_ETH);
        if (walletETH != null) {
            walletETH.setSummary(BraveRewardsNativeWorker.getInstance().GetAddress(ETH_ADDRESS_NAME));
        }
        Preference walletLTC = findPreference(PREF_WALLET_LTC);
        if (walletLTC != null) {
            walletLTC.setSummary(BraveRewardsNativeWorker.getInstance().GetAddress(LTC_ADDRESS_NAME));
        }
        super.onStart();
    }

    @Override
    public void onStop() {
        BraveRewardsNativeWorker.getInstance().RemoveObserver(this);
        super.onStop();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (PREF_USE_REWARDS_STAGING_SERVER.equals(preference.getKey())) {
            PrefServiceBridge.getInstance().setUseRewardsStagingServer((boolean) newValue);
            BraveRewardsNativeWorker.getInstance().ResetTheWholeState();
            RestartWorker.AskForRelaunch(getActivity());
        }
        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (PREF_WALLET_BAT.equals(preference.getKey()) ||
            PREF_WALLET_BTC.equals(preference.getKey()) ||
            PREF_WALLET_ETH.equals(preference.getKey()) ||
            PREF_WALLET_LTC.equals(preference.getKey())) {
            ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("", preference.getSummary());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getActivity().getApplicationContext(),
                getResources().getString(R.string.brave_sync_copied_text) + "\n" + preference.getSummary(),
                Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private void checkQACode() {
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.qa_code_check, null);
        final EditText input = (EditText) view.findViewById(R.id.qa_code);

        DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int button) {
                if (button != AlertDialog.BUTTON_POSITIVE ||
                    !input.getText().toString().equals(ConfigAPIs.QA_CODE)) {
                    getActivity().finish();
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
// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.preferences;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.text.format.DateUtils;
import android.view.ContextThemeWrapper;

import org.chromium.base.Log;
import org.chromium.chrome.R;

/**
 * Settings fragment that allows to control Sync functionality.
 */
public class BraveSyncPreferences extends PreferenceFragment {

    private static final String PREF_SYNC_SWITCH = "sync_switch";
    private static final String PREF_SYNC_BOOKMARKS_CHECK_BOX = "sync_bookmarks_check_box";
    //private static final String PREF_OS_VERSION = "os_version";
    //private static final String PREF_LEGAL_INFORMATION = "legal_information";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.prefs_sync);
        addPreferencesFromResource(R.xml.brave_sync_preferences);

        ChromeSwitchPreference syncSwitch =
                (ChromeSwitchPreference) findPreference(PREF_SYNC_SWITCH);
        syncSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Log.i("TAG", "!!!sync switch == " + (boolean)newValue);
                ChromeBaseCheckBoxPreference syncBookmarksCheckBox =
                        (ChromeBaseCheckBoxPreference) findPreference(PREF_SYNC_BOOKMARKS_CHECK_BOX);
                syncBookmarksCheckBox.setEnabled((boolean)newValue);

                return true;
            }
        });

        ChromeBaseCheckBoxPreference syncBookmarksCheckBox =
                (ChromeBaseCheckBoxPreference) findPreference(PREF_SYNC_BOOKMARKS_CHECK_BOX);

        syncBookmarksCheckBox.setEnabled(syncSwitch.isChecked());

        /*PrefServiceBridge prefServiceBridge = PrefServiceBridge.getInstance();
        AboutVersionStrings versionStrings = prefServiceBridge.getAboutVersionStrings();
        Preference p = findPreference(PREF_APPLICATION_VERSION);
        p.setSummary(getApplicationVersion(getActivity(), versionStrings.getApplicationVersion()));
        p = findPreference(PREF_OS_VERSION);
        p.setSummary(versionStrings.getOSVersion());
        p = findPreference(PREF_LEGAL_INFORMATION);
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        p.setSummary(getString(R.string.legal_information_summary, currentYear));*/
    }


}

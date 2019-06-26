package org.chromium.chrome.browser.preferences;

import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.widget.ListView;

import org.chromium.chrome.R;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.PreferenceUtils;
import org.chromium.chrome.browser.preferences.website.SiteSettingsCategory;
import org.chromium.chrome.browser.preferences.website.SingleCategoryPreferences;
import org.chromium.chrome.browser.preferences.website.ContentSettingsResources;
import org.chromium.chrome.browser.preferences.website.ContentSettingValues;
//import org.chromium.chrome.browser.MixPanelWorker;

import java.util.ArrayList;
import java.util.List;

/**
 * The main Site Settings screen, which shows all the site settings categories: All sites, Location,
 * Microphone, etc. By clicking into one of these categories, the user can see or and modify
 * permissions that have been granted to websites, as well as enable or disable permissions
 * browser-wide.
 *
 * Depending on version and which experiment is running, this class also handles showing the Media
 * sub-menu, which contains Autoplay and Protected Content. To avoid the Media sub-menu having only
 * one sub-item, when either Autoplay or Protected Content should not be visible the other is shown
 * in the main setting instead (as opposed to under Media).
 */
public class ControlsPreferences extends PreferenceFragment
        implements OnPreferenceClickListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.prefs_section_controls);
        PreferenceUtils.addPreferencesFromResource(this, R.xml.controls_preferences);

        updatePreferenceStates();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ((ListView) getView().findViewById(android.R.id.list)).setDivider(null);
    }

    private Preference findPreference(@SiteSettingsCategory.Type int type) {
        return findPreference(SiteSettingsCategory.preferenceKey(type));
    }

    private void configurePreferences() {
        getPreferenceScreen().removePreference(findPreference(SiteSettingsCategory.Type.PLAY_VIDEO_IN_BACKGROUND));
    }

    private void updatePreferenceStates() {
        PrefServiceBridge prefServiceBridge = PrefServiceBridge.getInstance();

        Preference p = findPreference(SiteSettingsCategory.Type.PLAY_VIDEO_IN_BACKGROUND);
            int contentType = SiteSettingsCategory.contentSettingsType(SiteSettingsCategory.Type.PLAY_VIDEO_IN_BACKGROUND);
            boolean requiresTriStateSetting =
                    prefServiceBridge.requiresTriStateContentSetting(contentType);

            boolean checked = false;
            @ContentSettingValues
            int setting = ContentSettingValues.DEFAULT;

            if (requiresTriStateSetting) {
                setting = prefServiceBridge.getContentSetting(contentType);
            } else {
                checked = prefServiceBridge.isCategoryEnabled(contentType);
            }

            p.setTitle(ContentSettingsResources.getTitle(contentType));
            p.setOnPreferenceClickListener(this);

            p.setSummary( checked ? ContentSettingsResources.getPlayVideoInBackgroundEnabledSummary() : ContentSettingsResources.getPlayVideoInBackgroundDisabledSummary());

            if (p.isEnabled()) {
                p.setIcon(PreferenceUtils.getTintedIcon(
                        getActivity(), ContentSettingsResources.getIcon(contentType)));
            } else {
                p.setIcon(ContentSettingsResources.getDisabledIcon(contentType, getResources()));
            }
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreferenceStates();
    }

    // OnPreferenceClickListener:

    @Override
    public boolean onPreferenceClick(Preference preference) {
        /*if (R.string.javascript_permission_title == preference.getTitleRes()) {
            MixPanelWorker.SendEvent("JavaScript Option Changed", "JavaScript", !PrefServiceBridge.getInstance().javaScriptEnabled());
        }*/
        preference.getExtras().putString(
                SingleCategoryPreferences.EXTRA_CATEGORY, preference.getKey());
        preference.getExtras().putString(SingleCategoryPreferences.EXTRA_TITLE,
                preference.getTitle().toString());
        return false;
    }
}
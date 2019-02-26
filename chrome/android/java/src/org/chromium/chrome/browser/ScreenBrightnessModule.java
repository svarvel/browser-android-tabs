// NOTE: helper class to deal with screen brightness 
// Author: Matteo Varvello (varvello@brave.com)
// Date: 02/26/2019
package org.chromium.chrome.browser;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;


/**
 * A module responsible for adjusting the brightness level of the display and the activity.
 */

public class ScreenBrightnessModule {

    private static final int BRIGHTNESS_MAX = 255;
    private static final int BRIGHTNESS_MIN = 0;

    /**
     * Check if the application has enough permissions
     */
    public static boolean hasSettingsPermission(Context context) {
        // By default, Android versions earlier than 6 have permission.
        boolean hasPermission = true;

        // Check for permisions if >= Android 6
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasPermission = Settings.System.canWrite(context);
        }

        return hasPermission;
    }

    /**
     * Gets the brightness level of the device settings.
     * @param context The application context
     * @return The brightness level.
     */
    public static Integer getSystemBrightness(Context context) {
        Integer brightness;
        try {
            brightness = Settings.System.getInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS
            );
        } catch (Settings.SettingNotFoundException e) {
            brightness = null;
        }
        return brightness;
    }

    /**
     * Sets the brightness level to the device settings.
     *
     * @param brightness The brightness level between 0-255.
     * @param context The application context
     *
     * @return True if the brightness has been set.
     */
    public static boolean setSystemBrightness(int brightness, Context context) {
        if (hasSettingsPermission(context)) {
            // ensure brightness is bound between range 0-255
            brightness = Math.max(BRIGHTNESS_MIN, Math.min(brightness, BRIGHTNESS_MAX));
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness
            );
            return true;
        }
        return false;
    }
}

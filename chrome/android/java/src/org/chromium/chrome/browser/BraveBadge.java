package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.chromium.chrome.R;

public class BraveBadge extends TextView {

    public enum BadgeColor {
        GREY(Color.parseColor("#9E9E9E")),
        BLUE_GREY(Color.parseColor("#607D8B")),
        RED(Color.parseColor("#f44336")),
        BLUE(Color.parseColor("#2196F3")),
        CYAN(Color.parseColor("#00BCD4")),
        TEAL(Color.parseColor("#009688")),
        GREEN(Color.parseColor("#4CAF50")),
        YELLOW(Color.parseColor("#FFEB3B")),
        ORANGE(Color.parseColor("#FF9800")),
        DEEP_ORANGE(Color.parseColor("#FF5722")),
        PURPLE(Color.parseColor("#9C27B0")),
        LIGHT_BLUE(Color.parseColor("#03A9F4")),
        LIGHT_GREEN(Color.parseColor("#8BC34A")),
        BLACK(Color.parseColor("#000000"));

        private int color;

        BadgeColor(int color) {
            this.color = color;
        }

        public int getColor() {
            return color;
        }
    }

    public BraveBadge(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void update(Activity activity, int counter) {
        update(activity, null, counter + "");
    }

    public void update(Activity activity, String counter) {
        update(activity, null, counter);
    }

    /**
     * update the given menu item with badgeCount and style
     *
     * @param activity use to bind onOptionsItemSelected
     * @param color   background badge
     * @param counter counter
     */
    public void update(final Activity activity, BadgeColor color, String counter) {
        GradientDrawable mDrawable = new GradientDrawable();
        this.bringToFront();

        if (color != null) {
            // Set Color
            mDrawable.setCornerRadius(5);
            mDrawable.setColor(color.getColor());
            this.setPadding(2, 0, 2, 1);
            this.setBackground(mDrawable);
        }

        //Manage min value
        if (counter == null) {
            this.setVisibility(View.GONE);
        } else {
            this.setVisibility(View.VISIBLE);
            this.setText(counter);
        }
    }
}

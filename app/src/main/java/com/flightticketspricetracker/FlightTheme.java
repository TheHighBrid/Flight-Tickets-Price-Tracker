package com.flightticketspricetracker;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

public final class FlightTheme {
    public static final int NAVY = Color.rgb(10, 27, 51);
    public static final int NAVY_2 = Color.rgb(9, 38, 79);
    public static final int DEEP_BLUE = Color.rgb(18, 58, 109);
    public static final int ROYAL = Color.rgb(30, 99, 245);
    public static final int CYAN = Color.rgb(0, 184, 255);
    public static final int AQUA = Color.rgb(126, 230, 255);
    public static final int ICE = Color.rgb(230, 242, 255);
    public static final int WHITE = Color.WHITE;
    public static final int SURFACE = Color.rgb(13, 38, 72);
    public static final int SURFACE_2 = Color.rgb(16, 49, 91);
    public static final int BORDER = Color.rgb(45, 91, 145);
    public static final int TEXT = Color.rgb(246, 250, 255);
    public static final int MUTED = Color.rgb(166, 195, 226);
    public static final int SUCCESS = Color.rgb(32, 201, 120);
    public static final int WARNING = Color.rgb(255, 180, 74);
    public static final int ERROR = Color.rgb(255, 93, 103);
    public static final int PURPLE = Color.rgb(163, 111, 255);

    private FlightTheme() {
    }

    public static GradientDrawable solid(int fill, float radiusPx, int strokeWidthPx, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radiusPx);
        if (strokeWidthPx > 0) drawable.setStroke(strokeWidthPx, stroke);
        return drawable;
    }

    public static GradientDrawable horizontalGradient(int start, int end, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{start, end}
        );
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    public static GradientDrawable verticalGradient(int start, int end, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{start, end}
        );
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    public static StateListDrawable pressable(int normal, int pressed, float radiusPx, int strokeWidthPx, int stroke) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, solid(pressed, radiusPx, strokeWidthPx, stroke));
        states.addState(new int[]{-android.R.attr.state_enabled}, solid(withAlpha(normal, 0.45f), radiusPx, strokeWidthPx, stroke));
        states.addState(new int[]{}, solid(normal, radiusPx, strokeWidthPx, stroke));
        return states;
    }

    public static ColorStateList checkTint(int checked, int unchecked) {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{checked, unchecked}
        );
    }

    public static int withAlpha(int color, float alpha) {
        return Color.argb(
                Math.round(255f * alpha),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }
}

package com.flightticketspricetracker;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public final class BrandedMainActivity extends MainActivity {
    private static final String LEGACY_BRAND = "FLIGHT TRACKER";
    private static final String LEGACY_TAGLINE = "LIVE  •  ACCURATE  •  GLOBAL";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyArabicBranding(getWindow().getDecorView());
    }

    private void applyArabicBranding(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            String value = textView.getText() == null ? "" : textView.getText().toString();
            if (LEGACY_BRAND.equals(value)) {
                textView.setText(R.string.app_name);
                textView.setLetterSpacing(0f);
                textView.setTextDirection(View.TEXT_DIRECTION_RTL);
            } else if (LEGACY_TAGLINE.equals(value)) {
                textView.setText(R.string.app_tagline);
                textView.setLetterSpacing(0f);
                textView.setTextDirection(View.TEXT_DIRECTION_RTL);
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                applyArabicBranding(group.getChildAt(index));
            }
        }
    }
}

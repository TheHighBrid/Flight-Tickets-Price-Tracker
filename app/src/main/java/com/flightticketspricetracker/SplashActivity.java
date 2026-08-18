package com.flightticketspricetracker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.VideoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class SplashActivity extends Activity {
    private static final long PLAYBACK_WATCHDOG_MS = 16_000L;
    private static final long INTRO_SIZE_BYTES = 74_591L;

    private static final int[] INTRO_PARTS = {
            R.raw.app_intro_00,
            R.raw.app_intro_01,
            R.raw.app_intro_02,
            R.raw.app_intro_03,
            R.raw.app_intro_04,
            R.raw.app_intro_05,
            R.raw.app_intro_06,
            R.raw.app_intro_07,
            R.raw.app_intro_08
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private VideoView introVideo;
    private boolean navigating;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showImmersiveUi();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        introVideo = new VideoView(this);
        introVideo.setBackgroundColor(Color.BLACK);
        root.addView(introVideo, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(root);

        try {
            File introFile = materializeIntroVideo();
            introVideo.setVideoURI(Uri.fromFile(introFile));
        } catch (IOException error) {
            openMainApp();
            return;
        }

        introVideo.setOnPreparedListener(player -> {
            player.setLooping(false);
            player.setVolume(1f, 1f);
            introVideo.start();
        });
        introVideo.setOnCompletionListener(player -> openMainApp());
        introVideo.setOnErrorListener((player, what, extra) -> {
            openMainApp();
            return true;
        });

        handler.postDelayed(this::openMainApp, PLAYBACK_WATCHDOG_MS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showImmersiveUi();
        if (introVideo != null && introVideo.getCurrentPosition() > 0 && !introVideo.isPlaying()) {
            introVideo.start();
        }
    }

    @Override
    protected void onPause() {
        if (introVideo != null && introVideo.isPlaying()) {
            introVideo.pause();
        }
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            showImmersiveUi();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (introVideo != null) {
            introVideo.stopPlayback();
        }
        super.onDestroy();
    }

    private File materializeIntroVideo() throws IOException {
        File videoFile = new File(getCacheDir(), "app_intro_v1.mp4");
        if (videoFile.isFile() && videoFile.length() == INTRO_SIZE_BYTES) {
            return videoFile;
        }

        File temporaryFile = new File(getCacheDir(), "app_intro_v1.tmp");
        if (temporaryFile.exists() && !temporaryFile.delete()) {
            throw new IOException("Unable to reset intro video cache");
        }

        try (FileOutputStream output = new FileOutputStream(temporaryFile, false)) {
            byte[] buffer = new byte[8192];
            for (int resourceId : INTRO_PARTS) {
                try (InputStream input = getResources().openRawResource(resourceId)) {
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                }
            }
            output.getFD().sync();
        }

        if (temporaryFile.length() != INTRO_SIZE_BYTES) {
            temporaryFile.delete();
            throw new IOException("Unexpected intro video size");
        }

        if (videoFile.exists() && !videoFile.delete()) {
            temporaryFile.delete();
            throw new IOException("Unable to replace intro video cache");
        }

        if (!temporaryFile.renameTo(videoFile)) {
            temporaryFile.delete();
            throw new IOException("Unable to prepare intro video");
        }
        return videoFile;
    }

    private void showImmersiveUi() {
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    private void openMainApp() {
        if (navigating || isFinishing()) {
            return;
        }
        navigating = true;
        handler.removeCallbacksAndMessages(null);

        Intent intent = new Intent(this, BrandedMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}

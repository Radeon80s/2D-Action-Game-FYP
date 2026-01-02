package com.sotiris.engine;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.Gdx;

import games.spooky.gdx.sfx.android.AndroidAudioDurationResolver;

public class AndroidLauncher extends AndroidApplication {
    private void onMissionComplete() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("MISSION_ID", getIntent().getStringExtra("MISSION_ID"));
        setResult(Activity.RESULT_OK, resultIntent);
        finish(); // Close the activity
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        String missionId = getIntent().getStringExtra("MISSION_ID");
        String gender = getIntent().getStringExtra("GENDER");

        if (missionId != null && missionId.equals("mission_1")) {
            AndroidAudioDurationResolver.initialize();
            initialize(new Demo(this::onMissionComplete, gender), config);
        }
        hideSystemUI();
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onBackPressed() {
        // Prevent back button from exiting the app
    }

    @Override
    public void exit() {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Ensure proper cleanup
        if (Gdx.app != null) {
            Gdx.app.exit();
            Gdx.app = null;
        }

        Gdx.input = null;
        Gdx.files = null;
        Gdx.audio = null;
        Gdx.graphics = null;
        Gdx.net = null;
    }
}

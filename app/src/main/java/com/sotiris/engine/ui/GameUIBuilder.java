package com.sotiris.engine.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.sotiris.engine.utils.MySpatializedSoundPlayer;

/**
 * GameUIBuilder handles creation of UI screens (GameOver, MissionComplete, Settings).
 * Extracted from Demo.java to reduce class complexity.
 */
public class GameUIBuilder {

    /**
     * Callback interface for UI actions
     */
    public interface UICallback {
        void onRestartMission();
        void onExitGame();
        void onBackToMain();
        void onBackFromSettings();
        void onVolumeChanged(float volume);
    }

    private final Skin skin;
    private final MySpatializedSoundPlayer<?> soundPlayer;
    private UICallback callback;

    // Volume control references for sync
    private Slider mainVolumeSlider;
    private Slider settingsVolumeSlider;
    private TextButton muteButton;
    private float savedVolume = 1.0f;

    public GameUIBuilder(Skin skin, MySpatializedSoundPlayer<?> soundPlayer) {
        this.skin = skin;
        this.soundPlayer = soundPlayer;
    }

    public void setCallback(UICallback callback) {
        this.callback = callback;
    }

    /**
     * Creates the Game Over UI screen
     */
    public void buildGameOverUI(Stage gameOverStage) {
        if (gameOverStage.getActors().size > 0) return;

        Table table = new Table();
        table.setFillParent(true);
        gameOverStage.addActor(table);

        Label gameOverLabel = new Label("YOU DIED", skin);
        gameOverLabel.setFontScale(2f);
        gameOverLabel.setColor(Color.RED);

        TextButton restartButton = new TextButton("Restart Mission", skin);
        TextButton exitButton = new TextButton("Exit Game", skin);

        restartButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (callback != null) callback.onRestartMission();
            }
        });

        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (callback != null) callback.onExitGame();
            }
        });

        table.add(gameOverLabel).padBottom(20);
        table.row();
        table.add(restartButton).width(200).height(50).padBottom(10);
        table.row();
        table.add(exitButton).width(200).height(50);
    }

    /**
     * Creates the Mission Complete UI screen
     */
    public void buildMissionCompleteUI(Stage missionCompleteStage) {
        if (missionCompleteStage.getActors().size > 0) return;

        Table table = new Table();
        table.setFillParent(true);
        missionCompleteStage.addActor(table);

        Label missionCompleteLabel = new Label("Demo Complete", skin);
        Label label2 = new Label("You win", skin);
        missionCompleteLabel.setFontScale(2f);
        missionCompleteLabel.setColor(Color.GREEN);

        TextButton exitButton = new TextButton("Back to Main", skin);
        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (callback != null) callback.onBackToMain();
            }
        });

        table.add(missionCompleteLabel).padBottom(20);
        table.row();
        table.add(label2).width(100).height(40).padBottom(10);
        table.row();
        table.add(exitButton).width(200).height(50);
    }

    /**
     * Creates the Settings UI screen
     */
    public void buildSettingsUI(Stage settingsStage) {
        if (settingsStage.getActors().size > 0) return;

        Table table = new Table();
        table.setFillParent(true);
        settingsStage.addActor(table);

        Label settingsLabel = new Label("Settings", skin);
        settingsLabel.setFontScale(2f);
        settingsLabel.setColor(Color.WHITE);

        settingsVolumeSlider = new Slider(0f, 1f, 0.1f, false, skin);
        settingsVolumeSlider.setValue(soundPlayer.getVolume());
        settingsVolumeSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float volume = settingsVolumeSlider.getValue();
                if (soundPlayer != null) {
                    soundPlayer.setVolume(volume);
                    if (mainVolumeSlider != null) {
                        mainVolumeSlider.setValue(volume);
                    }
                    if (callback != null) callback.onVolumeChanged(volume);
                }
            }
        });

        TextButton backButton = new TextButton("Back", skin);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (callback != null) callback.onBackFromSettings();
            }
        });

        table.add(settingsLabel).padBottom(20);
        table.row();
        table.add(new Label("Volume", skin)).padBottom(10);
        table.row();
        table.add(settingsVolumeSlider).width(300).padBottom(20);
        table.row();
        table.add(backButton).width(200).height(50);
    }

    /**
     * Creates the main HUD volume slider
     */
    public Slider buildVolumeSlider(Stage uiStage) {
        mainVolumeSlider = new Slider(0f, 1f, 0.1f, false, skin);
        mainVolumeSlider.setValue(soundPlayer.getVolume());
        mainVolumeSlider.setPosition(500, 50);
        mainVolumeSlider.setSize(150, 35);

        mainVolumeSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float volume = mainVolumeSlider.getValue();
                if (soundPlayer != null) {
                    soundPlayer.setVolume(volume);
                    if (settingsVolumeSlider != null) {
                        settingsVolumeSlider.setValue(volume);
                    }
                    updateMuteButtonText(volume);
                    if (volume > 0f) {
                        savedVolume = volume;
                    }
                    if (callback != null) callback.onVolumeChanged(volume);
                }
            }
        });

        uiStage.addActor(mainVolumeSlider);
        return mainVolumeSlider;
    }

    /**
     * Creates the mute button
     */
    public TextButton buildMuteButton(Stage uiStage) {
        muteButton = new TextButton("Mute", skin);
        muteButton.setPosition(400, 50);
        muteButton.setSize(100, 30);

        muteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (soundPlayer != null) {
                    if (soundPlayer.getVolume() > 0f) {
                        savedVolume = soundPlayer.getVolume();
                        soundPlayer.setVolume(0f);
                        syncSliders(0f);
                        muteButton.setText("Unmute");
                    } else {
                        soundPlayer.setVolume(savedVolume);
                        syncSliders(savedVolume);
                        muteButton.setText("Mute");
                    }
                    if (callback != null) callback.onVolumeChanged(soundPlayer.getVolume());
                }
            }
        });

        uiStage.addActor(muteButton);
        return muteButton;
    }

    private void syncSliders(float volume) {
        if (mainVolumeSlider != null) mainVolumeSlider.setValue(volume);
        if (settingsVolumeSlider != null) settingsVolumeSlider.setValue(volume);
    }

    private void updateMuteButtonText(float volume) {
        if (muteButton != null) {
            muteButton.setText(volume > 0f ? "Mute" : "Unmute");
        }
    }

    // Getters for sliders if needed externally
    public Slider getMainVolumeSlider() {
        return mainVolumeSlider;
    }

    public Slider getSettingsVolumeSlider() {
        return settingsVolumeSlider;
    }

    public TextButton getMuteButton() {
        return muteButton;
    }
}

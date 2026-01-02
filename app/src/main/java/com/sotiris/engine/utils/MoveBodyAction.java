package com.sotiris.engine.utils;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.ui.Image;

public class MoveBodyAction extends Action {
    private final Image image;
    private final Body body;
    private final Vector2 targetPosition;
    private final float duration;
    private float time;

    private final MySpatializedSoundPlayer<Vector2> soundPlayer;  // Reference to sound player
    private final long engineSoundId;       // The ID of the engine sound
    private final boolean engineSoundPlaying;

    public MoveBodyAction(Image image, Body body, float x, float y, float duration, MySpatializedSoundPlayer<Vector2> soundPlayer, long engineSoundId) {
        this.image = image;
        this.body = body;
        this.targetPosition = new Vector2(x, y);
        this.duration = duration;
        this.time = 0;

        this.soundPlayer = soundPlayer;
        this.engineSoundId = engineSoundId;
        this.engineSoundPlaying = true;  // Assuming the engine sound is playing when the action starts
    }

    @Override
    public boolean act(float delta) {
        time += delta;
        float alpha = Math.min(1, time / duration);

        // Calculate new position
        float newX = image.getX() + (targetPosition.x - image.getX()) * alpha * 0.1f;  // Adjust speed
        float newY = image.getY() + (targetPosition.y - image.getY()) * alpha * 0.1f;  // Adjust speed

        // Update image position
        image.setPosition(newX, newY);

        // Update body position in Box2D
        body.setTransform(newX + image.getWidth() / 2, newY + image.getHeight() / 2, 0);

        // **Update the engine sound position**
        if (engineSoundPlaying && engineSoundId != -1) {
            Vector2 soundPosition = new Vector2(newX, newY);  // Use the car's new position for the sound
            soundPlayer.updateSoundPosition(engineSoundId, soundPosition);
        }

        return time >= duration;
    }
}

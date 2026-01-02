package com.sotiris.engine.utils;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.ui.Image;

public class MoveBodyAction extends Action {
    private final Image image;
    private final Body body;
    private final Vector2 startPosition;
    private final Vector2 targetPosition;
    private final float duration;
    private float time;
    private boolean initialized = false;

    private final MySpatializedSoundPlayer<Vector2> soundPlayer;
    private final long engineSoundId;
    private final boolean engineSoundPlaying;

    public MoveBodyAction(Image image, Body body, float x, float y, float duration, MySpatializedSoundPlayer<Vector2> soundPlayer, long engineSoundId) {
        this.image = image;
        this.body = body;
        this.startPosition = new Vector2();
        this.targetPosition = new Vector2(x, y);
        this.duration = duration;
        this.time = 0;

        this.soundPlayer = soundPlayer;
        this.engineSoundId = engineSoundId;
        this.engineSoundPlaying = true;
    }

    @Override
    public boolean act(float delta) {
        // Capture start position on first frame
        if (!initialized) {
            startPosition.set(image.getX(), image.getY());
            initialized = true;
        }

        time += delta;
        float alpha = Math.min(1, time / duration);

        // Linear interpolation from start to target
        float newX = startPosition.x + (targetPosition.x - startPosition.x) * alpha;
        float newY = startPosition.y + (targetPosition.y - startPosition.y) * alpha;

        // Update image position
        image.setPosition(newX, newY);

        // Update body position in Box2D
        body.setTransform(newX + image.getWidth() / 2, newY + image.getHeight() / 2, 0);

        // Update the engine sound position
        if (engineSoundPlaying && engineSoundId != -1) {
            Vector2 soundPosition = new Vector2(newX, newY);
            soundPlayer.updateSoundPosition(engineSoundId, soundPosition);
        }

        return time >= duration;
    }
}

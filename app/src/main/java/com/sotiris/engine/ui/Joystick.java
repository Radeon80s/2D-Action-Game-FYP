package com.sotiris.engine.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;

public class Joystick extends Actor {
    private final Texture bgTexture;
    private final Texture knobTexture;
    private final Vector2 bgPosition;
    private final Vector2 knobPosition;
    private final Vector2 initialKnobPosition;
    private final float bgRadius;
    private final float knobRadius;
    private boolean isTouched;
    private final Vector2 direction;
    public boolean getIsTouched(){
        return isTouched;
    }

    public void reset() {
        // Reset the joystick position back to the initial position
        knobPosition.set(initialKnobPosition);

        // Reset the direction and touch state
        direction.set(0, 0);
        isTouched = false;
    }

    public Joystick(String bgTexturePath, String knobTexturePath, float x, float y) {
        bgTexture = new Texture(Gdx.files.internal(bgTexturePath));
        knobTexture = new Texture(Gdx.files.internal(knobTexturePath));

        bgRadius = (float) bgTexture.getWidth() / 2;
        knobRadius = (float) knobTexture.getWidth() / 2;

        bgPosition = new Vector2(x, y);
        initialKnobPosition = new Vector2(x, y);
        knobPosition = new Vector2(x, y);

        direction = new Vector2(0, 0);

        setBounds(bgPosition.x - bgRadius, bgPosition.y - bgRadius, bgTexture.getWidth(), bgTexture.getHeight());

        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                isTouched = true;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                float touchX = event.getStageX();
                float touchY = event.getStageY();
                knobPosition.set(touchX, touchY);

                Vector2 delta = new Vector2(touchX - bgPosition.x, touchY - bgPosition.y);
                if (delta.len() > bgRadius) {
                    delta.setLength(bgRadius);
                    knobPosition.set(bgPosition.x + delta.x, bgPosition.y + delta.y);
                }

                direction.set(delta).nor().scl(delta.len() / bgRadius);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                isTouched = false;
                knobPosition.set(initialKnobPosition);
                direction.set(0, 0);
            }
        });
    }

    public Vector2 getDirection() {
        return direction;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        batch.draw(bgTexture, bgPosition.x - bgRadius, bgPosition.y - bgRadius);
        batch.draw(knobTexture, knobPosition.x - knobRadius, knobPosition.y - knobRadius);
    }

    @Override
    public boolean remove() {
        bgTexture.dispose();
        knobTexture.dispose();
        return super.remove();
    }
}

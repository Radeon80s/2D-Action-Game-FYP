package com.sotiris.engine.entities;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.*;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.TimeUtils;
import com.sotiris.engine.Demo;
import com.sotiris.engine.utils.MoveBodyAction;
import com.sotiris.engine.utils.MySpatializedSoundPlayer;

import games.spooky.gdx.sfx.SfxSound;

public class Car extends Actor {
    private final Image carImage;
    private final Image leftDoorImage;
    private final Image rightDoorImage;
    private final Body carBody;
    private final Rectangle collisionRectangle;

    private final MySpatializedSoundPlayer<Vector2> soundPlayer;
    private final SfxSound carEngineSound;
    private final SfxSound carDoorOpenedSound;
    private final SfxSound carHitSound;
    private boolean engineSoundPlaying = false;
    private boolean doorsOpen = false;

    private long engineSoundId = -1;
    Texture leftDoorTexture,rightDoorTexture,carTexture;

    private <T> T getAssetIfLoaded(AssetManager assetManager, String assetPath, Class<T> type) {
        return assetManager.isLoaded(assetPath, type) ? assetManager.get(assetPath, type) : null;
    }

    public Car(World world,
               AssetManager assetManager, MySpatializedSoundPlayer<Vector2> soundPlayer) {
        this.soundPlayer = soundPlayer;

        this.carTexture = getAssetIfLoaded(assetManager, "cars/car.png", Texture.class);
        this.leftDoorTexture = getAssetIfLoaded(assetManager, "cars/left_door.png", Texture.class);
        this.rightDoorTexture = getAssetIfLoaded(assetManager, "cars/right_door.png", Texture.class);
        this.carHitSound = getAssetIfLoaded(assetManager, "sounds/car_hit.wav", SfxSound.class);


        this.carImage = new Image(this.carTexture);
        this.leftDoorImage = new Image(this.leftDoorTexture);
        this.rightDoorImage = new Image(this.rightDoorTexture);

        carEngineSound = assetManager.isLoaded("sounds/engine.wav")
                ? assetManager.get("sounds/engine.wav", SfxSound.class)
                : null;

        carDoorOpenedSound = assetManager.isLoaded("sounds/car_door_opened.wav")
                ? assetManager.get("sounds/car_door_opened.wav", SfxSound.class)
                : null;

        // Set up car body for physics
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        carBody = world.createBody(bodyDef);

        PolygonShape shape = new PolygonShape();
        shape.setAsBox(carImage.getWidth() / 2, carImage.getHeight() / 2);
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.isSensor = true; // Use as a sensor to detect collisions without physical response

        carBody.createFixture(fixtureDef);
        shape.dispose();

        // Initialize the collision rectangle
        collisionRectangle = new Rectangle(carImage.getX(), carImage.getY(), carImage.getWidth(), carImage.getHeight());
    }

    public void startSoundEngine(){
        // Start playing the engine sound looped
        if (carEngineSound != null && !engineSoundPlaying) {
            engineSoundId = soundPlayer.play(getPosition(), carEngineSound, 1.0f, true);
            if (engineSoundId != -1) {
                engineSoundPlaying = true;
            } else {
                Gdx.app.error("Car", "Failed to start engine sound.");
            }
        } else {
            Gdx.app.error("Car", "Engine sound is null or already playing.");
        }
    }
    @Override
    public void draw(Batch batch, float parentAlpha) {
        carImage.draw(batch, parentAlpha);
        leftDoorImage.draw(batch, parentAlpha);
        rightDoorImage.draw(batch, parentAlpha);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        updatePositionFromBody();
    }

    public void moveTo(float x, float y, float duration) {
        carImage.addAction(new MoveBodyAction(carImage, carBody, x, y, duration,soundPlayer,engineSoundId));
    }

    private void updatePositionFromBody() {
        Vector2 position = carBody.getPosition();
        carImage.setPosition(position.x - carImage.getWidth() / 2, position.y - carImage.getHeight() / 2);

        // Update the door positions as well
        float doorOffsetY = (carImage.getHeight() - leftDoorImage.getHeight()) / 2; // Position doors vertically in the middle of the car
        leftDoorImage.setPosition(carImage.getX() - leftDoorImage.getWidth(), carImage.getY() + doorOffsetY);
        rightDoorImage.setPosition(carImage.getX() + carImage.getWidth(), carImage.getY() + doorOffsetY);

        // Update the collision rectangle position
        collisionRectangle.setPosition(carImage.getX(), carImage.getY());
    }

    public void openDoors() {
        if (doorsOpen) {
            return;
        }
        doorsOpen = true;

        // Update door positions to current car position
        float doorOffsetX = 0; // Align doors with the car edges
        float doorOffsetY = (carImage.getHeight() - leftDoorImage.getHeight()) / 2; // Position doors vertically in the middle of the car

        leftDoorImage.setPosition(carImage.getX() + doorOffsetX - leftDoorImage.getWidth(), carImage.getY() + doorOffsetY);
        rightDoorImage.setPosition(carImage.getX() + carImage.getWidth(), carImage.getY() + doorOffsetY);

        float doorMoveDistance = -4;  // Move distance for the door (horizontal movement)
        float doorRotationAngle = 75; // Rotate angle for the door

        leftDoorImage.addAction(Actions.sequence(
                Actions.moveBy(-doorMoveDistance, 0, 0.5f),  // Move left door out horizontally
                Actions.rotateBy(doorRotationAngle, 0.5f)  // Rotate left door clockwise
        ));

        rightDoorImage.addAction(Actions.sequence(
                Actions.moveBy(doorMoveDistance, 0, 0.5f),  // Move right door out horizontally
                Actions.rotateBy(-doorRotationAngle, 0.5f)  // Rotate right door counterclockwise
        ));


        // Play door opening sound
        if (carDoorOpenedSound != null) {
            soundPlayer.play(getPosition(), carDoorOpenedSound, 1.0f, false);
        } else {
            Gdx.app.error("Car", "Door opening sound is null.");
        }

        if (engineSoundPlaying && engineSoundId != -1) {
                    soundPlayer.stop(engineSoundId);
                    engineSoundPlaying = false;
                    engineSoundId = -1;
        }
    }



    @Override
    public void setPosition(float x, float y) {
        carImage.setPosition(x, y);
        carBody.setTransform(x + carImage.getWidth() / 2, y + carImage.getHeight() / 2, 0);

        float doorOffsetX = 0; // Align doors with the car edges
        float doorOffsetY = (carImage.getHeight() - leftDoorImage.getHeight()) / 2; // Position doors vertically in the middle of the car

        leftDoorImage.setPosition(x + doorOffsetX - leftDoorImage.getWidth(), y + doorOffsetY);
        rightDoorImage.setPosition(x + carImage.getWidth(), y + doorOffsetY);

        // Update the collision rectangle position
        collisionRectangle.setPosition(x, y);

    }

    public void addToStage(Stage stage) {
        // Add the images to the stage
        stage.addActor(carImage);
        stage.addActor(leftDoorImage);
        stage.addActor(rightDoorImage);
    }

    public void addAction(SequenceAction action) {
        carImage.addAction(action);
    }

    public void activateCollision() {
        for (Fixture fixture : carBody.getFixtureList()) {
            fixture.setSensor(false); // Disable the sensor to activate collision
        }
    }

    public void deactivateCollision() {
        for (Fixture fixture : carBody.getFixtureList()) {
            fixture.setSensor(true); // Enable the sensor to deactivate collision
        }
    }

    private long lastCollisionTime = 0;
    private static final long COLLISION_COOLDOWN = 500; // 500 milliseconds cooldown between collisions

    public void checkCollisionWithPlayer(Player player) {
        long currentTime = TimeUtils.millis();
        if (currentTime - lastCollisionTime < COLLISION_COOLDOWN) {
            return; // Skip collision handling if cooldown is not over
        }

        for (Fixture fixture : carBody.getFixtureList()) {
            if (fixture.testPoint(player.getBody().getPosition())) {
                lastCollisionTime = currentTime; // Update last collision time
                if (carHitSound != null) {
                    carHitSound.play(1.0f); // Full volume
                }
                Vector2 collisionDirection = player.getBody().getPosition().cpy().sub(carBody.getPosition()).nor();
                player.takeDamage(1000, collisionDirection); // Damage player

                Gdx.app.postRunnable(() -> {
                    Demo game = (Demo) Gdx.app.getApplicationListener();
                    game.triggerHitByCarEffect(player.getBody().getPosition().x, player.getBody().getPosition().y);
                });
            }
        }
    }

    public Vector2 getPosition() {
        return new Vector2(carImage.getX() + carImage.getWidth() / 2, carImage.getY() + carImage.getHeight() / 2);
    }

    public void stopSoundEngine() {
        //soundPlayer.stop();
        if (engineSoundPlaying && engineSoundId != -1) {
            soundPlayer.stop(engineSoundId);
            soundPlayer.stop();
            engineSoundPlaying = false;
            engineSoundId = -1;
        }
    }
}

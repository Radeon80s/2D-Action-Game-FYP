package com.sotiris.engine.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Timer;
import com.sotiris.engine.entities.Player;
import games.spooky.gdx.sfx.SfxSound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class CutsceneCharacter extends Actor {
    private enum CutsceneState { APPROACHING, DISPLAYING_DIALOG, WALKING_AWAY, DONE }
    private static final float BODY_RADIUS = 48f;
    private static final float MEAN_CHARACTER_INTERVAL = 0.12f;
    private static final float CHARACTER_INTERVAL_VARIANCE = 0.05f;
    private static final float MIN_CHARACTER_INTERVAL = 0.05f;
    private static final float MAX_CHARACTER_INTERVAL = 0.3f;

    private CutsceneState currentState;
    private final Vector2 position;
    private final Vector2 velocity;
    private final Player player;
    private Animation<TextureRegion>[] walkAnimations;
    private Animation<TextureRegion> idleAnimation;
    private int currentDirection;
    private float stateTime;
    private final Skin skin;
    private final Stage uiStage;
    private final boolean isEndCutscene;
    private final Runnable onCutsceneEnd;

    private List<String> dialogLines;
    private int currentDialogIndex;
    private Label dialogLabel;

    private boolean isIdle = true;
    private final TiledMap map;
    private float mapHeight() {
        return map.getProperties().get("height", Integer.class) * map.getProperties().get("tileheight", Integer.class);
    }
    private float mapWidth() {
        return map.getProperties().get("width", Integer.class) * map.getProperties().get("tilewidth", Integer.class);
    }

    private Group dialogGroup;  // Group containing dialog UI and capturing inputs

    // Sound variables
    private SfxSound keyPressSound;
    private final AssetManager assetManager;

    // Typewriter effect variables
    private Timer.Task typewriterTask;
    private final Random random = new Random();
    private boolean isTyping = false;  // Flag to indicate if the typewriter effect is ongoing

    public CutsceneCharacter(float startX, float startY, Player player, Skin skin, Stage uiStage,
                             CollisionManager collisionManager, boolean isEndCutscene,
                             Runnable onCutsceneEnd, TiledMap map, AssetManager assetManager,String missionId) {
        this.map = map;
        this.position = new Vector2(startX, startY);
        this.velocity = new Vector2();
        this.player = player;
        this.skin = skin;
        this.uiStage = uiStage;
        this.isEndCutscene = isEndCutscene;
        this.onCutsceneEnd = onCutsceneEnd;
        this.assetManager = assetManager;

        setPosition(startX, startY);
        currentDirection = 0;
        stateTime = 0f;
        currentState = CutsceneState.APPROACHING;

        // Load walk and idle animations
        loadAnimations();

        // Load key press sound
        loadKeyPressSound();

        if(Objects.equals(missionId, "mission_1")) {
            // Initialize dialog lines based on whether it's the start or end cutscene
            dialogLines = new ArrayList<>();
            if (!isEndCutscene) {
                dialogLines.add("Welcome to my LibGDX 2D Game Demo.");
                dialogLines.add("good luck.");
            } else {
                dialogLines.add("you are the last standing.");
                dialogLines.add("IMPRESSIVE SKILLS.");
            }
        }
        currentDialogIndex = 0;
        setSize(BODY_RADIUS * 2, BODY_RADIUS * 2);
    }

    private void loadAnimations() {
        // Load walk animations
        Texture walkTexture = new Texture(Gdx.files.internal("enemy/enemy_walk_gun.png"));
        TextureRegion[][] tmpWalk = TextureRegion.split(walkTexture, 48, 64);

        walkAnimations = new Animation[8];
        for (int i = 0; i < 8; i++) {
            walkAnimations[i] = new Animation<>(0.1f, tmpWalk[i]);
        }

        // Load idle animation
        Texture idleTexture = new Texture(Gdx.files.internal("enemy/enemy_idle.png"));
        TextureRegion[][] tmpIdle = TextureRegion.split(idleTexture, 48, 64);
        idleAnimation = new Animation<>(0.1f, tmpIdle[0]);  // Assuming idle has one row
    }

    private void loadKeyPressSound() {

        keyPressSound = assetManager.get("sounds/keystroke.wav", SfxSound.class);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        stateTime += delta;

        switch (currentState) {
            case APPROACHING:
                isIdle = false;  // Moving towards player, not idle
                moveToPlayer(delta);
                break;
            case DISPLAYING_DIALOG:
                isIdle = true;  // Idle during dialog
                // No action during DISPLAYING_DIALOG, waiting for input
                break;
            case WALKING_AWAY:
                isIdle = false;  // Walking away
                walkAway(delta);
                break;
            case DONE:
                isIdle = true;  // Idle after done
                // End of cutscene, notify via callback
                if (onCutsceneEnd != null) {
                    onCutsceneEnd.run();
                }
                remove(); // Remove actor from the stage
                break;
        }
    }

    private void moveToPlayer(float delta) {
        Vector2 toPlayer = player.getPosition().cpy().sub(position).nor();
        Vector2 scaledVelocity = new Vector2(toPlayer).scl(150f); // Create a new scaled vector
        velocity.set(scaledVelocity);
        updateDirection(toPlayer); // Use the original direction vector

        Vector2 deltaMovement = new Vector2(velocity).scl(delta);
        position.add(deltaMovement);
        setPosition(position.x, position.y);


        if (position.dst(player.getPosition()) < 100) {  // Trigger dialog when close
            Vector2 directionToPlayer = toPlayer.cpy();
            Vector2 oppositeDirection = new Vector2(-directionToPlayer.x, -directionToPlayer.y).nor();
                // Update player's direction to face the cutscene character
            player.setFacingDirection(oppositeDirection);


            currentState = CutsceneState.DISPLAYING_DIALOG;
            displayDialog();
        }
    }

    private void updateDirection(Vector2 directionVec) {
        if (directionVec.isZero()) return;

        float angle = (directionVec.angleDeg() + 360) % 360;

        if (angle >= 337.5 || angle < 22.5) {
            currentDirection = 6; // Right
        } else if (angle >= 22.5 && angle < 67.5) {
            currentDirection = 4; // Right Up
        } else if (angle >= 67.5 && angle < 112.5) {
            currentDirection = 3; // Up
        } else if (angle >= 112.5 && angle < 157.5) {
            currentDirection = 2; // Left Up
        } else if (angle >= 157.5 && angle < 202.5) {
            currentDirection = 7; // Left
        } else if (angle >= 202.5 && angle < 247.5) {
            currentDirection = 1; // Left Down
        } else if (angle >= 247.5 && angle < 292.5) {
            currentDirection = 0; // Down
        } else if (angle >= 292.5 && angle < 337.5) {
            currentDirection = 5; // Right Down
        }
    }

    private void displayDialog() {
        if (currentDialogIndex < dialogLines.size()) {
            String currentLine = dialogLines.get(currentDialogIndex);

            createDialogUI();  // Create the UI elements

            playTypewriterEffect(currentLine);  // Start the typewriter effect
        } else {
            currentState = CutsceneState.WALKING_AWAY;
        }
    }

    private void createDialogUI() {
        // Remove previous dialog UI if it exists
        if (dialogGroup != null) {
            dialogGroup.remove();
        }

        // Create a Group to contain the dialog and capture input
        dialogGroup = new Group();
        dialogGroup.setSize(uiStage.getWidth(), uiStage.getHeight());
        dialogGroup.setPosition(0, 0);

        // Create a new Table to layout the dialog and avatar
        // To store the dialog table globally so it can be removed later
        Table dialogTable = new Table();
        dialogTable.setFillParent(true);  // Make sure the table covers the whole stage

        // Load the avatar texture
        Texture avatarTexture = new Texture(Gdx.files.internal("cutscene_avatar.png"));
        Image avatarImage = new Image(avatarTexture);  // Create an Image from the avatar texture
        avatarImage.setSize(96, 96);  // Adjust avatar size

        // Create the dialog label with empty text initially
        dialogLabel = new Label("", skin);
        dialogLabel.setFontScale(1.2f);  // Scale up the text a bit
        dialogLabel.setWrap(true);  // Allow the text to wrap
        dialogLabel.setAlignment(Align.center);  // Align the text to the center
        dialogLabel.setWidth(600);  // Set a reasonable width for the dialog

        // Add the avatar and label to the table
        dialogTable.add(avatarImage).size(96, 96).padRight(20);  // Add avatar with padding to the right
        dialogTable.add(dialogLabel).width(600).padLeft(20).padTop(20).padBottom(20).center();  // Add the dialog label

        // Position the dialogTable at the center of the screen
        dialogTable.center();  // Ensure the entire table is centered

        // Add padding to the dialog table
        dialogTable.pad(10);

        dialogGroup.addActor(dialogTable);  // Add the dialogTable to the dialogGroup

        // Add InputListener to the dialogGroup
        dialogGroup.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (isTyping) {
                    skipTypewriterEffect();
                } else {
                    proceedDialog();  // Proceed to the next dialog line
                }
                return true;
            }
        });

        uiStage.addActor(dialogGroup);  // Add the dialogGroup to the stage
    }
    private void playTypewriterEffect(final String text) {
        final int totalChars = text.length();
        final StringBuilder displayedText = new StringBuilder();
        final int[] currentCharIndex = {0};

        isTyping = true;

        // Cancel any existing typewriter tasks
        if (typewriterTask != null) {
            typewriterTask.cancel();
            typewriterTask = null;
        }

        typewriterTask = new Timer.Task() {
            @Override
            public void run() {
                // Display next character
                displayedText.append(text.charAt(currentCharIndex[0]));
                dialogLabel.setText(displayedText.toString());

                currentCharIndex[0]++;

                if (currentCharIndex[0] >= totalChars) {
                    // All characters displayed
                    isTyping = false;
                    this.cancel();
                    typewriterTask = null;
                } else {
                    // Play key press sound
                    playKeyPressSound();

                    // Schedule next character
                    float gaussian = (float) random.nextGaussian();
                    float interval = MEAN_CHARACTER_INTERVAL + gaussian * CHARACTER_INTERVAL_VARIANCE;
                    if (interval < MIN_CHARACTER_INTERVAL) {
                        interval = MIN_CHARACTER_INTERVAL;
                    } else if (interval > MAX_CHARACTER_INTERVAL) {
                        interval = MAX_CHARACTER_INTERVAL;
                    }
                    Timer.schedule(this, interval);
                }
            }
        };

        // Start the first character with minimal delay
        Timer.schedule(typewriterTask, MIN_CHARACTER_INTERVAL);
    }

    private void playKeyPressSound() {
        if (keyPressSound != null) {
            float volume = 0.4f + random.nextFloat() * 0.2f; // Volume between 0.4 and 0.6
            float pitch = 0.9f + random.nextFloat() * 0.2f;  // Pitch between 0.9 and 1.1
            keyPressSound.play(volume, pitch, 0f);
        }
    }

    private void skipTypewriterEffect() {
        if (typewriterTask != null) {
            typewriterTask.cancel();
            typewriterTask = null;
        }

        // Display full text
        dialogLabel.setText(dialogLines.get(currentDialogIndex));

        // Stop typing
        isTyping = false;
    }

    private void proceedDialog() {
        // Remove the dialogGroup
        if (dialogGroup != null) {
            dialogGroup.remove();
            dialogGroup = null;
        }

        // Cancel any typing tasks
        if (typewriterTask != null) {
            typewriterTask.cancel();
            typewriterTask = null;
        }

        // Increment the dialog index
        currentDialogIndex++;

        // Check if there are more dialog lines to display
        if (currentDialogIndex < dialogLines.size()) {
            displayDialog();  // Display the next dialog line
        } else {
            // Move to the next state once the dialog is done
            currentState = CutsceneState.WALKING_AWAY;
        }
    }

    private void walkAway(float delta) {
        Vector2 awayDirection = new Vector2(isEndCutscene ? 1 : -1, 0);
        Vector2 scaledVelocity = new Vector2(awayDirection).scl(100f); // Create a new scaled vector
        velocity.set(scaledVelocity);
        updateDirection(awayDirection);

        Vector2 deltaMovement = new Vector2(velocity).scl(delta);
        position.add(deltaMovement);
        setPosition(position.x, position.y);

        if (position.x < 0 || position.x > mapWidth() || position.y < 0 || position.y > mapHeight()) {
            currentState = CutsceneState.DONE;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        TextureRegion currentFrame;
        if (isIdle) {  // Check if character is idle
            currentFrame = idleAnimation.getKeyFrame(stateTime, true);
        } else {
            currentFrame = walkAnimations[currentDirection].getKeyFrame(stateTime, true);
        }
        batch.draw(currentFrame, getX(), getY(), 48, 64);
    }

    @Override
    public boolean remove() {
        cleanup();
        return super.remove();
    }

    private void cleanup() {
        // Remove the dialogGroup
        if (dialogGroup != null) {
            dialogGroup.remove();
            dialogGroup = null;
        }

        // Cancel any typing tasks
        if (typewriterTask != null) {
            typewriterTask.cancel();
            typewriterTask = null;
        }

    }
    public boolean isWalkingAway() {
        return currentState == CutsceneState.WALKING_AWAY;
    }
}

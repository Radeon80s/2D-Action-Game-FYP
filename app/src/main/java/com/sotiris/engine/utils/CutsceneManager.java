package com.sotiris.engine.utils;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.sotiris.engine.entities.Player;

import java.util.Random;

/**
 * CutsceneManager handles all cutscene-related logic including
 * NPC spawning, camera control, and cutscene state management.
 */
public class CutsceneManager {

    /**
     * Callback interface for cutscene events
     */
    public interface CutsceneCallback {
        void onStartCutsceneComplete();
        void onEndCutsceneComplete();
    }

    // Cutscene state
    private CutsceneCharacter cutsceneCharacter;
    private boolean followCutsceneCharacter = false;
    private boolean startCutsceneTriggered = false;
    private boolean endCutsceneTriggered = false;
    private boolean isTransitioning = false;
    private float targetZoom = 1.0f;

    // Dependencies
    private final Stage gameStage;
    private final Stage uiStage;
    private final Player player;
    private final Skin skin;
    private final TiledMap map;
    private final AssetManager assetManager;
    private final CollisionManager collisionManager;
    private final Random random;
    private CutsceneCallback callback;

    public CutsceneManager(Stage gameStage, Stage uiStage, Player player, Skin skin,
                           TiledMap map, AssetManager assetManager, CollisionManager collisionManager) {
        this.gameStage = gameStage;
        this.uiStage = uiStage;
        this.player = player;
        this.skin = skin;
        this.map = map;
        this.assetManager = assetManager;
        this.collisionManager = collisionManager;
        this.random = new Random();
    }

    public void setCallback(CutsceneCallback callback) {
        this.callback = callback;
    }

    /**
     * Triggers the start cutscene when player enters the cutscene zone
     */
    public boolean triggerStartCutscene() {
        if (startCutsceneTriggered || isTransitioning) return false;

        // Set flag FIRST to prevent retriggering
        startCutsceneTriggered = true;
        isTransitioning = true;
        player.setVelocity(new Vector2(0, 0));
        startCutscene();
        isTransitioning = false;
        return true;
    }

    /**
     * Triggers the end cutscene when all enemies are defeated
     */
    public boolean triggerEndCutscene() {
        if (endCutsceneTriggered || isTransitioning) return false;

        // Set flag FIRST to prevent retriggering
        endCutsceneTriggered = true;
        isTransitioning = true;
        player.setVelocity(new Vector2(0, 0));
        startEndCutscene();
        isTransitioning = false;
        return true;
    }

    private void startCutscene() {
        Vector2 npcStartPosition = getNPCStartPosition();
        cutsceneCharacter = new CutsceneCharacter(
                npcStartPosition.x,
                npcStartPosition.y,
                player,
                skin,
                uiStage,
                collisionManager,
                false,
                () -> {
                    if (callback != null) {
                        callback.onStartCutsceneComplete();
                    }
                },
                map,
                assetManager,
                "mission_1"
        );
        followCutsceneCharacter = true;
        targetZoom = 0.5f;
        gameStage.addActor(cutsceneCharacter);
    }

    private void startEndCutscene() {
        Vector2 npcStartPosition = getNPCStartPosition();
        cutsceneCharacter = new CutsceneCharacter(
                npcStartPosition.x,
                npcStartPosition.y,
                player,
                skin,
                uiStage,
                collisionManager,
                true,
                () -> {
                    if (callback != null) {
                        callback.onEndCutsceneComplete();
                    }
                },
                map,
                assetManager,
                "mission_1"
        );
        gameStage.addActor(cutsceneCharacter);
    }

    private Vector2 getNPCStartPosition() {
        Vector2 npcPosition = new Vector2();
        int side = random.nextInt(4);
        switch (side) {
            case 0:
                npcPosition.x = 0;
                npcPosition.y = random.nextFloat() * mapHeight();
                break;
            case 1:
                npcPosition.x = mapWidth();
                npcPosition.y = random.nextFloat() * mapHeight();
                break;
            case 2:
                npcPosition.x = random.nextFloat() * mapWidth();
                npcPosition.y = mapHeight();
                break;
            case 3:
                npcPosition.x = random.nextFloat() * mapWidth();
                npcPosition.y = 0;
                break;
        }
        return npcPosition;
    }

    /**
     * Updates camera position during cutscene
     * Call this in render() when in CUTSCENE_START state
     */
    public void updateCamera(OrthographicCamera camera) {
        if (cutsceneCharacter == null) return;

        float lerp = 0.1f;
        if (followCutsceneCharacter && !cutsceneCharacter.isWalkingAway()) {
            camera.position.x += (cutsceneCharacter.getX() - camera.position.x) * lerp;
            camera.position.y += (cutsceneCharacter.getY() - camera.position.y) * lerp;
        } else {
            camera.position.x += (player.getX() - camera.position.x) * lerp;
            camera.position.y += (player.getY() - camera.position.y) * lerp;
            camera.zoom = targetZoom;
        }

        if (Math.abs(camera.zoom - targetZoom) > 0.01f) {
            camera.zoom += (targetZoom - camera.zoom) * 0.05f;
        } else {
            camera.zoom = targetZoom;
        }
        camera.update();
    }

    /**
     * Resets cutscene state after start cutscene completes
     */
    public void onStartCutsceneFinished() {
        followCutsceneCharacter = false;
        targetZoom = 1.0f;
    }

    /**
     * Resets all cutscene state for game restart
     */
    public void reset() {
        startCutsceneTriggered = false;
        endCutsceneTriggered = false;
        followCutsceneCharacter = false;
        isTransitioning = false;
        targetZoom = 1.0f;
        cutsceneCharacter = null;
    }

    private float mapWidth() {
        return map.getProperties().get("width", Integer.class) *
               map.getProperties().get("tilewidth", Integer.class);
    }

    private float mapHeight() {
        return map.getProperties().get("height", Integer.class) *
               map.getProperties().get("tileheight", Integer.class);
    }

    // Getters
    public boolean isStartCutsceneTriggered() {
        return startCutsceneTriggered;
    }

    public boolean isEndCutsceneTriggered() {
        return endCutsceneTriggered;
    }

    public boolean isFollowingCutsceneCharacter() {
        return followCutsceneCharacter;
    }

    public float getTargetZoom() {
        return targetZoom;
    }

    public CutsceneCharacter getCutsceneCharacter() {
        return cutsceneCharacter;
    }
}

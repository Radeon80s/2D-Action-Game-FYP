package com.sotiris.engine.utils;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.RunnableAction;
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;
import com.sotiris.engine.entities.BulletPool;
import com.sotiris.engine.entities.Car;
import com.sotiris.engine.entities.Enemy;
import com.sotiris.engine.entities.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * WaveManager handles enemy wave spawning and car arrivals.
 * Extracted from Demo.java to reduce class complexity.
 */
public class WaveManager {

    public interface WaveCallback {
        void onAllWavesComplete();
    }

    private final int[] waveEnemies = {5, 4, 1, 2, 1, 4, 5};
    private int currentWave = 0;
    private int activeEnemies = 0;

    private final Stage gameStage;
    private final World world;
    private final Player player;
    private final TiledMap map;
    private final AssetManager assetManager;
    private final CollisionManager collisionManager;
    private final MySpatializedSoundPlayer<Vector2> soundPlayer;
    private final BulletPool bulletPool;
    private final Random random;

    private final List<Car> cars;
    private final List<RectangleMapObject> carSpawnPoints;
    private WaveCallback waveCallback;

    public WaveManager(Stage gameStage, World world, Player player, TiledMap map,
                       AssetManager assetManager, CollisionManager collisionManager,
                       MySpatializedSoundPlayer<Vector2> soundPlayer, BulletPool bulletPool) {
        this.gameStage = gameStage;
        this.world = world;
        this.player = player;
        this.map = map;
        this.assetManager = assetManager;
        this.collisionManager = collisionManager;
        this.soundPlayer = soundPlayer;
        this.bulletPool = bulletPool;
        this.random = new Random();
        this.cars = new ArrayList<>();
        this.carSpawnPoints = new ArrayList<>();

        extractCarSpawnPoints();
    }

    public void setWaveCallback(WaveCallback callback) {
        this.waveCallback = callback;
    }

    public void reset() {
        currentWave = 0;
        activeEnemies = 0;
        cars.clear();
        carSpawnPoints.clear();
        extractCarSpawnPoints();
    }

    public void startWaves() {
        spawnNextWave();
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                spawnNextWave();
            }
        }, 10, 10);
    }

    public void spawnNextWave() {
        if (player.isDead()) return;
        if (currentWave >= waveEnemies.length) return;

        if (carSpawnPoints.isEmpty()) {
            extractCarSpawnPoints();
        }

        RectangleMapObject spawnPointObject = carSpawnPoints.remove(random.nextInt(carSpawnPoints.size()));
        Rectangle spawnPoint = spawnPointObject.getRectangle();

        Car car = new Car(world, assetManager, soundPlayer);
        cars.add(car);

        float offScreenY = mapHeight();
        car.setPosition(spawnPoint.x, offScreenY);
        car.addToStage(gameStage);
        car.startSoundEngine();

        RunnableAction activateCollision = new RunnableAction();
        activateCollision.setRunnable(car::activateCollision);

        RunnableAction openDoorsAndAddCollision = new RunnableAction();
        openDoorsAndAddCollision.setRunnable(() -> {
            car.openDoors();
            Rectangle collision = spawnPointObject.getRectangle();
            if (!collisionManager.isPlayerInsideRectangle(player, collision)) {
                collisionManager.addCollisionRectangle(collision);
            } else {
                boolean change = !collisionManager.isPlayerInsideRectangle(player, collision);
                int attempts = 0;
                while (!change && attempts < 5) {
                    change = !collisionManager.isPlayerInsideRectangle(player, collision);
                    attempts++;
                    if (change) {
                        collisionManager.addCollisionRectangle(collision);
                    }
                }
            }
        });

        RunnableAction deactivateCollision = new RunnableAction();
        deactivateCollision.setRunnable(car::deactivateCollision);

        RunnableAction spawnEnemiesAction = new RunnableAction();
        spawnEnemiesAction.setRunnable(() -> spawnWaveEnemies(spawnPoint, currentWave));

        RunnableAction removeCar = new RunnableAction();
        removeCar.setRunnable(() -> cars.remove(car));

        SequenceAction sequence = new SequenceAction();
        sequence.addAction(activateCollision);
        sequence.addAction(Actions.run(() -> car.moveTo(spawnPoint.x, spawnPoint.y, 2f)));
        sequence.addAction(Actions.delay(2.1f));
        sequence.addAction(openDoorsAndAddCollision);
        sequence.addAction(deactivateCollision);
        sequence.addAction(spawnEnemiesAction);
        sequence.addAction(Actions.delay(0.5f));
        sequence.addAction(removeCar);

        car.addAction(sequence);
        currentWave++;
    }

    private void spawnWaveEnemies(Rectangle area, int wave) {
        if (wave >= waveEnemies.length) return;

        int numEnemies = waveEnemies[wave];
        float carX = area.x;
        float carY = area.y;
        float initialGap = 40f;
        float offsetY = 10f;

        Array<Vector2> spawnedPositions = new Array<>();

        for (int i = 0; i < numEnemies; i++) {
            Vector2 spawnPos = findValidSpawnPosition(carX, carY, initialGap, offsetY, spawnedPositions);

            if (spawnPos != null) {
                spawnEnemy(spawnPos);
                spawnedPositions.add(spawnPos);
            } else {
                spawnPos = getRandomSpawnPosition();
                spawnEnemy(spawnPos);
                spawnedPositions.add(spawnPos);
            }
        }
    }

    private Vector2 findValidSpawnPosition(float carX, float carY, float initialGap,
                                           float offsetY, Array<Vector2> spawnedPositions) {
        float gap = initialGap;
        int maxAttempts = 50;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            for (int side = -1; side <= 1; side += 2) {
                Vector2 spawnPos = new Vector2(
                        carX + side * gap * (attempt + 1),
                        carY + offsetY + MathUtils.random(-20, 20)
                );

                spawnPos.x = MathUtils.clamp(spawnPos.x, 20, mapWidth() - 20);
                spawnPos.y = MathUtils.clamp(spawnPos.y, 20, mapHeight() - 20);

                if (isValidPosition(spawnPos, spawnedPositions)) {
                    return spawnPos;
                }
            }
            gap *= 0.9f;
        }
        return null;
    }

    private boolean isValidPosition(Vector2 pos, Array<Vector2> spawnedPositions) {
        Rectangle spawnRect = new Rectangle(pos.x - 10, pos.y - 10, 20, 20);

        for (Rectangle collision : collisionManager.getCollisionRectangles()) {
            if (collision.overlaps(spawnRect)) {
                return false;
            }
        }

        for (Vector2 otherPos : spawnedPositions) {
            if (pos.dst(otherPos) < 30) {
                return false;
            }
        }
        return true;
    }

    private Vector2 getRandomSpawnPosition() {
        Vector2 pos = new Vector2();
        int maxAttempts = 50;

        for (int i = 0; i < maxAttempts; i++) {
            pos.set(MathUtils.random(20, mapWidth() - 20), MathUtils.random(20, mapHeight() - 20));
            if (isValidPosition(pos, new Array<>())) {
                return pos;
            }
        }
        return pos;
    }

    private void spawnEnemy(Vector2 spawnPos) {
        Enemy enemy = new Enemy(assetManager, spawnPos.x, spawnPos.y, player, collisionManager, world, bulletPool);
        enemy.setOnDeath(() -> {
            activeEnemies--;
            if (activeEnemies == 0 && currentWave >= waveEnemies.length) {
                if (waveCallback != null) {
                    waveCallback.onAllWavesComplete();
                }
            }
        });
        gameStage.addActor(enemy);
        activeEnemies++;
    }

    private void extractCarSpawnPoints() {
        MapLayer carSpawnLayer = map.getLayers().get("CarSpawnPoints");
        if (carSpawnLayer != null) {
            for (MapObject object : carSpawnLayer.getObjects()) {
                if (object instanceof RectangleMapObject) {
                    RectangleMapObject rectObject = (RectangleMapObject) object;
                    carSpawnPoints.add(rectObject);
                }
            }
        }
    }

    private float mapWidth() {
        return map.getProperties().get("width", Integer.class) *
               map.getProperties().get("tilewidth", Integer.class);
    }

    private float mapHeight() {
        return map.getProperties().get("height", Integer.class) *
               map.getProperties().get("tileheight", Integer.class);
    }

    public List<Car> getCars() {
        return cars;
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public int getActiveEnemies() {
        return activeEnemies;
    }

    public void checkCarCollisions() {
        for (Car car : cars) {
            car.checkCollisionWithPlayer(player);
        }
    }

    public void stopAllCarSounds() {
        for (Car car : cars) {
            car.stopSoundEngine();
        }
    }
}

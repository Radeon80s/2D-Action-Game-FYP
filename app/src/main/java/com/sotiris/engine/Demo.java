package com.sotiris.engine;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.InputMultiplexer;

import com.crashinvaders.vfx.VfxManager;
import com.crashinvaders.vfx.effects.RadialBlurEffect;

import com.sotiris.engine.entities.BulletPool;
import com.sotiris.engine.entities.Car;
import com.sotiris.engine.entities.Enemy;
import com.sotiris.engine.entities.Player;
import com.sotiris.engine.utils.CollisionManager;
import com.sotiris.engine.utils.CutsceneCharacter;
import com.sotiris.engine.utils.WaveManager;
import com.sotiris.engine.ui.Joystick;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.sotiris.engine.utils.MySpatializedSoundPlayer;

import com.sotiris.engine.ui.PieMenuManager;

import games.spooky.gdx.sfx.Effects;
import games.spooky.gdx.sfx.SfxMusic;
import games.spooky.gdx.sfx.SfxMusicLoader;
import games.spooky.gdx.sfx.SfxMusicPlaylist;
import games.spooky.gdx.sfx.SfxSound;
import games.spooky.gdx.sfx.SfxSoundLoader;
import games.spooky.gdx.sfx.spatial.SomeSoundSpatializer2;

public class Demo extends ApplicationAdapter {
    private CutsceneCharacter cutsceneCharacter;
    private float targetZoom = 1.0f;
    private boolean followCutsceneCharacter = false;
    private BulletPool bulletPool;
    private enum GameState {
        CUTSCENE_START,
        PLAYING,
        GAME_OVER,
        CUTSCENE_END,
        MISSION_COMPLETE,
        SETTINGS
    }

    private GameState currentGameState;
    private AssetManager assetManager;
    private SfxMusicPlaylist musicPlayer;
    private MySpatializedSoundPlayer<Vector2> soundPlayer;
    private SomeSoundSpatializer2 spatializer;
    private Skin skin;
    private Stage gameStage, uiStage, gameOverStage, missionCompleteStage, settingsStage;
    private PieMenuManager pieMenuManager;
    private Slider volumeSlider;
    private Slider settingsVolumeSlider;
    private TextButton muteButton;
    private Player player;
    private float savedVolume = 1.0f;  // Default matches soundPlayer initial volume
    private Random random;
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private TiledMap map;
    private OrthogonalTiledMapRenderer mapRenderer;
    private Box2DDebugRenderer debugRenderer;
    private VfxManager vfxManager;
    private RadialBlurEffect radialBlurEffect;
    private Texture bloodOverlayTexture;
    private ParticleEffect carBloodEffect;
    private Joystick joystick;
    private Texture rifleTexture;
    private static final float WEAPON_SCALE = 0.2f;
    private boolean isShooting = false;
    private CollisionManager collisionManager;
    private WaveManager waveManager;
    private World world;
    private final Rectangle cutsceneRectangle = new Rectangle(600, 300, 250, 250);
    private boolean isTransitioning = false;
    private boolean cutsceneTriggered = false;
    private static final float SPAWN_PADDING = 100f;
    private final Map<String, Texture> textureCache = new HashMap<>();
    float delta;
    private final Runnable onMissionComplete;
    boolean Male;
    public Demo(Runnable onMissionComplete, String gender) {
        this.onMissionComplete = onMissionComplete;
        if (gender != null && gender.contains("Female")) {
            Male = false;
        } else {
            Male = true; // Default to male if not specified
        }
    }
    private void notifyAndroidLauncher() {
        onMissionComplete.run();
    }

    @Override
    public void create() {

        delta = Gdx.graphics.getDeltaTime();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 800, 480);
        batch = new SpriteBatch();

        assetManager = new AssetManager(new InternalFileHandleResolver());
        assetManager.setLoader(SfxMusic.class, new SfxMusicLoader(new InternalFileHandleResolver()));
        assetManager.setLoader(SfxSound.class, new SfxSoundLoader(new InternalFileHandleResolver()));
        assetManager.setLoader(TiledMap.class, new TmxMapLoader(new InternalFileHandleResolver()));

        loadAssets();

        skin = new Skin();
        skin.addRegions(assetManager.get("skin/uiskin.atlas", TextureAtlas.class));
        skin.add("font", assetManager.get("skin/default.fnt", BitmapFont.class));
        skin.load(Gdx.files.internal("skin/uiskin.json"));


        map = assetManager.get("maps/map.tmx", TiledMap.class);
        mapRenderer = new OrthogonalTiledMapRenderer(map, 1f);
        collisionManager = new CollisionManager(map);
        world = new World(new Vector2(0, 0), true);
        debugRenderer = new Box2DDebugRenderer();
        gameStage = new Stage(new ExtendViewport(800, 480, camera));
        uiStage = new Stage(new ExtendViewport(800, 480));
        gameOverStage = new Stage(new ExtendViewport(800, 480));
        missionCompleteStage = new Stage(new ExtendViewport(800, 480));
        settingsStage = new Stage(new ExtendViewport(800, 480));

        setInputProcessing();

        random = new Random();
        addStaticCars();

        bulletPool = new BulletPool(gameStage, assetManager, collisionManager);
        initializeSoundPlayer();

        player = new Player(400, 400, world, collisionManager, assetManager,Male, bulletPool);
        Vector2 spawnPos = getRandomSpawnPosition(player.getWidth(), player.getHeight());
        player.setPosition(spawnPos.x, spawnPos.y);
        Body playerBody = player.getBody();
        playerBody.setTransform(spawnPos.x + player.getWidth() / 2, spawnPos.y + player.getHeight() / 2, 0);

        gameStage.addActor(player);
        joystick = new Joystick("touchpad.png", "touchpad-knob.png", 110, 110);
        uiStage.addActor(joystick);
        initializeShootButton();
        rifleTexture = getTexture("rifle.png");
        shapeRenderer = new ShapeRenderer();

        // Initialize WaveManager
        waveManager = new WaveManager(gameStage, world, player, map, assetManager,
                collisionManager, soundPlayer, bulletPool);
        waveManager.setWaveCallback(this::triggerCutsceneEnd);

        definePieMenuZones();
        initializeVisualEffects();
        initializeSpatializer();
        initializeVolumeSlider();
        initializeMuteButton();
        initializeGameOverUI();
        initializeMissionCompleteUI();
        initializeSettingsUI();
        currentGameState = GameState.PLAYING;


    }
    public BulletPool getBulletPool() {
        return bulletPool;
    }


    private void loadAssets() {
        assetManager.load("maps/map.tmx", TiledMap.class);
        assetManager.load("skin/uiskin.atlas", TextureAtlas.class);
        assetManager.load("skin/default.fnt", BitmapFont.class);
        assetManager.load("rifle.png", Texture.class);
        assetManager.load("touchpad.png", Texture.class);
        assetManager.load("touchpad-knob.png", Texture.class);
        assetManager.load("button_down_arcade.9.png", Texture.class);
        assetManager.load("button_up_arcade.9.png", Texture.class);
        assetManager.load("blood_overlay.png", Texture.class);
        assetManager.load("blood.p", ParticleEffect.class);
        for (int i = 0; i < 12; i++) {
            assetManager.load("cars/carstatic_" + i + ".png", Texture.class);
        }
        assetManager.load("cars/car.png", Texture.class);
        assetManager.load("cars/left_door.png", Texture.class);
        assetManager.load("cars/right_door.png", Texture.class);
        assetManager.load("enemy/enemy_idle.png", Texture.class);
        assetManager.load("enemy/enemy_walk_gun.png", Texture.class);
        assetManager.load("enemy/enemy_shoot.png", Texture.class);
        assetManager.load("enemy/enemy_death.png", Texture.class);
        assetManager.load("sounds/engine.wav", SfxSound.class);
        assetManager.load("sounds/car_door_opened.wav", SfxSound.class);
        assetManager.load("sounds/car_hit.wav", SfxSound.class);
        assetManager.load("sounds/keystroke.wav", SfxSound.class);

        assetManager.load("male/Idle_Gun.png", Texture.class);
        assetManager.load("male/idle_spear.png", Texture.class);
        assetManager.load("male/idle.png", Texture.class);
        assetManager.load("male/Walk_Gun.png", Texture.class);
        assetManager.load("male/walk_spear.png", Texture.class);
        assetManager.load("male/walk.png", Texture.class);
        assetManager.load("male/Run_Gun.png", Texture.class);
        assetManager.load("male/run_spear.png", Texture.class);
        assetManager.load("male/run.png", Texture.class);
        assetManager.load("male/Shooting.png", Texture.class);
        assetManager.load("male/attack_spear.png", Texture.class);
        assetManager.load("male/death_Gun.png", Texture.class);
        assetManager.load("male/death_spear.png", Texture.class);
        assetManager.load("male/death_normal.png", Texture.class);

        assetManager.load("bullet.png", Texture.class);

        assetManager.load("female/Idle_Gun.png", Texture.class);
        assetManager.load("female/idle_spear.png", Texture.class);
        assetManager.load("female/idle.png", Texture.class);
        assetManager.load("female/Walk_Gun.png", Texture.class);
        assetManager.load("female/walk_spear.png", Texture.class);
        assetManager.load("female/walk.png", Texture.class);
        assetManager.load("female/Run_Gun.png", Texture.class);
        assetManager.load("female/run_spear.png", Texture.class);
        assetManager.load("female/run.png", Texture.class);
        assetManager.load("female/Shooting.png", Texture.class);
        assetManager.load("female/attack_spear.png", Texture.class);
        assetManager.load("female/death_Gun.png", Texture.class);
        assetManager.load("female/death_spear.png", Texture.class);
        assetManager.load("female/death_normal.png", Texture.class);

        assetManager.load("Shadow.png", Texture.class);
        assetManager.finishLoading();
    }

    private Texture getTexture(String path) {
        if (!textureCache.containsKey(path)) {
            textureCache.put(path, assetManager.get(path, Texture.class));
        }
        return textureCache.get(path);
    }

    public void triggerHitByCarEffect(float x, float y) {
        Vector2 screenCoords = gameStage.getViewport().project(new Vector2(x, y));
        carBloodEffect.setPosition(screenCoords.x, screenCoords.y);
        carBloodEffect.start();
        Timer.schedule(new Timer.Task(){
            @Override
            public void run() {
                carBloodEffect.allowCompletion();
            }
        }, 3);
    }

    private float shootButtonX;
    private float shootButtonY;
    private float shootButtonWidth;
    private float shootButtonHeight;
    private void initializeShootButton() {
        Texture shootButtonUpTexture = getTexture("button_up_arcade.9.png");
        Texture shootButtonDownTexture = getTexture("button_down_arcade.9.png");
        ImageButton shootButton = new ImageButton(new TextureRegionDrawable(shootButtonUpTexture), new TextureRegionDrawable(shootButtonDownTexture));


        float screenWidth = uiStage.getViewport().getWorldWidth();
        float screenHeight = uiStage.getViewport().getWorldHeight();

        // Set a comfortable size for the button (about 15% of screen height)
        float buttonSize = screenHeight * 0.35f;

        // Position at bottom right with some padding
        shootButtonX = screenWidth - buttonSize - 20f;
        shootButtonY = 20f;
        shootButtonWidth = buttonSize;
        shootButtonHeight = buttonSize;

        shootButton.setPosition(shootButtonX, shootButtonY);
        shootButton.setSize(shootButtonWidth, shootButtonHeight);

        shootButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (currentGameState == GameState.PLAYING) {
                    isShooting = true;
                    player.shoot();
                }
                return true;
            }
            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (currentGameState == GameState.PLAYING) {
                    isShooting = false;
                    player.stopShooting();
                }
            }
        });
        uiStage.addActor(shootButton);
    }

    private void initializeVisualEffects() {
        vfxManager = new VfxManager(Pixmap.Format.RGBA8888);
        radialBlurEffect = new RadialBlurEffect(8);
        float healthFactor = player.getHealth() / 50f;
        float blurStrength = Math.max(0.05f, 0.3f * (1 - healthFactor));
        radialBlurEffect.setStrength(blurStrength);
        vfxManager.addEffect(radialBlurEffect);
        bloodOverlayTexture = getTexture("blood_overlay.png");
        carBloodEffect = new ParticleEffect();
        carBloodEffect.load(Gdx.files.internal("blood.p"), Gdx.files.internal(""));
    }

    private void definePieMenuZones() {
        // Get screen dimensions
        float screenWidth = uiStage.getViewport().getWorldWidth();
        float screenHeight = uiStage.getViewport().getWorldHeight();

        // Calculate appropriate padding for joystick zone
        // Create a reasonably sized exclusion zone around the joystick
        float joystickPadding = 180f; // Adjust based on touch accuracy needed
        Rectangle joystickZone = new Rectangle(
                joystick.getX() - joystickPadding,
                joystick.getY() - joystickPadding,
                joystick.getWidth() + (joystickPadding * 2),
                joystick.getHeight() + (joystickPadding * 2)
        );

        // Create an exclusion zone around the shoot button
        float shootButtonPadding = 40f; // Adjust based on touch accuracy needed
        Rectangle shootButtonZone = new Rectangle(
                shootButtonX - shootButtonPadding,
                shootButtonY - shootButtonPadding,
                shootButtonWidth + (shootButtonPadding * 2),
                shootButtonHeight + (shootButtonPadding * 2)
        );

        // Define the allowed area for the pie menu (essentially the play area)
        float marginX = screenWidth * 0.03f;
        float marginY = screenHeight * 0.03f;
        Rectangle allowedZone = new Rectangle(
                marginX,
                marginY,
                screenWidth - (2 * marginX),
                screenHeight - (2 * marginY)
        );

        // Create the PieMenuManager with the updated zones
        pieMenuManager = new PieMenuManager(
                uiStage,
                skin,
                allowedZone,
                joystickZone,
                shootButtonZone,
                new Rectangle(), // Additional exclusion zone if needed
                player
        );

    }

    private void initializeSoundPlayer() {
        soundPlayer = new MySpatializedSoundPlayer<>();
        soundPlayer.setVolume(1.0f);
    }

    private void initializeSpatializer() {
        spatializer = new SomeSoundSpatializer2();
        spatializer.setHorizontalRange(700f);
        spatializer.setVerticalRange(500f);
        spatializer.setCenter(player.getX(), player.getY(), 0f);
        soundPlayer.setSpatializer(spatializer);
    }

    private void initializeVolumeSlider() {
        volumeSlider = new Slider(0f, 1f, 0.1f, false, skin);
        volumeSlider.setValue(soundPlayer.getVolume());
        volumeSlider.setPosition(500, 50);
        volumeSlider.setSize(150, 35);
        volumeSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float volume = volumeSlider.getValue();
                if (soundPlayer != null) {
                    soundPlayer.setVolume(volume);
                    settingsVolumeSlider.setValue(volume);

                    // Update mute button text based on slider position
                    if (volume > 0f) {
                        muteButton.setText("Mute");
                        savedVolume = volume;
                    } else {
                        muteButton.setText("Unmute");
                    }
                }
            }
        });
        uiStage.addActor(volumeSlider);
    }

    private void initializeMuteButton() {
        muteButton = new TextButton("Mute", skin);
        muteButton.setPosition(400, 50);
        muteButton.setSize(100, 30);
        muteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (soundPlayer != null) {
                    if (soundPlayer.getVolume() > 0f) {
                        // Muting: Save current volume first
                        savedVolume = soundPlayer.getVolume();
                        soundPlayer.setVolume(0f);
                        volumeSlider.setValue(0f);
                        settingsVolumeSlider.setValue(0f);
                        muteButton.setText("Unmute");
                    } else {
                        // Unmuting: Restore saved volume
                        soundPlayer.setVolume(savedVolume);
                        volumeSlider.setValue(savedVolume);
                        settingsVolumeSlider.setValue(savedVolume);
                        muteButton.setText("Mute");
                    }
                }
            }
        });
        uiStage.addActor(muteButton);
    }

    private void initializeGameOverUI() {
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
                restartMission();
            }
        });
        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.exit();
            }
        });
        table.add(gameOverLabel).padBottom(20);
        table.row();
        table.add(restartButton).width(200).height(50).padBottom(10);
        table.row();
        table.add(exitButton).width(200).height(50);
    }

    private void initializeMissionCompleteUI() {
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
                notifyAndroidLauncher();
                Gdx.app.exit();
            }
        });

        table.add(missionCompleteLabel).padBottom(20);
        table.row();
        table.add(label2).width(100).height(40).padBottom(10);
        table.row();
        table.add(exitButton).width(200).height(50);
    }

    private void initializeSettingsUI() {
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
                    volumeSlider.setValue(volume);
                }
            }
        });
        TextButton backButton = new TextButton("Back", skin);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                currentGameState = GameState.PLAYING;
                setInputProcessing();
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

    private void addStaticCars() {
        MapLayer carStaticLayer = map.getLayers().get("CarStaticPoints");
        if (carStaticLayer != null) {
            for (MapObject object : carStaticLayer.getObjects()) {
                if (object instanceof RectangleMapObject) {
                    RectangleMapObject rectObject = (RectangleMapObject) object;
                    Rectangle rect = rectObject.getRectangle();
                    collisionManager.addCollisionRectangle(rect);
                    int carIndex = random.nextInt(12);
                    String carTexturePath = "cars/carstatic_" + carIndex + ".png";
                    Texture carTexture = getTexture(carTexturePath);
                    Image carImage = new Image(carTexture);
                    float carX = rect.x + rect.width / 2 - carImage.getWidth() / 2;
                    float carY = rect.y + rect.height / 2 - carImage.getHeight() / 2;
                    carImage.setPosition(carX, carY);
                    gameStage.addActor(carImage);
                    Rectangle collisionRect = new Rectangle(carX, carY, carImage.getWidth(), carImage.getHeight());
                    collisionManager.addCollisionRectangle(collisionRect);
                }
            }
        }
    }

    private Vector2 getRandomSpawnPosition(float playerWidth, float playerHeight) {
        Vector2 spawnPos = new Vector2();
        boolean validPosition = false;
        int attempts = 0;
        int maxAttempts = 10;
        while (!validPosition && attempts < maxAttempts) {
            int side = random.nextInt(4);
            Rectangle area = cutsceneRectangle;
            switch (side) {
                case 0:
                    spawnPos.x = area.x - SPAWN_PADDING - playerWidth;
                    spawnPos.y = area.y + random.nextFloat() * area.height;
                    break;
                case 1:
                    spawnPos.x = area.x + area.width + SPAWN_PADDING;
                    spawnPos.y = area.y + random.nextFloat() * area.height;
                    break;
                case 2:
                    spawnPos.x = area.x + random.nextFloat() * area.width;
                    spawnPos.y = area.y + area.height + SPAWN_PADDING;
                    break;
                case 3:
                    spawnPos.x = area.x + random.nextFloat() * area.width;
                    spawnPos.y = area.y - SPAWN_PADDING - playerHeight;
                    break;
            }
            spawnPos.x = Math.max(0, Math.min(spawnPos.x, mapWidth() - playerWidth));
            spawnPos.y = Math.max(0, Math.min(spawnPos.y, mapHeight() - playerHeight));
            Rectangle playerRect = new Rectangle(spawnPos.x, spawnPos.y, playerWidth, playerHeight);
            validPosition = !collisionManager.isPlayerInsideRectangle(player,playerRect);
            attempts++;
        }
        if (!validPosition) {
            spawnPos.set(100, 100);
        }
        return spawnPos;
    }

    private float mapWidth() {
        return map.getProperties().get("width", Integer.class) * map.getProperties().get("tilewidth", Integer.class);
    }

    private float mapHeight() {
        return map.getProperties().get("height", Integer.class) * map.getProperties().get("tileheight", Integer.class);
    }

    private void triggerCutsceneEnd() {
        if (currentGameState == GameState.CUTSCENE_END || isTransitioning) return;
        isTransitioning = true;
        currentGameState = GameState.CUTSCENE_END;
        player.setVelocity(new Vector2(0, 0));

        startEndCutscene();
        isTransitioning = false;
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

                    if (currentGameState == GameState.CUTSCENE_END) {
                        triggerMissionCompleteSequence();
                    }
                },
                map,
                assetManager,
                "mission_1"
        );
        gameStage.addActor(cutsceneCharacter);

    }
    private void triggerCutsceneStart() {
        if (cutsceneTriggered || currentGameState == GameState.CUTSCENE_START || isTransitioning) return;
        isTransitioning = true;
        player.setVelocity(new Vector2(0, 0));
        startCutscene();
        isTransitioning = false;
        cutsceneTriggered = true;
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

                    if (currentGameState == GameState.CUTSCENE_START) {
                        currentGameState = GameState.PLAYING;
                        followCutsceneCharacter = false;
                        targetZoom = 1.0f;
                        setInputProcessing();
                    }

                    spawnFirstWave();
                    pieMenuManager.enableInput();
                },
                map,
                assetManager,
                "mission_1"
        );
        boolean isCutsceneActive = true;
        followCutsceneCharacter = true;
        targetZoom = 0.5f;
        currentGameState = GameState.CUTSCENE_START;
        pieMenuManager.disableInput();
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

    private void spawnFirstWave() {
        waveManager.startWaves();
    }

    private void triggerMissionCompleteSequence() {
        if (currentGameState == GameState.MISSION_COMPLETE || isTransitioning) return;
        isTransitioning = true;
        currentGameState = GameState.MISSION_COMPLETE;
        Gdx.input.setInputProcessor(missionCompleteStage);
        isTransitioning = false;
    }

    private void triggerGameOverSequence() {
        if (currentGameState == GameState.GAME_OVER || isTransitioning) return;
        isTransitioning = true;
        currentGameState = GameState.GAME_OVER;
        Gdx.input.setInputProcessor(gameOverStage);
        isTransitioning = false;
    }

    private void triggerSettingsSequence() {
        if (currentGameState == GameState.SETTINGS || isTransitioning) return;
        isTransitioning = true;
        currentGameState = GameState.SETTINGS;
        Gdx.input.setInputProcessor(settingsStage);
        isTransitioning = false;
    }

    private void restartMission() {
        soundPlayer.stop();

        gameStage.clear();
        clearDynamicEntities();
        resetGameWorld();
        resetUI();
        resetPlayer();
        setInputProcessing();
        currentGameState = GameState.PLAYING;
    }

    private void setInputProcessing() {
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(gameStage);
        Gdx.input.setInputProcessor(multiplexer);
    }

    private void resetUI() {
        if (joystick != null) {
            joystick.reset();
        }
        if (pieMenuManager != null) {
            pieMenuManager.dispose();
            pieMenuManager = null;
        }
        initializeShootButton();
        definePieMenuZones();
    }

    private void resetGameWorld() {
        waveManager.reset();
        clearDynamicEntities();
        addStaticCars();
        initializeGameOverUI();
        initializeMissionCompleteUI();
        initializeSettingsUI();
    }

    private void resetPlayer() {
        player.reset();
        Vector2 spawnPos = getRandomSpawnPosition(player.getWidth(), player.getHeight());
        player.setPosition(spawnPos.x, spawnPos.y);
        Body playerBody = player.getBody();
        playerBody.setTransform(spawnPos.x + player.getWidth() / 2, spawnPos.y + player.getHeight() / 2, 0);
        playerBody.setLinearVelocity(0,0);
        gameStage.addActor(player);
    }

    private void clearDynamicEntities() {
        waveManager.stopAllCarSounds();
        for (Actor actor : new Array<>(gameStage.getActors())) {
            if (actor instanceof Player || actor instanceof Enemy || actor instanceof Car) {
                actor.remove();
            }
        }
        collisionManager.clearCollisionRectangles();
    }

    private static final float TIME_STEP = 1/60f;
    private float accumulator = 0f;
    @Override
    public void render() {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        delta = Gdx.graphics.getDeltaTime();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        accumulator += delta;

        while (accumulator >= TIME_STEP) {
            world.step(TIME_STEP, 8, 3);
            accumulator -= TIME_STEP;
        }

        switch (currentGameState) {
            case CUTSCENE_START:
                if (followCutsceneCharacter && !cutsceneCharacter.isWalkingAway()) {
                    float lerp = 0.1f;
                    camera.position.x += (cutsceneCharacter.getX() - camera.position.x) * lerp;
                    camera.position.y += (cutsceneCharacter.getY() - camera.position.y) * lerp;
                } else {
                    float playerLerp = 0.1f;
                    camera.position.x += (player.getX() - camera.position.x) * playerLerp;
                    camera.position.y += (player.getY() - camera.position.y) * playerLerp;
                    camera.zoom = targetZoom;
                    camera.update();
                }
                if (Math.abs(camera.zoom - targetZoom) > 0.01f) {
                    camera.zoom += (targetZoom - camera.zoom) * 0.05f;
                } else {
                    camera.zoom = targetZoom;
                }

                camera.update();
                break;
            case PLAYING:

                if (player != null && player.isDead()) triggerGameOverSequence();

                Rectangle playerBounds = new Rectangle(player.getX(), player.getY(), player.getWidth(), player.getHeight());
                if (cutsceneRectangle.contains(playerBounds)) triggerCutsceneStart();
                float playerLerp = 0.1f;
                camera.position.x += (player.getX() - camera.position.x) * playerLerp;
                camera.position.y += (player.getY() - camera.position.y) * playerLerp;
                camera.zoom = targetZoom;
                camera.update();
                break;
            case GAME_OVER:
            case MISSION_COMPLETE:
            case SETTINGS:
                break;
        }
        if (spatializer != null) {
            spatializer.setCenter(player.getX(), player.getY(), 0f);
        }
        if (soundPlayer != null) soundPlayer.update(delta);
        float lerp = 0.1f;

        mapRenderer.setView(camera);
        mapRenderer.render();
        shapeRenderer.setProjectionMatrix(camera.combined);

        handleInput();
        gameStage.act(delta);
        gameStage.draw();
        if (currentGameState == GameState.CUTSCENE_START || currentGameState == GameState.PLAYING) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            float glowWidth = 2f;
            for (float i = glowWidth; i > 0; i--) {
                shapeRenderer.setColor(new Color(1f, 0f, 1f, i / glowWidth));
                shapeRenderer.rect(cutsceneRectangle.x - i, cutsceneRectangle.y - i,
                        cutsceneRectangle.width + 2 * i, cutsceneRectangle.height + 2 * i);
            }
            shapeRenderer.end();
        }

        if (!player.isDead())  pieMenuManager.handleInput(Gdx.input.getX(), Gdx.input.getY(), delta);
        if (player.getHealth() <= 150) {
            vfxManager.cleanUpBuffers();
            vfxManager.beginInputCapture();
            mapRenderer.render();
            gameStage.draw();
            vfxManager.endInputCapture();
            vfxManager.applyEffects();
            vfxManager.renderToScreen();
            batch.begin();
            batch.draw(bloodOverlayTexture, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            batch.end();
        }
        uiStage.act(delta);
        uiStage.draw();
        if (currentGameState == GameState.GAME_OVER) {
            gameOverStage.act(delta);
            gameOverStage.draw();
        }
        if (currentGameState == GameState.MISSION_COMPLETE) {
            missionCompleteStage.act(delta);
            missionCompleteStage.draw();
        }
        if (currentGameState == GameState.SETTINGS) {
            settingsStage.act(delta);
            settingsStage.draw();
        }
        carBloodEffect.update(delta);
        batch.begin();
        carBloodEffect.draw(batch, delta);
        drawWeaponIcon();

        batch.end();
        waveManager.checkCarCollisions();
    }

    private void handleInput() {
        if (currentGameState != GameState.PLAYING) return;
        if (Gdx.input.isKeyPressed(Input.Keys.BACK)) triggerSettingsSequence();
        Vector2 direction = joystick.getDirection();
        if (!player.isDead()) player.setVelocity(direction);
        else {
            player.setVelocity(new Vector2(0, 0));
            joystick.setVisible(false);
        }
    }

    private void drawWeaponIcon() {
        if(player.isGun()) {
            Texture currentWeaponTexture = rifleTexture;
            float iconX = 10;
            float iconY = Gdx.graphics.getHeight() - currentWeaponTexture.getHeight() * WEAPON_SCALE - 10;
            batch.draw(currentWeaponTexture, iconX, iconY, currentWeaponTexture.getWidth() * WEAPON_SCALE, currentWeaponTexture.getHeight() * WEAPON_SCALE);
        }
    }

    @Override
    public void resize(int width, int height) {
        if (gameStage != null) gameStage.getViewport().update(width, height, true);
        if (uiStage != null) uiStage.getViewport().update(width, height, true);
        if (gameOverStage != null) gameOverStage.getViewport().update(width, height, true);
        if (missionCompleteStage != null) missionCompleteStage.getViewport().update(width, height, true);
        if (settingsStage != null) settingsStage.getViewport().update(width, height, true);

        if (camera != null) {
            camera.position.set(400, 240, 0);
            camera.update();
        }
    }

    @Override
    public void pause() {
        if (soundPlayer != null) {
            soundPlayer.stop();
        }
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        if (vfxManager != null) vfxManager.dispose();
        if (radialBlurEffect != null) radialBlurEffect.dispose();
        if (bloodOverlayTexture != null) bloodOverlayTexture.dispose();
        if (carBloodEffect != null) carBloodEffect.dispose();
        if (player != null) player.dispose();
        if (batch != null) batch.dispose();
        if (gameStage != null) gameStage.dispose();
        if (uiStage != null) uiStage.dispose();
        if (gameOverStage != null) gameOverStage.dispose();
        if (missionCompleteStage != null) missionCompleteStage.dispose();
        if (settingsStage != null) settingsStage.dispose();
        if (map != null) map.dispose();
        if (mapRenderer != null) mapRenderer.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (debugRenderer != null) debugRenderer.dispose();
        if (rifleTexture != null) rifleTexture.dispose();
        if (world != null) world.dispose();
        if (pieMenuManager != null) pieMenuManager.dispose();
        //         if (musicPlayer != null) musicPlayer.dispose();
        if (soundPlayer != null) {
            soundPlayer.stop();
        }
        if (assetManager != null) assetManager.dispose();
        for (Texture texture : textureCache.values()) texture.dispose();
    }
}
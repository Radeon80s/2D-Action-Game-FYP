package com.sotiris.engine;

import androidx.annotation.NonNull;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.Interpolation;

public class MainMenu extends ApplicationAdapter {

    // Constants - smaller UI elements
    private static final float WORLD_WIDTH = 1600;
    private static final float WORLD_HEIGHT = 900;
    private static final float BUTTON_WIDTH = 300f;
    private static final float BUTTON_HEIGHT = 60f;

    // Core components
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private InputMultiplexer inputMultiplexer;

    // Stages
    private Stage mainStage;
    private Stage settingsStage;
    private Stage creditsStage;

    // UI
    private Skin skin;
    private Texture backgroundTexture;

    // UI state
    private MenuState currentState = MenuState.MAIN;
    private float soundVolume = 1.0f;
    public boolean Male;

    // Callback
    private final Runnable onPlaySelected;

    private enum MenuState {
        MAIN, SETTINGS, CREDITS
    }

    public MainMenu(Runnable onPlaySelected, String gender) {
        this.onPlaySelected = onPlaySelected;
        this.Male = gender == null || gender.contains("Male");
    }

    @Override
    public void create() {
        // Initialize camera
        camera = new OrthographicCamera();
        camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);

        // Initialize batch
        batch = new SpriteBatch();

        // Initialize input
        inputMultiplexer = new InputMultiplexer();
        Gdx.input.setInputProcessor(inputMultiplexer);
        Gdx.input.setCatchKey(Input.Keys.BACK, true);

        // Create stages
        mainStage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera));
        settingsStage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera));
        creditsStage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera));

        // Create background programmatically
        backgroundTexture = createGradientTexture(
                new Color(0.05f, 0.05f, 0.1f, 1f),
                new Color(0.2f, 0.2f, 0.3f, 1f));

        // Create UI skin
        createSkin();

        // Create UI
        createMainMenu();
        createSettingsMenu();
        createCreditsMenu();

        // Start with main menu
        showMainMenu();
    }

    private void createSkin() {
        skin = new Skin();

        // Create a basic font
        BitmapFont font = new BitmapFont();
        font.getData().setScale(1.5f);
        skin.add("default-font", font, BitmapFont.class);

        // Button styles
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = font;
        buttonStyle.fontColor = Color.WHITE;
        buttonStyle.downFontColor = Color.LIGHT_GRAY;

        // Simple button textures
        buttonStyle.up = new TextureRegionDrawable(createSolidColorTexture(1, 1, new Color(0.2f, 0.2f, 0.3f, 0.8f)));
        buttonStyle.down = new TextureRegionDrawable(createSolidColorTexture(1, 1, new Color(0.3f, 0.3f, 0.4f, 0.8f)));
        buttonStyle.over = new TextureRegionDrawable(createSolidColorTexture(1, 1, new Color(0.25f, 0.25f, 0.35f, 0.8f)));

        skin.add("default", buttonStyle);

        // Label styles
        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = font;
        labelStyle.fontColor = Color.WHITE;
        skin.add("default", labelStyle);

        // Title style
        Label.LabelStyle titleStyle = new Label.LabelStyle(labelStyle);
        titleStyle.fontColor = Color.YELLOW;
        skin.add("title", titleStyle);

        // Slider style
        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.knob = new TextureRegionDrawable(createSolidColorTexture(20, 20, Color.WHITE));
        sliderStyle.background = new TextureRegionDrawable(createSolidColorTexture(300, 10, new Color(0.2f, 0.2f, 0.2f, 0.8f)));
        skin.add("default-horizontal", sliderStyle);

        // Select box style
        SelectBox.SelectBoxStyle selectBoxStyle = new SelectBox.SelectBoxStyle();
        selectBoxStyle.font = font;
        selectBoxStyle.fontColor = Color.WHITE;
        selectBoxStyle.background = new TextureRegionDrawable(createSolidColorTexture(1, 1, new Color(0.2f, 0.2f, 0.3f, 0.8f)));
        selectBoxStyle.scrollStyle = new ScrollPane.ScrollPaneStyle();
        selectBoxStyle.listStyle = new List.ListStyle();
        selectBoxStyle.listStyle.font = font;
        selectBoxStyle.listStyle.fontColorSelected = Color.WHITE;
        selectBoxStyle.listStyle.fontColorUnselected = Color.LIGHT_GRAY;
        selectBoxStyle.listStyle.selection = new TextureRegionDrawable(createSolidColorTexture(1, 1, new Color(0.3f, 0.3f, 0.4f, 0.8f)));
        selectBoxStyle.listStyle.background = new TextureRegionDrawable(createSolidColorTexture(1, 1, new Color(0.2f, 0.2f, 0.2f, 0.9f)));
        skin.add("default", selectBoxStyle);

        // Scroll pane style
        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle();
        scrollPaneStyle.background = new TextureRegionDrawable(createSolidColorTexture(1, 1, new Color(0.1f, 0.1f, 0.1f, 0.5f)));
        skin.add("default", scrollPaneStyle);
    }

    private Texture createSolidColorTexture(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private Texture createGradientTexture(Color topColor, Color bottomColor) {
        Pixmap pixmap = new Pixmap(10, 10, Pixmap.Format.RGBA8888);

        for (int y = 0; y < 10; y++) {
            float ratio = (float)y / (float) 10;
            Color blendedColor = new Color(
                    topColor.r * (1-ratio) + bottomColor.r * ratio,
                    topColor.g * (1-ratio) + bottomColor.g * ratio,
                    topColor.b * (1-ratio) + bottomColor.b * ratio,
                    1f
            );
            pixmap.setColor(blendedColor);
            pixmap.drawLine(0, y, 10, y);
        }

        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private void createMainMenu() {
        mainStage.clear();

        Table rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.align(Align.center);

        Label titleLabel = new Label("2D Game Demo", skin, "title");
        titleLabel.setFontScale(2.5f);
        rootTable.add(titleLabel).padBottom(60f).row();

        // Create buttons
        TextButton playButton = new TextButton("PLAY NOW", skin);
        TextButton settingsButton = new TextButton("SETTINGS", skin);
        TextButton creditsButton = new TextButton("CREDITS", skin);
        TextButton exitButton = new TextButton("EXIT", skin);

        // Button listeners
        playButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (onPlaySelected != null) {
                    onPlaySelected.run();
                }
            }
        });

        settingsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showSettingsMenu();
            }
        });

        creditsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showCreditsMenu();
            }
        });

        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });

        // Add buttons to table with spacing
        float buttonSpacing = 20f;
        rootTable.add(playButton).width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padBottom(buttonSpacing).row();
        rootTable.add(settingsButton).width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padBottom(buttonSpacing).row();
        rootTable.add(creditsButton).width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padBottom(buttonSpacing).row();
        rootTable.add(exitButton).width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padBottom(buttonSpacing).row();

        Label versionLabel = new Label("Version 1.0", skin);
        versionLabel.setColor(0.7f, 0.7f, 0.7f, 1f);
        rootTable.add(versionLabel).padTop(40f).row();

        mainStage.addActor(rootTable);
    }

    private void createSettingsMenu() {
        settingsStage.clear();

        Table rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.align(Align.center);

        // Settings title
        Label titleLabel = new Label("SETTINGS", skin, "title");
        titleLabel.setFontScale(2.0f);

        // Sound effects
        Label soundLabel = new Label("Sound Effects", skin);
        Slider soundSlider = new Slider(0f, 1f, 0.1f, false, skin);
        soundSlider.setValue(soundVolume);
        soundSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                soundVolume = soundSlider.getValue();
            }
        });

        // Gender selection
        Label genderLabel = new Label("Character Gender", skin);
        SelectBox<String> genderSelect = getStringSelectBox();

        // Back button
        TextButton backButton = new TextButton("BACK", skin);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showMainMenu();
            }
        });

        // Add elements to table
        rootTable.add(titleLabel).colspan(2).padBottom(50f).row();

        rootTable.add(soundLabel).width(150f).align(Align.left).padRight(20f);
        rootTable.add(soundSlider).width(250f).padBottom(30f).row();

        rootTable.add(genderLabel).width(150f).align(Align.left).padRight(20f);
        rootTable.add(genderSelect).width(250f).padBottom(50f).row();

        rootTable.add(backButton).colspan(2).width(BUTTON_WIDTH).height(BUTTON_HEIGHT).padTop(30f);

        settingsStage.addActor(rootTable);
    }

    @NonNull
    private SelectBox<String> getStringSelectBox() {
        SelectBox<String> genderSelect = new SelectBox<>(skin);
        genderSelect.setItems("Male", "Female");
        genderSelect.setSelected(Male ? "Male" : "Female");
        genderSelect.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String selected = genderSelect.getSelected();
                Male = selected.equals("Male");
            }
        });
        return genderSelect;
    }

    private void createCreditsMenu() {
        creditsStage.clear();

        Table rootTable = new Table();
        rootTable.setFillParent(true);
        rootTable.align(Align.center);

        // Credits title
        Label titleLabel = new Label("CREDITS", skin, "title");
        titleLabel.setFontScale(2.0f);

        String creditsText = "Developed by Sotiris Konstantinou\n\n" +
                "University of Nicosia\n" +
                "Final Year Project 2025-2026\n\n" +
                "Supervisor: Prof. C. Mavromoustakis\n\n" +
                "-- Libraries --\n" +
                "LibGDX - libgdx/libgdx\n" +
                "gdx-sfx - spookygames/gdx-sfx\n" +
                "gdx-vfx - crashinvaders/gdx-vfx\n" +
                "PieMenu - payne911/PieMenu\n\n" +
                "-- Assets --\n" +
                "sscary - The Adventurer (itch.io)\n" +
                "Game Gland - Zombie Apocalypse (itch.io)\n" +
                "Castle Dungeon Tileset (OpenGameArt)\n" +
                "Vary Car Pack (OpenGameArt)";

        Label creditsContentLabel = new Label(creditsText, skin);
        creditsContentLabel.setAlignment(Align.center);

        // Scrollable credits container (even though it's empty)
        ScrollPane scrollPane = new ScrollPane(creditsContentLabel, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setFlickScroll(true);
        scrollPane.setScrollingDisabled(true, false);

        // Back button
        TextButton backButton = new TextButton("BACK", skin);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showMainMenu();
            }
        });

        // Add elements to table
        rootTable.add(titleLabel).padBottom(30f).row();
        rootTable.add(scrollPane).width(600f).height(350f).padBottom(30f).row();
        rootTable.add(backButton).width(BUTTON_WIDTH).height(BUTTON_HEIGHT);

        creditsStage.addActor(rootTable);
    }

    private void showMainMenu() {
        currentState = MenuState.MAIN;
        inputMultiplexer.clear();
        inputMultiplexer.addProcessor(mainStage);

        // Simple fade animation
        mainStage.getRoot().getColor().a = 0;
        mainStage.getRoot().addAction(Actions.fadeIn(0.3f, Interpolation.fade));

        settingsStage.getRoot().addAction(Actions.fadeOut(0.3f, Interpolation.fade));
        creditsStage.getRoot().addAction(Actions.fadeOut(0.3f, Interpolation.fade));
    }

    private void showSettingsMenu() {
        currentState = MenuState.SETTINGS;
        inputMultiplexer.clear();
        inputMultiplexer.addProcessor(settingsStage);

        settingsStage.getRoot().getColor().a = 0;
        settingsStage.getRoot().addAction(Actions.fadeIn(0.3f, Interpolation.fade));

        mainStage.getRoot().addAction(Actions.fadeOut(0.3f, Interpolation.fade));
        creditsStage.getRoot().addAction(Actions.fadeOut(0.3f, Interpolation.fade));
    }

    private void showCreditsMenu() {
        currentState = MenuState.CREDITS;
        inputMultiplexer.clear();
        inputMultiplexer.addProcessor(creditsStage);

        creditsStage.getRoot().getColor().a = 0;
        creditsStage.getRoot().addAction(Actions.fadeIn(0.3f, Interpolation.fade));

        mainStage.getRoot().addAction(Actions.fadeOut(0.3f, Interpolation.fade));
        settingsStage.getRoot().addAction(Actions.fadeOut(0.3f, Interpolation.fade));
    }

    @Override
    public void render() {
        // Handle back key
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            if (currentState == MenuState.SETTINGS || currentState == MenuState.CREDITS) {
                showMainMenu();
            } else if (currentState == MenuState.MAIN) {
                Gdx.app.exit();
            }
        }

        // Clear screen
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.1f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Update stages
        mainStage.act(Gdx.graphics.getDeltaTime());
        settingsStage.act(Gdx.graphics.getDeltaTime());
        creditsStage.act(Gdx.graphics.getDeltaTime());

        // Draw background
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (backgroundTexture != null) {
            // Create a tiled background from our small gradient texture
            float xTiles = WORLD_WIDTH / backgroundTexture.getWidth() + 1;
            float yTiles = WORLD_HEIGHT / backgroundTexture.getHeight() + 1;

            for (int x = 0; x < xTiles; x++) {
                for (int y = 0; y < yTiles; y++) {
                    batch.draw(backgroundTexture,
                            x * backgroundTexture.getWidth(),
                            y * backgroundTexture.getHeight());
                }
            }
        }
        batch.end();

        // Draw active stage
        switch (currentState) {
            case MAIN:
                mainStage.draw();
                break;
            case SETTINGS:
                settingsStage.draw();
                break;
            case CREDITS:
                creditsStage.draw();
                break;
        }
    }

    public InputMultiplexer getInputProcessor() {
        return inputMultiplexer;
    }

    @Override
    public void resize(int width, int height) {
        // Update viewports
        mainStage.getViewport().update(width, height, true);
        settingsStage.getViewport().update(width, height, true);
        creditsStage.getViewport().update(width, height, true);

        // Update camera
        camera.position.set(WORLD_WIDTH/2, WORLD_HEIGHT/2, 0);
        camera.update();
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (mainStage != null) mainStage.dispose();
        if (settingsStage != null) settingsStage.dispose();
        if (creditsStage != null) creditsStage.dispose();
        if (skin != null) skin.dispose();
        if (backgroundTexture != null) backgroundTexture.dispose();
    }
}
package com.sotiris.engine;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import games.spooky.gdx.sfx.android.AndroidAudioDurationResolver;

public class MainActivity extends AndroidApplication {
    private static final String TAG = "MainActivity";
    private GameController gameController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        AndroidAudioDurationResolver.initialize();

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useImmersiveMode = true;

        gameController = new GameController();
        initialize(gameController, config);
        hideSystemUi();

        Log.d(TAG, "MainActivity initialized successfully");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    private void hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {

            getWindow().setDecorFitsSystemWindows(false);

            WindowInsetsController controller =
                    getWindow().getInsetsController();

            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    @Override
    public void onBackPressed() {
        if (gameController != null) {
            gameController.handleBackPress();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameController != null) {
            gameController.dispose();
            gameController = null;
        }
        Log.d(TAG, "MainActivity destroyed");
    }
}

/**
 * GameController manages the game flow and screen transitions.
 * This is the single Game instance that LibGDX initializes.
 */
class GameController extends Game {
    private static final String TAG = "GameController";

    private MainMenuScreen mainMenuScreen;
    private DemoScreen demoScreen;
    private String playerGender = "Male";
    private boolean isDisposed = false;

    @Override
    public void create() {
        try {
            Log.d(TAG, "GameController create() called");
            mainMenuScreen = new MainMenuScreen(this, playerGender);
            setScreen(mainMenuScreen);
            Log.d(TAG, "Main menu screen set successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in GameController.create()", e);
            e.printStackTrace();
            throw e;
        }
    }

    public void startDemo() {
        if (isDisposed) {
            Log.e(TAG, "Attempted to start demo after disposal");
            return;
        }

        try {
            playerGender = mainMenuScreen.getGender();
            Log.d(TAG, "Starting demo with gender: " + playerGender);

            if (demoScreen != null) {
                demoScreen.dispose();
                demoScreen = null;
            }

            demoScreen = new DemoScreen(this, playerGender);
            setScreen(demoScreen);
            Log.d(TAG, "Demo screen set successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error starting demo", e);
            e.printStackTrace();
        }
    }

    public void returnToMainMenu() {
        if (isDisposed) {
            Log.e(TAG, "Attempted to return to menu after disposal");
            return;
        }

        try {
            if (demoScreen != null) {
                playerGender = demoScreen.getGender();
                mainMenuScreen.setGender(playerGender);
                Log.d(TAG, "Updated gender to: " + playerGender);
            }

            setScreen(mainMenuScreen);

            if (demoScreen != null) {
                demoScreen.dispose();
                demoScreen = null;
            }

            Log.d(TAG, "Returned to main menu successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error returning to main menu", e);
            e.printStackTrace();
        }
    }

    public void handleBackPress() {
        if (isDisposed) return;

        Screen currentScreen = getScreen();
        if (currentScreen == demoScreen) {
            returnToMainMenu();
        } else {
            Gdx.app.exit();
        }
    }

    @Override
    public void dispose() {
        if (isDisposed) return;

        isDisposed = true;
        Log.d(TAG, "GameController disposing");

        try {
            if (mainMenuScreen != null) {
                mainMenuScreen.dispose();
                mainMenuScreen = null;
            }
            if (demoScreen != null) {
                demoScreen.dispose();
                demoScreen = null;
            }
            super.dispose();
            Log.d(TAG, "GameController disposed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error during disposal", e);
            e.printStackTrace();
        }
    }
}

/**
 * Screen wrapper for MainMenu
 */
class MainMenuScreen implements Screen {
    private static final String TAG = "MainMenuScreen";

    private MainMenu mainMenu;
    private final GameController controller;
    private boolean isDisposed = false;
    private boolean isInitialized = false;

    public MainMenuScreen(GameController controller, String gender) {
        this.controller = controller;
        try {
            Log.d(TAG, "Creating MainMenu with gender: " + gender);
            this.mainMenu = new MainMenu(controller::startDemo, gender);
            // CRITICAL: MainMenu extends ApplicationAdapter, so we must call create()
            this.mainMenu.create();
            Log.d(TAG, "MainMenuScreen created and initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating MainMenu", e);
            e.printStackTrace();
            throw e;
        }
    }

    public String getGender() {
        return mainMenu != null && mainMenu.Male ? "Male" : "Female";
    }

    public void setGender(String gender) {
        if (mainMenu != null) {
            mainMenu.Male = gender == null || gender.contains("Male");
        }
    }

    @Override
    public void show() {
        if (isDisposed) return;

        try {
            Log.d(TAG, "MainMenuScreen.show() called");

            // Set input processor
            if (mainMenu != null) {
                Gdx.input.setInputProcessor(mainMenu.getInputProcessor());
                Log.d(TAG, "Input processor set for MainMenu");
            }

            isInitialized = true;
            Log.d(TAG, "MainMenuScreen shown successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in MainMenuScreen.show()", e);
            e.printStackTrace();
        }
    }

    @Override
    public void render(float delta) {
        if (isDisposed || !isInitialized || mainMenu == null) return;

        try {
            // Clear screen first
            Gdx.gl.glClearColor(0.05f, 0.05f, 0.1f, 1);
            Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);

            // Render the main menu
            mainMenu.render();
        } catch (Exception e) {
            Log.e(TAG, "Error rendering main menu", e);
            e.printStackTrace();
        }
    }

    @Override
    public void resize(int width, int height) {
        if (isDisposed || mainMenu == null) return;

        try {
            Log.d(TAG, "Resizing MainMenu to: " + width + "x" + height);
            mainMenu.resize(width, height);
        } catch (Exception e) {
            Log.e(TAG, "Error resizing main menu", e);
            e.printStackTrace();
        }
    }

    @Override
    public void pause() {
        Log.d(TAG, "MainMenuScreen paused");
    }

    @Override
    public void resume() {
        Log.d(TAG, "MainMenuScreen resumed");
    }

    @Override
    public void hide() {
        Log.d(TAG, "MainMenuScreen hidden");
    }

    @Override
    public void dispose() {
        if (isDisposed) return;

        isDisposed = true;
        Log.d(TAG, "MainMenuScreen disposing");

        try {
            if (mainMenu != null) {
                mainMenu.dispose();
                mainMenu = null;
            }
            Log.d(TAG, "MainMenuScreen disposed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error disposing main menu", e);
            e.printStackTrace();
        }
    }
}

/**
 * Screen wrapper for Demo
 */
class DemoScreen implements Screen {
    private static final String TAG = "DemoScreen";

    private Demo demo;
    private final GameController controller;
    private boolean isDisposed = false;
    private boolean isCreated = false;

    public DemoScreen(GameController controller, String gender) {
        this.controller = controller;
        try {
            Log.d(TAG, "Creating Demo with gender: " + gender);
            this.demo = new Demo(() -> controller.returnToMainMenu(), gender);
            Log.d(TAG, "DemoScreen created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating Demo", e);
            e.printStackTrace();
            throw e;
        }
    }

    public String getGender() {
        return demo != null && demo.Male ? "Male" : "Female";
    }

    @Override
    public void show() {
        if (isDisposed) return;

        if (!isCreated) {
            try {
                Log.d(TAG, "Initializing Demo");
                demo.create();
                isCreated = true;
                Log.d(TAG, "Demo initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error initializing demo", e);
                e.printStackTrace();
                throw e;
            }
        }

        Log.d(TAG, "DemoScreen shown");
    }

    @Override
    public void render(float delta) {
        if (isDisposed || !isCreated || demo == null) return;

        try {
            demo.render();
        } catch (Exception e) {
            Log.e(TAG, "Error rendering demo", e);
            e.printStackTrace();
        }
    }

    @Override
    public void resize(int width, int height) {
        if (isDisposed || !isCreated || demo == null) return;

        try {
            Log.d(TAG, "Resizing Demo to: " + width + "x" + height);
            demo.resize(width, height);
        } catch (Exception e) {
            Log.e(TAG, "Error resizing demo", e);
            e.printStackTrace();
        }
    }

    @Override
    public void pause() {
        if (isCreated && !isDisposed && demo != null) {
            try {
                demo.pause();
            } catch (Exception e) {
                Log.e(TAG, "Error pausing demo", e);
                e.printStackTrace();
            }
        }
        Log.d(TAG, "DemoScreen paused");
    }

    @Override
    public void resume() {
        if (isCreated && !isDisposed && demo != null) {
            try {
                demo.resume();
            } catch (Exception e) {
                Log.e(TAG, "Error resuming demo", e);
                e.printStackTrace();
            }
        }
        Log.d(TAG, "DemoScreen resumed");
    }

    @Override
    public void hide() {
        Log.d(TAG, "DemoScreen hidden");
    }

    @Override
    public void dispose() {
        if (isDisposed) return;

        isDisposed = true;
        Log.d(TAG, "DemoScreen disposing");

        try {
            if (demo != null && isCreated) {
                demo.dispose();
                demo = null;
            }
            Log.d(TAG, "DemoScreen disposed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error disposing demo", e);
            e.printStackTrace();
        }
    }
}
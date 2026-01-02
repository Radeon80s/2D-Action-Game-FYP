package com.sotiris.engine.ui;

import android.os.SystemClock;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.sotiris.engine.entities.Player;
import com.payne.games.piemenu.PieMenu;

/**
 * PieMenuManager handles the display and interaction of a PieMenu in the game.
 * It detects touch inputs within a specified allowed zone, shows a ProgressBar
 * during a hold duration, and displays the PieMenu upon completion. It also
 * manages weapon selection through the PieMenu.
 */
public class PieMenuManager extends PieMenu.PieMenuCallbacks {

    // UI Stage where PieMenu and ProgressBar are added
    private final Stage uiStage;

    public void disableInput(){
        getInput = false;
    }

    public void enableInput(){
        getInput = true;
    }

    private boolean getInput = true;
    // PieMenu instance
    private final PieMenu pieMenu;

    // ProgressBar to indicate hold duration progress
    private final ProgressBar progressBar;

    // Flags to track PieMenu and touch states
    private boolean isMenuVisible = false;
    private boolean isTouchActive = false;
    private boolean isTouchWithinAllowedZone = false;

    // Rectangle zones for touch input management
    private final Rectangle allowedZone;
    private final Rectangle joystickZone; // Exclusion zone for joystick
    private final Rectangle shootButtonZone; // Exclusion zone for shoot button
    private final Rectangle stealButtonZone; // Exclusion zone for steal button

    // Selected weapon, using the Player.WeaponType enum
    private Player.WeaponType selectedWeapon = Player.WeaponType.NORMAL;

    // Skin used for styling UI elements
    private final Skin skin;

    // Stores the initial touch position in stage coordinates
    private final Vector2 initialHoldPosition = new Vector2();

    // Reference to the Player instance for weapon switching
    private final Player player;

    // Hold duration in seconds for the ProgressBar to fill
    private static final float HOLD_DURATION = 0.3f; // Reduced to 0.3 seconds

    // Pointer index currently assigned to PieMenu activation
    private int activePointer = -1; // -1 indicates no active pointer

    // Sound effects
    private final Sound progressBarOpenedSound;
    private final Sound grantedStateChangeSound;

    /**
     * Constructor for PieMenuManager.
     *
     * @param uiStage          The UI stage where the PieMenu and ProgressBar will be added.
     * @param skin             The Skin used for styling UI elements.
     * @param allowedZone      The zone within which touch inputs can trigger the PieMenu.
     * @param joystickZone     The exclusion zone for the joystick.
     * @param shootButtonZone  The exclusion zone for the shoot button.
     * @param player           The Player instance to interact with for weapon switching.
     */
    public PieMenuManager(Stage uiStage, Skin skin, Rectangle allowedZone, Rectangle joystickZone, Rectangle shootButtonZone,Rectangle stealButtonZone, Player player) {
        this.uiStage = uiStage;
        this.skin = skin;
        this.allowedZone = allowedZone;
        this.joystickZone = joystickZone;
        this.shootButtonZone = shootButtonZone;
        this.stealButtonZone = stealButtonZone;
        this.player = player;

        // Initialize the PieMenu
        this.pieMenu = createPieMenu();

        // Initialize the ProgressBar
        this.progressBar = createProgressBar();

        // Attach PieMenuCallbacks to handle highlighting changes
        this.pieMenu.addListener(this);

        // Load sound effects
        this.progressBarOpenedSound = Gdx.audio.newSound(Gdx.files.internal("sounds/granted_state_change.wav"));
        this.grantedStateChangeSound = Gdx.audio.newSound(Gdx.files.internal("sounds/progress_bar_opened.wav"));

    }

    /**
     * Creates and configures the PieMenu.
     *
     * @return Configured PieMenu instance.
     */
    private PieMenu createPieMenu() {
        // Create a white pixel texture for the PieMenu background
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture tmpTex = new Texture(pixmap);
        pixmap.dispose();
        TextureRegion whitePixel = new TextureRegion(tmpTex);

        // Define PieMenu style
        PieMenu.PieMenuStyle style = new PieMenu.PieMenuStyle();
        style.separatorWidth = 2;
        style.circumferenceWidth = 2;
        style.backgroundColor = new Color(0, 0, 0, 0.1f); // Semi-transparent black
        style.separatorColor = new Color(1, 1, 1, 1); // White separators
        style.downColor = new Color(1, 0, 0, 1); // Red color when pressed
        style.sliceColor = new Color(0.2f, 0.2f, 0.2f, 0.1f); // Dark grey slices

        // Initialize PieMenu with radius 80, starting angle 0, and angular range 90 degrees
        PieMenu pieMenu = new PieMenu(whitePixel, style, 80, 0, 90);
        pieMenu.setInfiniteSelectionRange(true);

        // Add weapons as PieMenu options using Labels
        Label normal = new Label("Normal", skin);
        Label spear = new Label("Spear", skin);
        Label gun = new Label("Gun", skin);
        pieMenu.addActor(normal);
        pieMenu.addActor(spear);
        pieMenu.addActor(gun);

        // Initially hide the PieMenu
        pieMenu.setVisible(false);

        return pieMenu;
    }

    /**
     * Creates and configures the ProgressBar.
     *
     * @return Configured ProgressBar instance.
     */
    private ProgressBar createProgressBar() {
        // Define ProgressBar range and step size
        float min = 0f;
        float max = 1f;
        float stepSize = 0.01f;

        // Retrieve ProgressBarStyle from the Skin using "default-horizontal"
        ProgressBar.ProgressBarStyle style = skin.get("default-horizontal", ProgressBar.ProgressBarStyle.class);

        // Initialize ProgressBar with smaller size for better UI integration
        ProgressBar progressBar = new ProgressBar(min, max, stepSize, false, style);
        progressBar.setSize(80, 8); // Reduced size for subtlety
        progressBar.setValue(0f); // Initialize at 0
        progressBar.setVisible(false); // Initially hidden

        // Optional: Customize ProgressBar appearance further if needed
        progressBar.getStyle().background.setMinHeight(8);
        progressBar.getStyle().knob.setMinHeight(8);

        // Add ProgressBar to the UI stage
        uiStage.addActor(progressBar);

        return progressBar;
    }

    /**
     *
     * @param screenX The x-coordinate of the touch in screen coordinates.
     * @param screenY The y-coordinate of the touch in screen coordinates.
     * @param delta   The time elapsed since the last frame.
     */
    public void handleInput(float screenX, float screenY, float delta) {
        // Iterate through all possible touch pointers

        if(!getInput){
            return;
        }

        int maxPointers = 20; // Adjust based on device capabilities
        for (int pointer = 0; pointer < maxPointers; pointer++) {
            if (Gdx.input.isTouched(pointer)) {
                Vector2 stageCoords = new Vector2(Gdx.input.getX(pointer), Gdx.input.getY(pointer));
                uiStage.screenToStageCoordinates(stageCoords);
                float x = stageCoords.x;
                float y = stageCoords.y;

                // If PieMenuManager is not active and the touch is eligible
                if (!isTouchActive && isEligibleForPieMenu(pointer, x, y)) {
                    // Assign this pointer for PieMenu activation
                    activePointer = pointer;
                    isTouchActive = true;
                    isTouchWithinAllowedZone = true;
                    initialHoldPosition.set(x, y);

                    // Position and show the ProgressBar near the touch point
                    positionProgressBar(x, y);
                    progressBar.setValue(0f); // Reset ProgressBar value
                    progressBar.setVisible(true); // Show ProgressBar
                    progressBarOpenedSound.play(); // Play sound effect
                }
            } else {
                // Touch is not active for this pointer
                if (isTouchActive && activePointer == pointer) {
                    // Touch has been released for the active pointer
                    cancelHoldProcess();
                    hidePieMenu();
                }
            }
        }

        // Update ProgressBar if touch is active and within allowed zone
        if (isTouchActive && isTouchWithinAllowedZone && activePointer != -1 && !isMenuVisible) {
            // Ensure the active pointer is still touched
            if (Gdx.input.isTouched(activePointer)) {
                // Get current touch position
                Vector2 stageCoords = new Vector2(Gdx.input.getX(activePointer), Gdx.input.getY(activePointer));
                uiStage.screenToStageCoordinates(stageCoords);
                float x = stageCoords.x;
                float y = stageCoords.y;

                // Update ProgressBar position in case the touch has moved
                positionProgressBar(x, y);

                // Increment ProgressBar value based on delta time
                float newValue = progressBar.getValue() + (delta / HOLD_DURATION);
                progressBar.setValue(newValue);

                // Check if hold duration is completed
                if (newValue >= progressBar.getMaxValue()) {

                    showPieMenu(initialHoldPosition.x, initialHoldPosition.y);
                    progressBar.setVisible(false); // Hide ProgressBar after PieMenu is shown
                    isMenuVisible = true; // Set menu visibility flag
                    grantedStateChangeSound.play(); // Optional: Play sound effect when PieMenu is shown
                }
            }
        }

        // If PieMenu is visible and the active touch is ongoing, update the highlight
        if (isMenuVisible && isTouchActive && activePointer != -1 && Gdx.input.isTouched(activePointer)) {
            Vector2 stageCoords = new Vector2(Gdx.input.getX(activePointer), Gdx.input.getY(activePointer));
            uiStage.screenToStageCoordinates(stageCoords);
            float x = stageCoords.x;
            float y = stageCoords.y;
            pieMenu.highlightSliceAtStage(x, y);
        }

        // Handle touch release to finalize selection
        if (isMenuVisible && isTouchActive && activePointer != -1 && !Gdx.input.isTouched(activePointer)) {
            hidePieMenu();
            cancelHoldProcess();
        }
    }

    /**
     * Determines if the touch is eligible to trigger the PieMenu.
     *
     * @param pointer The touch pointer index.
     * @param x       The x-coordinate in stage space.
     * @param y       The y-coordinate in stage space.
     * @return True if eligible; false otherwise.
     */
    private boolean isEligibleForPieMenu(int pointer, float x, float y) {
        // Check if touch is within allowed zone and outside exclusion zones
        return allowedZone.contains(x, y) && !joystickZone.contains(x, y) && !shootButtonZone.contains(x, y) && !stealButtonZone.contains(x, y);
    }

    /**
     * Positions the ProgressBar near the touch point, ensuring it remains within stage boundaries.
     *
     * @param x The x-coordinate in stage space.
     * @param y The y-coordinate in stage space.
     */
    private void positionProgressBar(float x, float y) {
        float progressBarWidth = progressBar.getWidth();
        float progressBarHeight = progressBar.getHeight();

        float stageWidth = uiStage.getViewport().getWorldWidth();
        float stageHeight = uiStage.getViewport().getWorldHeight();

        // Calculate position to place the ProgressBar above the touch point
        float posX = x - progressBarWidth / 2;
        float posY = y + 80; // 40 units above the touch point

        // Boundary checks to keep ProgressBar within stage
        if (posX + progressBarWidth > stageWidth) {
            posX = stageWidth - progressBarWidth - 10; // 10 units padding
        } else if (posX < 10) {
            posX = 10; // 10 units padding
        }

        if (posY + progressBarHeight > stageHeight) {
            posY = stageHeight - progressBarHeight - 10; // 10 units padding
        }

        // Set the position of the ProgressBar
        progressBar.setPosition(posX, posY);
    }

    /**
     * Displays the PieMenu at the specified stage coordinates.
     *
     * @param x The x-coordinate in stage space.
     * @param y The y-coordinate in stage space.
     */
    private void showPieMenu(float x, float y) {
        // Prevent showing the PieMenu if it's already visible
        if (isMenuVisible) return;

        /*
        Debounce the menu
         */

        // Determine stage dimensions
        float stageWidth = uiStage.getViewport().getWorldWidth();
        float stageHeight = uiStage.getViewport().getWorldHeight();

        float menuWidth = pieMenu.getWidth();
        float menuHeight = pieMenu.getHeight();

        // Adjust X position to keep PieMenu within stage boundaries
        if (x + menuWidth / 2 > stageWidth) {
            x = stageWidth - menuWidth / 2;
        } else if (x - menuWidth / 2 < 0) {
            x = menuWidth / 2;
        }

        // Adjust Y position to keep PieMenu within stage boundaries
        if (y + menuHeight / 2 > stageHeight) {
            y = stageHeight - menuHeight / 2;
        } else if (y - menuHeight / 2 < 0) {
            y = menuHeight / 2;
        }

        // Position the PieMenu centered at (x, y)
        pieMenu.setPosition(x - menuWidth / 2, y - menuHeight / 2);
        pieMenu.setVisible(true);
        isMenuVisible = true;

        // Add PieMenu to UI stage if not already added
        if (!uiStage.getActors().contains(pieMenu, true)) {
            uiStage.addActor(pieMenu);
        }

        // Play sound effect for PieMenu opening
        progressBarOpenedSound.play();

    }

    /**
     * Hides the PieMenu if it's currently visible.
     */
    private void hidePieMenu() {
        if (isMenuVisible) {
            pieMenu.setVisible(false);
            isMenuVisible = false;
        }
    }

    private static final long DEBOUNCE_INTERVAL = 100; // in milliseconds
    private long lastHighlightChangeTime = 0; // Tracks the last time highlight was processed

    /**
     * Overrides the PieMenuCallbacks to handle real-time highlighting changes.
     * @param highlightedIndex The index of the newly highlighted slice.
     */
    @Override
    public void onHighlightChange(int highlightedIndex) {
        long currentTime = SystemClock.elapsedRealtime();

        // Check if the debounce interval has elapsed
        if (currentTime - lastHighlightChangeTime < DEBOUNCE_INTERVAL) {
            return; // Skip processing if within debounce interval
        }

        lastHighlightChangeTime = currentTime; // Update the timestamp

        if (highlightedIndex != PieMenu.NO_SELECTION) {
            // Determine the weapon based on the highlighted index
            switch (highlightedIndex) {
                case 1:
                    selectedWeapon = Player.WeaponType.SPEAR;
                    break;
                case 2:
                    selectedWeapon = Player.WeaponType.GUN;
                    break;
                default:
                    selectedWeapon = Player.WeaponType.NORMAL;
            }

            Player.WeaponType currentWeapon = player.getCurrentWeapon();

            // Only switch the weapon if it's different from the current one
            if (selectedWeapon != currentWeapon) {
                player.switchWeapon(selectedWeapon);
                grantedStateChangeSound.play(); // Play sound effect for weapon switch
            }
        }
    }


    /**
     * Cancels the hold process by hiding the ProgressBar and resetting flags.
     */
    private void cancelHoldProcess() {
        if (isTouchActive) {
            progressBar.setVisible(false); // Hide ProgressBar
            progressBar.setValue(0f); // Reset ProgressBar value
            isTouchActive = false;
            isTouchWithinAllowedZone = false;
            activePointer = -1;
        }
    }


    /**
     * Returns the currently selected weapon.
     *
     * @return The selected Player.WeaponType.
     */
    public Player.WeaponType getSelectedWeapon() {
        return selectedWeapon;
    }

    /**
     * Cleans up resources by removing the PieMenu and ProgressBar from the stage, disposing of the skin, and sounds.
     */
    public void dispose() {
        pieMenu.remove(); // Remove PieMenu from stage
        progressBar.remove(); // Remove ProgressBar from stage
        if (skin != null) {
            skin.dispose(); // Dispose of Skin resources
        }
        // Dispose of sound resources
        progressBarOpenedSound.dispose();
        grantedStateChangeSound.dispose();
    }
}

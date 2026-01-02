package com.sotiris.engine;

import com.badlogic.gdx.utils.GdxNativesLoader;

/**
 * Helper class to ensure libGDX native libraries are loaded properly
 */
public class GdxInitializer {

    private static boolean initialized = false;
    public static synchronized void init() {
        if (!initialized) {
            try {
                // Load libGDX native libs
                GdxNativesLoader.load();
                initialized = true;
            } catch (Exception e) {
                System.err.println("Warning: Error initializing libGDX natives: " + e.getMessage());
            }
        }
    }
}
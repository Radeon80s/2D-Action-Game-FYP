package com.sotiris.engine.utils;

import games.spooky.gdx.sfx.spatial.SpatializedSound;
import games.spooky.gdx.sfx.spatial.SpatializedSoundPlayer;

public class MySpatializedSoundPlayer<T> extends SpatializedSoundPlayer<T> {
    public void updateSoundPosition(long id, T newPosition) {
        SpatializedSound<T> sound = sounds.get(id);
        if (sound != null) {
            sound.setPosition(newPosition);
        }
    }

}

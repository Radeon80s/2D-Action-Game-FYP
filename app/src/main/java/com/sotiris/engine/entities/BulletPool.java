package com.sotiris.engine.entities;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Pool;
import com.sotiris.engine.utils.CollisionManager;

public class BulletPool extends Pool<Bullet> {
    private final Stage stage;
    private final AssetManager assetManager;
    private final CollisionManager collisionManager;

    public BulletPool(Stage stage, AssetManager assetManager, CollisionManager collisionManager) {
        this.stage = stage;
        this.assetManager = assetManager;
        this.collisionManager = collisionManager;
    }

    @Override
    protected Bullet newObject() {
        // Create a new Bullet object
        return new Bullet(0, 0, new Vector2(), 0, null, collisionManager, assetManager,this);
    }

    public Bullet obtainBullet(float x, float y, Vector2 velocity, int damage, Actor owner) {
        // Obtain a bullet from the pool
        Bullet bullet = obtain();
        bullet.setPosition(new Vector2(x, y));
        bullet.setVelocity(velocity);
        bullet.setOwner(owner);
        bullet.setDamage(damage);
        bullet.setActive(true); // Mark the bullet as active
        stage.addActor(bullet); // Add bullet to the stage so it can be drawn and updated
        return bullet;
    }

    public void freeBullet(Bullet bullet) {
        bullet.remove();
        bullet.setActive(false); // Mark bullet as inactive
        free(bullet); // Return bullet to the pool
    }

    @Override
    protected void reset(Bullet bullet) {
        bullet.setVelocity(new Vector2(0, 0));
        bullet.setPosition(new Vector2(0, 0));
    }
}

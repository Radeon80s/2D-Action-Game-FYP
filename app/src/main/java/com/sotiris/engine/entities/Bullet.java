package com.sotiris.engine.entities;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.sotiris.engine.utils.CollisionManager;

public class Bullet extends Actor {
    private boolean isActive;
    private final Texture bulletTexture;
    private final Vector2 position;
    private Vector2 velocity;
    private int damage;
    private Actor owner;
    private final CollisionManager collisionManager;
    private final BulletPool bulletPool; // Reference to the BulletPool

    public Bullet(float x, float y, Vector2 velocity, int damage, Actor owner, CollisionManager collisionManager, AssetManager assetManager, BulletPool bulletPool) {
        bulletTexture = assetManager.get("bullet.png", Texture.class);
        position = new Vector2(x, y);
        this.velocity = velocity;
        this.damage = damage;
        this.owner = owner;
        this.collisionManager = collisionManager;
        this.bulletPool = bulletPool; // Assign the bullet pool

        setBounds(position.x, position.y, bulletTexture.getWidth(), bulletTexture.getHeight());
        setSize(bulletTexture.getWidth(), bulletTexture.getHeight());
    }

    private float calculateRotationAngle() {
        return velocity.angleDeg() - 90; // Offset by -90 degrees if the texture is oriented upwards
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        position.add(velocity.x * delta, velocity.y * delta);
        setPosition(position.x, position.y);

        // Collision detection
        Rectangle bulletRect = new Rectangle(getX(), getY(), getWidth(), getHeight());

        for (Actor actor : getStage().getActors()) {
            if (actor == owner) continue;

            if (actor instanceof Player && !(owner instanceof Player)) {
                Player player = (Player) actor;
                if (bulletRect.overlaps(player.getBounds())) {
                    Vector2 attackDirection = new Vector2(velocity).nor();
                    player.takeDamage(damage, attackDirection);
                    bulletPool.freeBullet(this); // Free bullet to the pool
                    return;
                }
            } else if (actor instanceof Enemy && owner instanceof Player) {
                Enemy enemy = (Enemy) actor;
                if (bulletRect.overlaps(enemy.getBounds())) {
                    Vector2 attackDirection = new Vector2(velocity).nor();
                    enemy.takeDamage(damage, attackDirection);
                    bulletPool.freeBullet(this); // Free bullet to the pool
                    return;
                }
            }
        }

        // Check collision with static objects
        for (Rectangle rect : collisionManager.getCollisionRectangles()) {
            if (bulletRect.overlaps(rect)) {
                bulletPool.freeBullet(this); // Free bullet to the pool
                return;
            }
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float rotationAngle = calculateRotationAngle();

        batch.draw(
                bulletTexture,
                getX(), getY(),
                getWidth() / 2f, getHeight() / 2f, // Origin of rotation is the center
                getWidth(), getHeight(),
                1f, 1f, // Scaling factors
                rotationAngle, // Rotation angle in degrees
                0, 0,
                bulletTexture.getWidth(), bulletTexture.getHeight(),
                false, false
        );
    }

    @Override
    public boolean remove() {
        setActive(false);
        return super.remove();
    }

    public void setOwner(Actor owner) {
        this.owner = owner;
    }

    public void setDamage(int damage) {
        this.damage = damage;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    public void setVelocity(Vector2 velocity) {
        this.velocity = velocity;
    }

    public void setPosition(Vector2 add) {
        position.set(add);
    }
}

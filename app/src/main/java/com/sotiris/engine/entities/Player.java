package com.sotiris.engine.entities;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;
import com.sotiris.engine.utils.CollisionManager;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class Player extends Actor {
    public void reset() {
        health = INITIAL_HEALTH;
        isDead = false;
        isShooting = false;
        isSpearAttack = false;
        isMoving = false;
        deathAnimationComplete = false;
        spearHasHit = false;

        // Reset player position and velocity
        //   body.setTransform(body.getPosition(), body.getAngle()); // Keeps the current position but resets the physics
        velocity.setZero();
        lastNonZeroDirection.set(1, 0);  // Reset to facing right

        // Reset current weapon and state
        currentWeapon = WeaponType.NORMAL;
        currentState = PlayerState.IDLE;
        stateTime = 0f;
        currentDirection = 0;

        // Clear blood effects
        for (ParticleEffectPool.PooledEffect effect : activeBloodEffects) {
            effect.free();
        }
        activeBloodEffects.clear();

        // Clear animation progress
        previousFrameIndex = -1;
        timeSinceLastShot = 0f;

        // Resume player movement (if it was suspended)
        suspendPlayer = false;
    }
    private static final float MAX_SPEED = 200f;
    private static final float FRAME_DURATION = 0.13f;
    private static final int INITIAL_HEALTH = 1000;//500;
    private static final float BODY_RADIUS = 38f;
    private static final int SPEAR_DAMAGE = 100;
    private static final float BULLET_SPEED = 1000f;
    private static final int BULLET_DAMAGE = 20;

    public boolean isUnarmed() {
        return currentWeapon.equals(WeaponType.NORMAL);
    }

    public enum PlayerState {IDLE, WALK, RUN, SHOOTING, ATTACK_SPEAR, DEATH}

    public enum WeaponType {GUN, SPEAR, NORMAL}

    private final ShapeRenderer shapeRenderer;
    private final World world;
    private final Body body;
    private final Vector2 velocity;
    private final Vector2 aimDirection = new Vector2();
    private final Vector2 lastNonZeroDirection = new Vector2(1, 0); // Default to facing right
    private static final float[] DIRECTION_ANGLES_8 = {270f, 225f, 135f, 90f, 45f, 315f, 180f, 0f};


    private static final float SHOOT_COOLDOWN = 0.1f; // 0.1 seconds between shots
    private float timeSinceLastShot = 0f;
    private final CollisionManager collisionManager;
    private WeaponType currentWeapon;
    private PlayerState currentState;
    private int currentDirection;
    private float stateTime;
    private int health;
    private boolean isDead;
    private boolean isShooting;
    private boolean isSpearAttack;
    private boolean isMoving;
    private boolean deathAnimationComplete;
    private final Map<WeaponType, Map<PlayerState, Animation<TextureRegion>[]>> animations;
    private Animation<TextureRegion> shadowAnimation;
    private final Map<WeaponType, Animation<TextureRegion>> shadowDeathAnimations;
    private int previousFrameIndex = -1;
    private static final float MIN_TIME_BETWEEN_SHOTS = 0.1f; // Adjust as needed
    private final Array<ParticleEffectPool.PooledEffect> activeBloodEffects;  // Use PooledEffect instead of ParticleEffect
    private final ParticleEffectPool bloodEffectPool;
    private final AssetManager assetManager;
    private final boolean male;
    private final BulletPool bulletPool;

    public  Player(float x, float y, World world, CollisionManager collisionManager, AssetManager assetManager, boolean male, BulletPool bulletPool) {
        this.assetManager = assetManager;
        this.bulletPool = bulletPool;
        this.shapeRenderer = new ShapeRenderer();
        this.world = world;
        this.collisionManager = collisionManager;
        this.velocity = new Vector2();
        this.currentWeapon = WeaponType.NORMAL;
        this.currentState = PlayerState.IDLE;
        this.currentDirection = 0;
        this.stateTime = 0f;
        this.health = INITIAL_HEALTH;
        this.isDead = false;
        this.isShooting = false;
        this.isMoving = false;
        this.deathAnimationComplete = false;
        this.male = male;
        // Create the physics body
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x, y);
        this.body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(BODY_RADIUS);
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        body.createFixture(fixtureDef);
        shape.dispose();

        // Load animations
        this.animations = new EnumMap<>(WeaponType.class);
        this.shadowDeathAnimations = new EnumMap<>(WeaponType.class);
        loadAnimations();
        loadShadows();

        // Load blood effect
        // bloodEffect = new ParticleEffect();
        //bloodEffect.load(Gdx.files.internal("blood.p"), Gdx.files.internal(""));

        ParticleEffect bloodEffectTemplate = new ParticleEffect();
        bloodEffectTemplate.load(Gdx.files.internal("blood.p"), Gdx.files.internal(""));
        bloodEffectPool = new ParticleEffectPool(bloodEffectTemplate, 5, 6);
        activeBloodEffects = new Array<>();

        setSize(BODY_RADIUS * 2, BODY_RADIUS * 2);
    }

    private void loadAnimations() {
        if(male) {
            for (WeaponType weapon : WeaponType.values()) {
                Map<PlayerState, Animation<TextureRegion>[]> weaponAnimations = new EnumMap<>(PlayerState.class);
                animations.put(weapon, weaponAnimations);

                loadAnimation(weapon, PlayerState.IDLE,
                        weapon == WeaponType.GUN ? "Idle_Gun.png" :
                                (weapon == WeaponType.SPEAR ? "idle_spear.png" : "idle.png"),
                        weapon == WeaponType.NORMAL ? 8 : 6);

                String walkAnimationFileName;
                int walkFrameCount;
                if (weapon == WeaponType.GUN) {
                    walkAnimationFileName = "Walk_Gun.png";
                    walkFrameCount = 6;
                } else if (weapon == WeaponType.SPEAR) {
                    walkAnimationFileName = "walk_spear.png";
                    walkFrameCount = 6;
                } else {
                    walkAnimationFileName = "walk.png";
                    walkFrameCount = 8;
                }
                loadAnimation(weapon, PlayerState.WALK, walkAnimationFileName, walkFrameCount);

                loadAnimation(weapon, PlayerState.RUN,
                        weapon == WeaponType.GUN ? "Run_Gun.png" :
                                (weapon == WeaponType.SPEAR ? "run_spear.png" : "run.png"),
                        weapon == WeaponType.GUN ? 8 : 6);

                loadAnimation(weapon, PlayerState.SHOOTING, "Shooting.png", 8);
                loadAnimation(weapon, PlayerState.ATTACK_SPEAR, "attack_spear.png", 8);

                loadAnimation(weapon, PlayerState.DEATH,
                        weapon == WeaponType.GUN ? "death_Gun.png" :
                                (weapon == WeaponType.SPEAR ? "death_spear.png" : "death_normal.png"), 6);

            }
        } else
            for (WeaponType weapon : WeaponType.values()) {
                Map<PlayerState, Animation<TextureRegion>[]> weaponAnimations = new EnumMap<>(PlayerState.class);
                animations.put(weapon, weaponAnimations);

                loadAnimation(weapon, PlayerState.IDLE,
                        weapon == WeaponType.GUN ? "Idle_Gun.png" :
                                (weapon == WeaponType.SPEAR ? "idle_spear.png" : "idle.png"),
                        6);

                String walkAnimationFileName;
                int walkFrameCount;
                if (weapon == WeaponType.GUN) {
                    walkAnimationFileName = "Walk_Gun.png";
                    walkFrameCount = 6;
                } else if (weapon == WeaponType.SPEAR) {
                    walkAnimationFileName = "walk_spear.png";
                    walkFrameCount = 6;
                } else {
                    walkAnimationFileName = "walk.png";
                    walkFrameCount = 6;
                }
                loadAnimation(weapon, PlayerState.WALK, walkAnimationFileName, walkFrameCount);

                loadAnimation(weapon, PlayerState.RUN,
                        weapon == WeaponType.GUN ? "Run_Gun.png" :
                                (weapon == WeaponType.SPEAR ? "run_spear.png" : "run.png"),
                        weapon == WeaponType.GUN ? 8 : 6);

                loadAnimation(weapon, PlayerState.SHOOTING, "Shooting.png", 8);
                loadAnimation(weapon, PlayerState.ATTACK_SPEAR, "attack_spear.png", 8);

                loadAnimation(weapon, PlayerState.DEATH,
                        weapon == WeaponType.GUN ? "death_Gun.png" :
                                (weapon == WeaponType.SPEAR ? "death_spear.png" : "death_normal.png"), 6);

            }
    }

    private void loadAnimation(WeaponType weapon, PlayerState state, String filename, int directions) {
        Texture sheet;
        if(male) {
            sheet = assetManager.get("male/"+filename, Texture.class);
        } else {
            sheet = assetManager.get("female/"+filename, Texture.class);
        }

        int frameWidth = sheet.getWidth() / 8;
        int frameHeight = sheet.getHeight() / directions;
        TextureRegion[][] tmp = TextureRegion.split(sheet, frameWidth, frameHeight);
        Animation<TextureRegion>[] directionAnimations = new Animation[directions];
        for (int dir = 0; dir < directions; dir++) {
            Array<TextureRegion> frames = new Array<>(tmp[dir]);
            directionAnimations[dir] = new Animation<>(FRAME_DURATION, frames);
        }
        Objects.requireNonNull(animations.get(weapon)).put(state, directionAnimations);
    }

    private void loadShadows() {
        Texture shadowTexture = assetManager.get("Shadow.png", Texture.class);
        shadowAnimation = new Animation<>(FRAME_DURATION, new TextureRegion(shadowTexture));

        Texture shadowGunTexture = new Texture(Gdx.files.internal("male/death_Gun_shadow.png"));
        Animation<TextureRegion> deathGunShadow = new Animation<>(FRAME_DURATION, new TextureRegion(shadowGunTexture));

        Texture shadowSpearTexture = new Texture(Gdx.files.internal("male/death_Spear_shadow.png"));
        Animation<TextureRegion> deathSpearShadow = new Animation<>(FRAME_DURATION, new TextureRegion(shadowSpearTexture));

        Texture shadowNormalTexture = new Texture(Gdx.files.internal("male/death_normal_shadow.png"));
        Animation<TextureRegion> deathNormalShadow = new Animation<>(FRAME_DURATION, new TextureRegion(shadowNormalTexture));

        // Store shadow death animations per weapon type
        shadowDeathAnimations.put(WeaponType.GUN, deathGunShadow);
        shadowDeathAnimations.put(WeaponType.SPEAR, deathSpearShadow);
        shadowDeathAnimations.put(WeaponType.NORMAL, deathNormalShadow);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        stateTime += delta;

        // Update direction even if movement is suspended
        updateDirection();

        if (suspendPlayer) {
            return;
        }

        updateState(delta);
        updatePosition(delta);


        if (health <= 0 && !isDead) {
            die();
        }

        if (isShooting) {
            handleShootingAnimation(delta);
        }

        if (isSpearAttack) {
            checkSpearAttackCollision();
        }


        // Remove completed blood effects
        for (int i = activeBloodEffects.size - 1; i >= 0; i--) {
            ParticleEffectPool.PooledEffect bloodEffect = activeBloodEffects.get(i);
            bloodEffect.setPosition(getPosition().x,getPosition().y);
            bloodEffect.update(delta);

            // If the effect is complete, free it back to the pool
            if (bloodEffect.isComplete()) {
                bloodEffect.free();
                activeBloodEffects.removeIndex(i);
            }
        }
    }

    private void updateState(float delta) {
        if (isDead) {
            currentState = PlayerState.DEATH;
            return;
        }

        if (isShooting) {
            currentState = PlayerState.SHOOTING;
            velocity.setZero();
            isMoving = false;
        } else if (isSpearAttack) {
            currentState = PlayerState.ATTACK_SPEAR;
            velocity.setZero();
            isMoving = false;
        } else if (!isMoving) {
            currentState = PlayerState.IDLE;
        } else if (velocity.len() > 0.85) {
            currentState = PlayerState.RUN;
        } else {
            currentState = PlayerState.WALK;
        }
    }

    private void updateDirection() {
        Vector2 directionVector;

        if (isShooting || isSpearAttack) {
            if (aimDirection.isZero()) {
                directionVector = lastNonZeroDirection;
            } else {
                directionVector = aimDirection;
                lastNonZeroDirection.set(aimDirection);
            }
        } else {
            if (velocity.isZero()) {
                directionVector = lastNonZeroDirection;
            } else {
                directionVector = velocity;
                lastNonZeroDirection.set(velocity).nor();
            }
        }

        if (directionVector.isZero()) return;

        float angle = (directionVector.angleDeg() + 360) % 360;

        int directions = Objects.requireNonNull(Objects.requireNonNull(animations.get(currentWeapon)).get(currentState)).length;

        if (directions == 8) {
            if (angle >= 337.5 || angle < 22.5) {
                currentDirection = 7; // Right
            } else if (angle >= 22.5 && angle < 67.5) {
                currentDirection = 4; // Right Up
            } else if (angle >= 67.5 && angle < 112.5) {
                currentDirection = 3; // Up
            } else if (angle >= 112.5 && angle < 157.5) {
                currentDirection = 2; // Left Up
            } else if (angle >= 157.5 && angle < 202.5) {
                currentDirection = 6; // Left
            } else if (angle >= 202.5 && angle < 247.5) {
                currentDirection = 1; // Left Down
            } else if (angle >= 247.5 && angle < 292.5) {
                currentDirection = 0; // Down
            } else if (angle >= 292.5 && angle < 337.5) {
                currentDirection = 5; // Right Down
            }
        } else if (directions == 6) {
            if (angle >= 337.5 || angle < 22.5) {
                currentDirection = 5;
            } else if (angle >= 22.5 && angle < 67.5) {
                currentDirection = 4;
            } else if (angle >= 67.5 && angle < 112.5) {
                currentDirection = 3;
            } else if (angle >= 112.5 && angle < 157.5) {
                currentDirection = 2;
            } else if (angle >= 157.5 && angle < 202.5) {
                currentDirection = 1;
            } else if (angle >= 202.5 && angle < 247.5) {
                currentDirection = 1;
            } else if (angle >= 247.5 && angle < 292.5) {
                currentDirection = 0;
            } else if (angle >= 292.5 && angle < 337.5) {
                currentDirection = 5;
            }
        }
    }

    public void setFacingDirection(Vector2 direction) {
        if (direction.isZero()) return;
        lastNonZeroDirection.set(direction.nor());
        updateDirection();
    }
    public Vector2 getFacingDirection(){
        return lastNonZeroDirection;
    }
    private void updatePosition(float delta) {
        if (isMoving) {
            Vector2 currentPosition = body.getPosition().cpy();
            Vector2 movement = new Vector2(velocity).scl(MAX_SPEED * delta);
            Vector2 newPosition = currentPosition.add(movement);

            if (!isColliding(newPosition)) {
                body.setTransform(newPosition, body.getAngle());
                setPosition(body.getPosition().x - getWidth() / 2, body.getPosition().y - getHeight() / 2);
            } else {
                resolveCollision(newPosition);
            }
        }
    }

    private boolean isColliding(Vector2 newPosition) {
        for (Rectangle rect : collisionManager.getCollisionRectangles()) {
            if (rect.contains(newPosition)) {
                return true;
            }
        }
        return false;
    }

    private void resolveCollision(Vector2 newPosition) {
        for (Rectangle rect : collisionManager.getCollisionRectangles()) {
            if (rect.contains(newPosition)) {
                Vector2 correction = new Vector2();
                float overlapX = Math.min(newPosition.x - rect.x, rect.x + rect.width - newPosition.x);
                float overlapY = Math.min(newPosition.y - rect.y, rect.y + rect.height - newPosition.y);
                if (overlapX < overlapY) {
                    correction.x = (newPosition.x < rect.x + rect.width / 2) ? -overlapX : overlapX;
                } else {
                    correction.y = (newPosition.y < rect.y + rect.height / 2) ? -overlapY : overlapY;
                }
                newPosition.add(correction);
                body.setTransform(newPosition, body.getAngle());
                setPosition(newPosition.x - getWidth() / 2, newPosition.y - getHeight() / 2);
            }
        }
    }

    private boolean suspendPlayer = false;

    private void suspendMovement() {
        suspendPlayer = true;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Map<PlayerState, Animation<TextureRegion>[]> weaponAnimations = animations.get(currentWeapon);
        Animation<TextureRegion>[] stateAnimations = weaponAnimations.get(currentState);

        int directionIndex = Math.min(currentDirection, stateAnimations.length - 1);

        if (isDead) {
            suspendMovement();
            Animation<TextureRegion> deathAnimation =
                    Objects.requireNonNull
                            (weaponAnimations.get(PlayerState.DEATH))[directionIndex];
            TextureRegion currentFrame = deathAnimation.getKeyFrame(stateTime, false);

            if (!deathAnimationComplete) {
                batch.draw(currentFrame, getX(), getY(), getWidth(), getHeight());

                // Render the corresponding shadow death animation
                Animation<TextureRegion> shadowDeath = shadowDeathAnimations.get(currentWeapon);
                TextureRegion shadowFrame = shadowDeath.getKeyFrame(stateTime, false);
                batch.draw(shadowFrame, getX(), getY(), getWidth(), getHeight());


                if (deathAnimation.isAnimationFinished(stateTime)) {
                    deathAnimationComplete = true;
                }
            } else {
                TextureRegion lastFrame = deathAnimation.getKeyFrames()[deathAnimation.getKeyFrames().length - 1];
                batch.draw(lastFrame, getX(), getY(), getWidth(), getHeight());
            }
            return;
        }

        TextureRegion currentFrame;

        if (isDead && stateAnimations[directionIndex].isAnimationFinished(stateTime)) {
            return;
        }

        currentFrame = stateAnimations[directionIndex].getKeyFrame(stateTime, currentState != PlayerState.DEATH);

        batch.draw(currentFrame, getX(), getY(), getWidth(), getHeight());

        if (!isDead) {
            TextureRegion shadowFrame = shadowAnimation.getKeyFrame(stateTime, false);
            batch.draw(shadowFrame, getX(), getY(), getWidth(), getHeight());
        }
        drawHealthBar(batch);
        for (ParticleEffectPool.PooledEffect bloodEffect : activeBloodEffects) {
            if (!bloodEffect.isComplete()) {
                bloodEffect.draw(batch);
            }
        }
    }

    public void setVelocity(Vector2 newVelocity) {
        if (isShooting || isSpearAttack) {
            if (!newVelocity.isZero()) {
                this.aimDirection.set(newVelocity).nor();
                lastNonZeroDirection.set(aimDirection);
            }
        } else {
            this.velocity.set(newVelocity).clamp(0, MAX_SPEED);
            this.isMoving = this.velocity.len() > 0;
            if (isMoving) {
                lastNonZeroDirection.set(velocity).nor();
            }
        }
    }

    private void drawHealthBar(Batch batch) {
        batch.end();
        float healthBarWidth = getWidth() * 0.90f;
        float healthBarHeight = 6;
        float healthPercentage = (float) health / INITIAL_HEALTH;
        float currentHealthBarWidth = healthBarWidth * healthPercentage;

        float healthBarX = getX() + (getWidth() * 0.05f);
        float healthBarY = getY() + getHeight() - 4;

        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());

        // Draw the background
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(new Color(0, 0, 0, 0.2f));
        shapeRenderer.rect(healthBarX, healthBarY, healthBarWidth, healthBarHeight);
        shapeRenderer.end();

        // Draw the health bar
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(new Color(0.35f, 0.4f, 0.7f, 0.9f));
        shapeRenderer.rect(healthBarX, healthBarY, currentHealthBarWidth, healthBarHeight);
        shapeRenderer.end();

        // Draw the outline
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.BLACK);
        shapeRenderer.rect(healthBarX, healthBarY, healthBarWidth, healthBarHeight);
        shapeRenderer.end();
        batch.begin();
    }

    public void shoot() {
        if (isDead || isSpearAttack) return;

        if (this.currentWeapon.equals(WeaponType.GUN)) {
            if (!isShooting) {
                isShooting = true;
                stateTime = 0f;
                previousFrameIndex = -1;
            }


        } else if (this.currentWeapon.equals(WeaponType.SPEAR)) {
            isSpearAttack = true;
            stateTime = 0f;

            // Spear attack logic
            Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    stopSpearAttack();
                }
            }, FRAME_DURATION * 8);
        }
    }

    private void handleShootingAnimation(float delta) {
        timeSinceLastShot += delta;

        Animation<TextureRegion> shootingAnimation = Objects.requireNonNull(
                Objects.requireNonNull(animations.get(currentWeapon)).get(PlayerState.SHOOTING))[currentDirection];
        float frameDuration = shootingAnimation.getFrameDuration();
        int frameIndex = (int) (stateTime / frameDuration);

        // Fire a bullet each time a new frame is reached
        if (frameIndex != previousFrameIndex && timeSinceLastShot >= MIN_TIME_BETWEEN_SHOTS) {
            spawnBullet();
            timeSinceLastShot = 0f;
        }

        previousFrameIndex = frameIndex;

        if (shootingAnimation.isAnimationFinished(stateTime)) {
            isShooting = false;
            stateTime = 0f;
        }
    }

    private void spawnBullet() {
        Vector2 bulletDirection = new Vector2(aimDirection);
        if (bulletDirection.isZero()) {
            bulletDirection.set(lastNonZeroDirection);
            if (bulletDirection.isZero()) {
                float angle = DIRECTION_ANGLES_8[currentDirection];
                bulletDirection.set(1, 0).setAngleDeg(angle);
            }
        }
        bulletDirection.nor();

        Vector2 bulletStartPosition = body.getPosition().cpy().add(bulletDirection.scl(15f)); // Base muzzle offset

        // offset based on the value of currentDirection
        switch (currentDirection) {
            case 7: // Right
                bulletStartPosition.add(6f, -3f);
                break;
            case 4: // Right Up
                bulletStartPosition.add(16f, -2f);
                break;
            case 3: // Up
                bulletStartPosition.add(3f, 6f);
                break;
            case 2: // Left Up
                bulletStartPosition.add(-16f, -2f);
                break;
            case 6: // Left
                bulletStartPosition.add(-6f, -3f);
                break;
            case 1: // Left Down
                bulletStartPosition.add(-9f, -3f);
                break;
            case 0: // Down
                bulletStartPosition.add(-5f, -6f);
                break;
            case 5: // Right Down
                bulletStartPosition.add(5f, -6f);
                break;
            default:
                break;
        }
        Vector2 bulletVelocity = bulletDirection.cpy().nor().scl(BULLET_SPEED);
        // Create and add the bullet
        Bullet bullet = bulletPool.obtainBullet(
                bulletStartPosition.x,
                bulletStartPosition.y,
                bulletVelocity,
                BULLET_DAMAGE,
                this
        );
        getStage().addActor(bullet);
    }

    public void stopShooting() {
        isShooting = false;
    }

    public void stopSpearAttack() {
        isSpearAttack = false;
        spearHasHit = false; // Reset the hit flag
    }


    public void switchWeapon(WeaponType selectedWeapon) {
        if (selectedWeapon != null && selectedWeapon != currentWeapon) {
            this.currentWeapon = selectedWeapon;
            this.currentState = PlayerState.IDLE;
            this.stateTime = 0;
        }
    }

    private void setBloodDirection(ParticleEffectPool.PooledEffect effect, Vector2 attackDirection) {
        attackDirection.nor();  // Normalize the direction vector
        for (ParticleEmitter emitter : effect.getEmitters()) {
            emitter.getAngle().setHigh(attackDirection.angleDeg());  // Set the direction of the blood splatter
            emitter.getAngle().setLow(attackDirection.angleDeg());
        }
    }

    private void enhanceBloodEffect(ParticleEffectPool.PooledEffect effect) {
        Array<ParticleEmitter> emitters = new Array<>(effect.getEmitters());
        for (ParticleEmitter emitter : emitters) {
            // Randomize between Splash and Blurp-like effects
            boolean isSplash = MathUtils.randomBoolean(0.7f);  // 70% chance for splash

            // Emission settings (based on Splash or Blurp)
            if (isSplash) {
                emitter.getEmission().setHigh(50f, 90f);
                emitter.getEmission().setLow(25f, 50f);
            } else {
                emitter.getEmission().setHigh(50f, 100f);
                emitter.getEmission().setLow(0f);
            }

            // Life settings
            emitter.getLife().setHigh(200f, 250f); // High values for longer lifetime
            emitter.getLife().setLow(50f, 150f);   // Low values for shorter lifetime

            // Spawn shape settings (ellipse for Splash, square for Blurp)
            if (isSplash) {
                emitter.getSpawnWidth().setHigh(3f, 13f);
                emitter.getSpawnHeight().setHigh(3f, 13f);
            } else {
                emitter.getSpawnWidth().setHigh(1f, 1f); // Fixed small size for Blurp
                emitter.getSpawnHeight().setHigh(1f, 1f);
            }

            // Scale settings
            if (isSplash) {
                emitter.getXScale().setHigh(1.5f, 2.5f);  // Bigger scale for splash
                emitter.getYScale().setHigh(2f, 3.5f);    // Taller splash
            } else {
                emitter.getXScale().setHigh(1.5f, 1.5f);  // Smaller for pooling effect
                emitter.getYScale().setHigh(2f, 2f);
            }

            // Velocity settings (faster for Splash, slower for Blurp)
            if (isSplash) {
                emitter.getVelocity().setHigh(12f, 35f);
            } else {
                emitter.getVelocity().setHigh(15f, 20f);
                emitter.getVelocity().setLow(0f);  // Minimal movement for pooling
            }

            // Angle settings
            if (isSplash) {
                emitter.getAngle().setHigh(31f, 180f);  // Random angles for splash spread
            } else {
                emitter.getAngle().setHigh(91f, 93f);  // Vertical drop for pooling
            }

            // Rotation settings
            if (isSplash) {
                emitter.getRotation().setHigh(0f, 130f);  // Add rotation for dynamic splash
            } else {
                emitter.getRotation().setActive(false);  // No rotation for pooling
            }

            // Gravity settings (stronger gravity for Splash)
            if (isSplash) {
                emitter.getGravity().setHigh(-50f, -150f);  // Strong gravity for splash fall
            } else {
                emitter.getGravity().setHigh(-50f, -75f);   // Weaker gravity for pooling
            }

            // STRICT RED Tint settings (only shades of red, no green or blue)
            float rStart = MathUtils.random(0.8f, 1f);   // Bright red at start (full red spectrum)
            float rEnd = MathUtils.random(0.4f, 0.7f);   // Darker red at the end (still red)

            // Green and Blue are locked at zero to prevent any other color
            emitter.getTint().setColors(new float[]{
                    rStart, 0f, 0f, 1f,    // Starting color: bright red (R, G, B, Alpha)
                    rEnd, 0f, 0f, 0.7f     // Ending color: darker red (R, G, B, Alpha with transparency)
            });
            emitter.getTint().setTimeline(new float[]{0f, 1f});  // Full timeline for color transition

            // Transparency settings
            emitter.getTransparency().setHigh(1f);  // Fully opaque at start
            emitter.getTransparency().setLow(0.7f);  // Slight transparency towards the end

            // Wind settings (no wind for pooling, some wind for Splash)
            if (isSplash) {
                emitter.getWind().setActive(true);
                emitter.getWind().setHigh(MathUtils.random(-20f, 20f));  // Random wind effect
                emitter.getWind().setLow(MathUtils.random(-10f, 10f));
            } else {
                emitter.getWind().setActive(false);  // No wind for pooling
            }

            // Particle count (higher for Splash, lower for Blurp)
            if (isSplash) {
                emitter.setMaxParticleCount(MathUtils.random(25, 100));
            } else {
                emitter.setMaxParticleCount(MathUtils.random(5, 20));  // Less for pooling
            }

            // Misc options
            emitter.setAdditive(false);
            emitter.setBehind(true);
            emitter.setAligned(isSplash);  // Splash particles are aligned
            emitter.setPremultipliedAlpha(false);
            emitter.setSpriteMode(ParticleEmitter.SpriteMode.random);
        }

        // Set position of the effect (based on player position)
        effect.setPosition(this.getPosition().x, this.getPosition().y);

        // Add slight random offset for more natural appearance
        float offsetX = MathUtils.random(-5f, 5f);
        float offsetY = MathUtils.random(-5f, 5f);
        effect.setPosition(
                this.getPosition().x + offsetX,
                this.getPosition().y + offsetY
        );

        // Scale the entire effect randomly for variety
        effect.scaleEffect(MathUtils.random(0.8f, 1.2f));
    }


    public void takeDamage(int damage, Vector2 attackDirection) {
        if (!isDead) {
            health -= damage;
            // Trigger blood effect at player's position
            ParticleEffectPool.PooledEffect newBloodEffect = bloodEffectPool.obtain();
            setBloodDirection(newBloodEffect, attackDirection);  // Set blood direction based on attack direction
            enhanceBloodEffect(newBloodEffect);
            //modifyBlood(newBloodEffect);
            // Set position and start the effect
            newBloodEffect.setPosition(getX() + getWidth() / 2, getY() + getHeight() / 2);
            newBloodEffect.start();

            // Add the effect to active blood effects
            activeBloodEffects.add(newBloodEffect);

            if (health <= 0) {
                die();
            }
        }
    }


    private void die() {
        isDead = true;
        currentState = PlayerState.DEATH;
        stateTime = 0;
    }

    public boolean isDead() {
        return isDead;
    }

    public int getHealth() {
        return health;
    }

    public Vector2 getPosition() {
        return body.getPosition();
    }

    public Rectangle getBounds() {
        return new Rectangle(getPosition().x - getWidth() / 2,
                getPosition().y - getHeight() / 2, getWidth(), getHeight());
    }

    public Body getBody() {
        return body;
    }

    public void dispose() {

        for (Map<PlayerState, Animation<TextureRegion>[]> weaponAnimations : animations.values()) {
            for (Animation<TextureRegion>[] stateAnimations : weaponAnimations.values()) {
                for (Animation<TextureRegion> animation : stateAnimations) {
                    for (TextureRegion frame : animation.getKeyFrames()) {
                        frame.getTexture().dispose();
                    }
                }
            }
        }

        for (TextureRegion frame : shadowAnimation.getKeyFrames()) {
            frame.getTexture().dispose();
        }

        if (body != null) {
            world.destroyBody(body);
        }
        for (ParticleEffectPool.PooledEffect effect : activeBloodEffects) {
            effect.free();
        }
        bloodEffectPool.clear();

        shapeRenderer.dispose();
    }

    public WeaponType getCurrentWeapon() {
        return currentWeapon;
    }

    public boolean isGun() {
        return currentWeapon == WeaponType.GUN;
    }

    // Spear attack collision detection
    private boolean spearHasHit = false;

    private void checkSpearAttackCollision() {
        if (spearHasHit) return;

        Rectangle attackBounds = getAttackBounds();
        for (Actor actor : getStage().getActors()) {
            if (actor instanceof Enemy) {
                Enemy enemy = (Enemy) actor;
                if (attackBounds.overlaps(enemy.getBounds())) {
                    Vector2 attackDirection = new Vector2(velocity).nor();
                    enemy.takeDamage(SPEAR_DAMAGE,attackDirection);
                    spearHasHit = true;
                    break; // Only hit one enemy per attack
                }
            }
        }
    }

    private Rectangle getAttackBounds() {
        float attackWidth = getWidth();
        float attackHeight = getHeight();
        float attackX = getX();
        float attackY = getY();
        float attackRange = 10f; // Adjust as needed

        switch (currentDirection) {
            case 0: // Down
                attackY -= attackRange;
                break;
            case 1: // Left Down
                attackX -= attackRange;
                attackY -= attackRange;
                break;
            case 2: // Left Up
                attackX -= attackRange;
                attackY += attackRange;
                break;
            case 3: // Up
                attackY += attackRange;
                break;
            case 4: // Right Up
                attackX += attackRange;
                attackY += attackRange;
                break;
            case 5: // Right Down
                attackX += attackRange;
                attackY -= attackRange;
                break;
            case 6: // Left
                attackX -= attackRange;
                break;
            case 7: // Right
                attackX += attackRange;
                break;
        }

        return new Rectangle(attackX, attackY, attackWidth, attackHeight);
    }
}
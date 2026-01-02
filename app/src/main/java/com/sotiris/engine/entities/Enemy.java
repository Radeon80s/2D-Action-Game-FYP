package com.sotiris.engine.entities;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.utils.Array;
import com.sotiris.engine.utils.CollisionManager;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class Enemy extends Actor {
    private static final float BODY_RADIUS = 38f;
    private static final float MAX_SPEED = 200f;
    private static final float FRAME_DURATION = 0.08f;
    public static final int INITIAL_HEALTH = 120;
    private static final float HEALTH_REGEN_RATE = 5f;
    private static final float ATTACK_RANGE = 280f;
    private static final float STOP_ATTACK_RANGE = 350f;
    private static final float CHASE_RANGE = 600f;
    private static final float BULLET_SPEED = 700f;
    private static final int BULLET_DAMAGE = 3;

    private final AssetManager assetManager;

    public enum EnemyState { IDLE, WALK, ATTACK, DEATH }

    private final Vector2 position;
    private final Vector2 velocity;
    private final Player player;
    private final CollisionManager collisionManager;
    private Body body;
    private final World world;
    private Runnable onDeathCallback;
    private EnemyState currentState;
    private int currentDirection;
    private float stateTime;
    private int health;
    private boolean isDead;
    private boolean isAttacking;
    private boolean isMoving;
    private int previousFrameIndex;
    private float idleTime;

    private final Vector2 lastKnownPlayerPosition;
    private boolean playerInSight;

    private final Map<EnemyState, Animation<TextureRegion>[]> animations;
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final ParticleEffectPool bloodEffectPool;
    private final Array<ParticleEffectPool.PooledEffect> activeBloodEffects;
    private final BulletPool bulletPool;

    public Enemy(AssetManager assetManager, float x, float y, Player player, CollisionManager collisionManager, World world, BulletPool bulletPool) {
        this.assetManager = assetManager;
        this.bulletPool = bulletPool;
        this.position = new Vector2(x, y);
        this.velocity = new Vector2();
        this.player = player;
        this.collisionManager = collisionManager;
        this.world = world;
        this.lastKnownPlayerPosition = new Vector2();
        this.playerInSight = false;

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyDef.BodyType.DynamicBody;
        bodyDef.position.set(x, y);
        this.body = world.createBody(bodyDef);

        CircleShape shape = new CircleShape();
        shape.setRadius(BODY_RADIUS);
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.filter.groupIndex = -1;
        this.body.createFixture(fixtureDef);
        shape.dispose();

        this.currentState = EnemyState.IDLE;
        this.currentDirection = 0;
        this.stateTime = 0f;
        this.health = INITIAL_HEALTH;
        this.isDead = false;
        this.isAttacking = false;
        this.isMoving = false;
        this.previousFrameIndex = -1;
        this.idleTime = 0f;

        this.animations = new EnumMap<>(EnemyState.class);
        loadAnimations();
        ParticleEffect bloodEffectTemplate = new ParticleEffect();
        bloodEffectTemplate.load(Gdx.files.internal("blood.p"), Gdx.files.internal(""));
        bloodEffectPool = new ParticleEffectPool(bloodEffectTemplate, 5, 10);
        activeBloodEffects = new Array<>();

        setSize(BODY_RADIUS * 2, BODY_RADIUS * 2);
        setPosition(position.x - getWidth() / 2, position.y - getHeight() / 2);
    }

    private void loadAnimations() {
        loadAnimation(EnemyState.IDLE, "enemy/enemy_idle.png");
        loadAnimation(EnemyState.WALK, "enemy/enemy_walk_gun.png");
        loadAnimation(EnemyState.ATTACK, "enemy/enemy_shoot.png");
        loadAnimation(EnemyState.DEATH, "enemy/enemy_death.png");
    }

    @SuppressWarnings("unchecked")
    private void loadAnimation(EnemyState state, String filename) {
        Texture sheet = assetManager.get(filename, Texture.class);
        int frameWidth = sheet.getWidth() / 8;
        int frameHeight = sheet.getHeight() / 8;
        TextureRegion[][] tmp = TextureRegion.split(sheet, frameWidth, frameHeight);
        Animation<TextureRegion>[] directionAnimations = new Animation[8];
        for (int dir = 0; dir < 8; dir++) {
            Array<TextureRegion> frames = new Array<>(tmp[dir]);
            directionAnimations[dir] = new Animation<>(FRAME_DURATION, frames);
        }
        animations.put(state, directionAnimations);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (isDead) return;
        if (player.isDead()) return;
        stateTime += delta;

        updateState();
        avoidStacking(delta);
        updatePosition(delta);
        updateDirection();

        if (currentState == EnemyState.WALK && !isMoving) {
            currentState = EnemyState.IDLE;
        }

        if (health <= 0 && !isDead) {
            die();
        }

        if (isAttacking && !player.isDead()) {
            handleAttackAnimation();
        }

        if (currentState == EnemyState.IDLE) {
            idleTime += delta;
            if (idleTime > 2.0f) {
                health = Math.min(health + (int) (HEALTH_REGEN_RATE * delta), INITIAL_HEALTH);
            }
        } else {
            idleTime = 0f;
        }

        for (int i = activeBloodEffects.size - 1; i >= 0; i--) {
            ParticleEffectPool.PooledEffect bloodEffect = activeBloodEffects.get(i);
            if(!isDead) {
                bloodEffect.update(delta);
            } else {
                activeBloodEffects.removeIndex(i);
            }
            if (bloodEffect.isComplete()) {
                bloodEffect.free();
                activeBloodEffects.removeIndex(i);
            }
        }
    }

    private void updateState() {
        float distanceToPlayer = position.dst(player.getPosition());
        playerInSight = hasLineOfSight();

        if (distanceToPlayer < BODY_RADIUS * 4.5f) {
            playerInSight = true;
        }

        if (playerInSight) {
            lastKnownPlayerPosition.set(player.getPosition());
        }

        switch (currentState) {
            case IDLE:
                if (distanceToPlayer < ATTACK_RANGE && (playerInSight || isPlayerNearby())) {
                    currentState = EnemyState.ATTACK;
                    stateTime = 0f;
                    isAttacking = true;
                    isMoving = false;
                    velocity.setZero();
                } else if (distanceToPlayer < CHASE_RANGE) {
                    currentState = EnemyState.WALK;
                    isMoving = true;
                    moveTowardsPlayer();
                } else {
                    randomWander();
                }
                break;

            case WALK:
                if (distanceToPlayer < ATTACK_RANGE && (playerInSight || isPlayerNearby())) {
                    currentState = EnemyState.ATTACK;
                    stateTime = 0f;
                    isAttacking = true;
                    isMoving = false;
                    velocity.setZero();
                } else if (distanceToPlayer > CHASE_RANGE) {
                    currentState = EnemyState.IDLE;
                    isMoving = false;
                    velocity.setZero();
                } else if (!playerInSight) {
                    moveTowardsLastKnownPosition();
                } else {
                    moveTowardsPlayer();
                    if (!isMoving) {
                        randomWander();
                    }
                }
                break;

            case ATTACK:
                // FORCE the enemy to stay still while attacking
                isMoving = false;
                velocity.setZero();

                if (!isAttacking) {
                    isAttacking = true;
                    stateTime = 0f;
                }
                if (distanceToPlayer > STOP_ATTACK_RANGE || (!playerInSight && !isPlayerNearby())) {
                    currentState = EnemyState.WALK;
                    isMoving = true;
                    if (!playerInSight) {
                        moveTowardsLastKnownPosition();
                    } else {
                        moveTowardsPlayer();
                    }
                }
                break;

            case DEATH:
                // Stay completely still when dead
                isMoving = false;
                velocity.setZero();
                break;
        }
    }

    private void moveTowardsLastKnownPosition() {
        if (lastKnownPlayerPosition.isZero()) {
            return;
        }

        Vector2 toLastKnown = lastKnownPlayerPosition.cpy().sub(position).nor();
        Vector2 avoidance = obstacleAvoidance();

        Vector2 desiredVelocity = toLastKnown.scl(1.0f).add(avoidance.scl(0.5f)).nor().scl(MAX_SPEED);
        velocity.set(desiredVelocity);

        if (position.dst(lastKnownPlayerPosition) < 10f) {
            velocity.setZero();
            isMoving = false;
        }
    }

    private boolean hasLineOfSight() {
        Vector2 enemyPos = position.cpy().add(0.1f, 0.1f);
        Vector2 playerPos = player.getPosition().cpy().add(-0.1f, -0.1f);

        AtomicBoolean isBlockedByRaycast = new AtomicBoolean(false);
        boolean isBlockedByCollisionManager = false;

        world.rayCast((fixture, point, normal, fraction) -> {
            if (fixture.getUserData() instanceof Rectangle) {
                isBlockedByRaycast.set(true);
                return 0;
            }
            return -1;
        }, enemyPos, playerPos);

        for (Rectangle obstacle : collisionManager.getCollisionRectangles()) {
            if (Intersector.intersectSegmentRectangle(enemyPos, playerPos, obstacle)) {
                float bufferZone = 20f;
                Rectangle expandedObstacle = new Rectangle(obstacle.x - bufferZone, obstacle.y - bufferZone, obstacle.width + 2 * bufferZone, obstacle.height + 2 * bufferZone);

                if (Intersector.intersectSegmentRectangle(enemyPos, playerPos, expandedObstacle)) {
                    isBlockedByCollisionManager = true;
                    break;
                }
            }
        }

        return !(isBlockedByRaycast.get() || isBlockedByCollisionManager);
    }

    private boolean isPlayerNearby() {
        float nearbyDistance = 50f;
        return position.dst(player.getPosition()) < nearbyDistance;
    }

    private void randomWander() {
        float wanderRadius = 200f;
        float wanderDistance = MathUtils.random(50f, wanderRadius);
        float wanderAngle = MathUtils.random(0, 360);

        Vector2 wanderTarget = position.cpy().add(new Vector2(wanderDistance, 0).rotateDeg(wanderAngle));
        velocity.set(wanderTarget.cpy().sub(position).nor().scl(MAX_SPEED * 0.2f));
    }

    private void moveTowardsPlayer() {
        Vector2 toPlayer = player.getPosition().cpy().sub(position).nor();
        Vector2 avoidance = obstacleAvoidance();

        Vector2 desiredVelocity = toPlayer.scl(1.0f).add(avoidance.scl(0.5f)).nor().scl(MAX_SPEED);
        velocity.set(desiredVelocity);
    }

    private Vector2 obstacleAvoidance() {
        Vector2 avoidanceForce = new Vector2();

        for (Rectangle obstacle : collisionManager.getCollisionRectangles()) {
            float distance = getDistanceToObstacle(obstacle);
            float avoidanceRadius = BODY_RADIUS + 50f;

            if (distance < avoidanceRadius) {
                Vector2 obstacleCenter = new Vector2(obstacle.x + obstacle.width / 2, obstacle.y + obstacle.height / 2);
                Vector2 awayFromObstacle = position.cpy().sub(obstacleCenter).nor();
                float strength = (avoidanceRadius - distance) / avoidanceRadius;
                avoidanceForce.add(awayFromObstacle.scl(strength));
            }
        }

        for (Actor actor : getStage().getActors()) {
            if (actor instanceof Enemy && actor != this) {
                Enemy otherEnemy = (Enemy) actor;
                float distance = position.dst(otherEnemy.getPosition());
                float separationDistance = BODY_RADIUS * 3.5f;

                if (distance < separationDistance && distance > 0) {
                    Vector2 repulsion = position.cpy().sub(otherEnemy.getPosition()).nor()
                            .scl((separationDistance - distance) * 0.6f);
                    avoidanceForce.add(repulsion);
                }
            }
        }

        return avoidanceForce;
    }

    private float getDistanceToObstacle(Rectangle obstacle) {
        float closestX = Math.max(obstacle.x, Math.min(position.x, obstacle.x + obstacle.width));
        float closestY = Math.max(obstacle.y, Math.min(position.y, obstacle.y + obstacle.height));
        return position.dst(closestX, closestY);
    }

    private void handleAttackAnimation() {
        Animation<TextureRegion> attackAnimation = Objects.requireNonNull(animations.get(EnemyState.ATTACK))[currentDirection];
        float frameDuration = attackAnimation.getFrameDuration();
        int frameIndex = (int) (stateTime / frameDuration);

        // Only shoot when:
        // 1. Frame changed
        // 2. Player is in sight
        // 3. Enemy is NOT moving
        if (frameIndex != previousFrameIndex && playerInSight && !isMoving) {
            shoot();
        }

        previousFrameIndex = frameIndex;

        if (attackAnimation.isAnimationFinished(stateTime)) {
            isAttacking = false;
            if (position.dst(player.getPosition()) < STOP_ATTACK_RANGE && hasLineOfSight()) {
                stateTime = 0f;
                isAttacking = true;
            } else {
                currentState = EnemyState.WALK;
                isMoving = true;
                moveTowardsPlayer();
            }
        }
    }

    private void updateDirection() {
        Vector2 directionVec;

        if (isAttacking) {
            directionVec = player.getPosition().cpy().sub(position);
        } else if (isMoving && !velocity.isZero()) {
            directionVec = velocity.cpy();
        } else if (isMoving && velocity.isZero()) {
            directionVec = player.getPosition().cpy().sub(position);
        } else {
            directionVec = new Vector2(0, -1);
        }

        if (directionVec.isZero()) return;

        float angle = (directionVec.angleDeg() + 360) % 360;

        if (angle >= 337.5 || angle < 22.5) {
            currentDirection = 6;
        } else if (angle >= 22.5 && angle < 67.5) {
            currentDirection = 4;
        } else if (angle >= 67.5 && angle < 112.5) {
            currentDirection = 3;
        } else if (angle >= 112.5 && angle < 157.5) {
            currentDirection = 2;
        } else if (angle >= 157.5 && angle < 202.5) {
            currentDirection = 7;
        } else if (angle >= 202.5 && angle < 247.5) {
            currentDirection = 1;
        } else if (angle >= 247.5 && angle < 292.5) {
            currentDirection = 0;
        } else if (angle >= 292.5 && angle < 337.5) {
            currentDirection = 5;
        }
    }

    private void updatePosition(float delta) {
        if (isMoving) {
            Vector2 movement = velocity.cpy().scl(delta);
            Vector2 newPosition = position.cpy().add(movement);

            if (!isColliding(newPosition)) {
                position.set(newPosition);
                body.setTransform(newPosition, body.getAngle());
                setPosition(position.x - getWidth() / 2, position.y - getHeight() / 2);
            } else {
                velocity.setZero();
                isMoving = false;
            }
        }
    }

    private boolean isColliding(Vector2 newPosition) {
        Rectangle enemyRect = new Rectangle(newPosition.x - BODY_RADIUS, newPosition.y - BODY_RADIUS, BODY_RADIUS * 2, BODY_RADIUS * 2);
        for (Rectangle rect : collisionManager.getCollisionRectangles()) {
            if (rect.overlaps(enemyRect)) {
                return true;
            }
        }
        return false;
    }

    private void avoidStacking(float delta) {
        if (getStage() == null) {
            return;
        }

        for (Actor actor : getStage().getActors()) {
            if (actor instanceof Enemy && actor != this) {
                Enemy otherEnemy = (Enemy) actor;
                float distance = position.dst(otherEnemy.getPosition());
                float minDistance = BODY_RADIUS * 2.5f;

                if (distance < minDistance && distance > 0) {
                    Vector2 repulsion = position.cpy().sub(otherEnemy.getPosition()).nor();
                    float repulsionStrength = MathUtils.clamp((minDistance - distance) * 0.3f, 0f, 0.5f);
                    repulsion.scl(repulsionStrength);
                    velocity.add(repulsion.scl(delta));
                }
            }
        }
    }

    private void shoot() {
        Vector2 direction = player.getPosition().cpy().sub(position).nor();
        if (!player.isDead()) {
            Bullet bullet = bulletPool.obtainBullet(
                    position.x,
                    position.y,
                    direction.scl(BULLET_SPEED),
                    BULLET_DAMAGE,
                    this
            );
            if (getStage() != null) {
                getStage().addActor(bullet);
            }
        }
    }

    private void enhanceBloodEffect(ParticleEffectPool.PooledEffect effect) {
        Array<ParticleEmitter> emitters = new Array<>(effect.getEmitters());
        for (ParticleEmitter emitter : emitters) {
            boolean isSplash = MathUtils.randomBoolean(0.7f);

            if (isSplash) {
                emitter.getEmission().setHigh(50f, 90f);
                emitter.getEmission().setLow(25f, 50f);
            } else {
                emitter.getEmission().setHigh(50f, 100f);
                emitter.getEmission().setLow(0f);
            }

            emitter.getLife().setHigh(200f, 250f);
            emitter.getLife().setLow(50f, 150f);

            if (isSplash) {
                emitter.getSpawnWidth().setHigh(3f, 13f);
                emitter.getSpawnHeight().setHigh(3f, 13f);
            } else {
                emitter.getSpawnWidth().setHigh(1f, 1f);
                emitter.getSpawnHeight().setHigh(1f, 1f);
            }

            if (isSplash) {
                emitter.getXScale().setHigh(1.5f, 2.5f);
                emitter.getYScale().setHigh(2f, 3.5f);
            } else {
                emitter.getXScale().setHigh(1.5f, 1.5f);
                emitter.getYScale().setHigh(2f, 2f);
            }

            if (isSplash) {
                emitter.getVelocity().setHigh(12f, 35f);
            } else {
                emitter.getVelocity().setHigh(15f, 20f);
                emitter.getVelocity().setLow(0f);
            }

            if (isSplash) {
                emitter.getAngle().setHigh(31f, 180f);
            } else {
                emitter.getAngle().setHigh(91f, 93f);
            }

            if (isSplash) {
                emitter.getRotation().setHigh(0f, 130f);
            } else {
                emitter.getRotation().setActive(false);
            }

            if (isSplash) {
                emitter.getGravity().setHigh(-50f, -150f);
            } else {
                emitter.getGravity().setHigh(-50f, -75f);
            }

            float rStart = MathUtils.random(0.8f, 1f);
            float rEnd = MathUtils.random(0.4f, 0.7f);

            emitter.getTint().setColors(new float[]{
                    rStart, 0f, 0f, 1f,
                    rEnd, 0f, 0f, 0.7f
            });
            emitter.getTint().setTimeline(new float[]{0f, 1f});

            emitter.getTransparency().setHigh(1f);
            emitter.getTransparency().setLow(0.7f);

            if (isSplash) {
                emitter.getWind().setActive(true);
                emitter.getWind().setHigh(MathUtils.random(-20f, 20f));
                emitter.getWind().setLow(MathUtils.random(-10f, 10f));
            } else {
                emitter.getWind().setActive(false);
            }

            if (isSplash) {
                emitter.setMaxParticleCount(MathUtils.random(25, 100));
            } else {
                emitter.setMaxParticleCount(MathUtils.random(5, 20));
            }

            emitter.setAdditive(false);
            emitter.setBehind(true);
            emitter.setAligned(isSplash);
            emitter.setPremultipliedAlpha(false);
            emitter.setSpriteMode(ParticleEmitter.SpriteMode.random);
        }

        effect.setPosition(this.getPosition().x, this.getPosition().y);

        float offsetX = MathUtils.random(-5f, 5f);
        float offsetY = MathUtils.random(-5f, 5f);
        effect.setPosition(
                this.getPosition().x + offsetX,
                this.getPosition().y + offsetY
        );

        effect.scaleEffect(MathUtils.random(0.8f, 1.2f));
    }

    private void setBloodDirection(ParticleEffectPool.PooledEffect effect, Vector2 attackDirection) {
        attackDirection.nor();
        for (ParticleEmitter emitter : effect.getEmitters()) {
            emitter.getAngle().setHigh(attackDirection.angleDeg());
            emitter.getAngle().setLow(attackDirection.angleDeg());
        }
    }

    public void takeDamage(int damage, Vector2 attackDirection) {
        if (isDead) return;
        health -= damage;

        ParticleEffectPool.PooledEffect newBloodEffect = bloodEffectPool.obtain();
        enhanceBloodEffect(newBloodEffect);
        setBloodDirection(newBloodEffect, attackDirection);
        newBloodEffect.setPosition(getX() + getWidth() / 2, getY() + getHeight() / 2);
        newBloodEffect.start();
        activeBloodEffects.add(newBloodEffect);

        if (health <= 0) {
            die();
        }
    }

    public void setOnDeath(Runnable onDeathCallback) {
        this.onDeathCallback = onDeathCallback;
    }

    private void die() {
        isDead = true;
        currentState = EnemyState.DEATH;
        stateTime = 0f;

        // IMMEDIATELY destroy the physics body so no more collisions
        if (body != null) {
            world.destroyBody(body);
            body = null;
        }

        // Call the death callback immediately
        if (onDeathCallback != null) {
            onDeathCallback.run();
        }

        // Remove much faster - only wait for animation to finish
        addAction(Actions.sequence(
                Actions.delay(0.5f), // Just half a second for death animation
                Actions.removeActor()
        ));
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (!isVisible()) return;

        Animation<TextureRegion>[] stateAnimations = animations.get(currentState);
        if (stateAnimations == null) return;

        Animation<TextureRegion> animation = stateAnimations[currentDirection];
        TextureRegion currentFrame;

        // For death animation, don't loop
        if (currentState.equals(EnemyState.DEATH)) {
            currentFrame = animation.getKeyFrame(stateTime, false);
            batch.draw(currentFrame, getX(), getY(), getWidth(), getHeight());

            // Don't draw health bar when dead
            return;
        }

        // For all other states, loop normally
        currentFrame = animation.getKeyFrame(stateTime, true);
        batch.draw(currentFrame, getX(), getY(), getWidth(), getHeight());

        // Draw health bar (only when alive)
        if (!isDead) {
            drawHealthBar(batch);
        }

        // Draw blood effects
        for (ParticleEffectPool.PooledEffect bloodEffect : activeBloodEffects) {
            bloodEffect.draw(batch);
        }
    }

    private void drawHealthBar(Batch batch) {
        if(health < 0) {
            return;
        }

        batch.end();

        float healthBarWidth = getWidth() * 0.9f;
        float healthBarHeight = 4;

        float healthPercentage = (float) health / INITIAL_HEALTH;
        float currentHealthBarWidth = healthBarWidth * healthPercentage;

        float healthBarX = getX() + (getWidth() * 0.05f);
        float healthBarY = getY() + getHeight() + 4;

        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(new Color(0, 0, 0, 0.5f));
        shapeRenderer.rect(healthBarX, healthBarY, healthBarWidth, healthBarHeight);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.rect(healthBarX, healthBarY, currentHealthBarWidth, healthBarHeight);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.BLACK);
        shapeRenderer.rect(healthBarX, healthBarY, healthBarWidth, healthBarHeight);
        shapeRenderer.end();

        batch.begin();
    }

    @Override
    public boolean remove() {
        if (body != null && !isDead) {
            try {
                world.destroyBody(body);
            } catch (Exception e) {
                // Body already destroyed, ignore
            }
        }

        shapeRenderer.dispose();

        for (ParticleEffectPool.PooledEffect effect : activeBloodEffects) {
            effect.free();
        }
        bloodEffectPool.clear();

        return super.remove();
    }

    public Vector2 getPosition() {
        return position.cpy();
    }

    public Rectangle getBounds() {
        return new Rectangle(position.x - getWidth() / 2,
                position.y - getHeight() / 2, getWidth(), getHeight());
    }
}
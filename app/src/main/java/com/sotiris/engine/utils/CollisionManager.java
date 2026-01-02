package com.sotiris.engine.utils;

import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.sotiris.engine.entities.Player;

public class CollisionManager {
    private final Array<Rectangle> collisionRectangles;

    private final TiledMap map;

    public CollisionManager(TiledMap map) {
        collisionRectangles = new Array<>();
        this.map = map;
        for (MapObject object : map.getLayers().get("Collisions").getObjects()) {
            if (object instanceof RectangleMapObject) {
                RectangleMapObject rectangleObject = (RectangleMapObject) object;
                Rectangle rect = rectangleObject.getRectangle();
                float expandAmount = 5f; // Adjust this value as needed
                rect.x -= expandAmount; // Move the rectangle left
                rect.width += expandAmount; // Expand the width to cover the new space

                collisionRectangles.add(rect);
            }
        }
    }

    public Array<Rectangle> getCollisionRectangles() {
        return collisionRectangles;
    }

    public void addCollisionRectangle(Rectangle rectangle) {

        float expansion = 1f; // Adjust this value to control how much the rectangle expands
        rectangle.x -= expansion;
        rectangle.y -= expansion;
        rectangle.width += (expansion * 2);
        rectangle.height += (expansion * 2);
        collisionRectangles.add(rectangle);
    }

    public boolean isPlayerInsideRectangle(Player player, Rectangle rectangle) {
        Rectangle playerRect = new Rectangle(player.getX(), player.getY(), player.getWidth(), player.getHeight());
        return playerRect.overlaps(rectangle);
    }

    public void clearCollisionRectangles() {
        collisionRectangles.clear();
        for (MapObject object : map.getLayers().get("Collisions").getObjects()) {
            if (object instanceof RectangleMapObject) {
                RectangleMapObject rectangleObject = (RectangleMapObject) object;
                Rectangle rect = rectangleObject.getRectangle();
                float expandAmount = 5f; // Adjust this value as needed
                rect.x -= expandAmount; // Move the rectangle left
                rect.width += expandAmount; // Expand the width to cover the new space

                collisionRectangles.add(rect);
            }
        }
    }
}

package com.aiinterior.factory.design;

import com.aiinterior.factory.geometry.Priority;

/**
 * One piece of furniture before placement. {@code width} runs along the wall it backs onto,
 * {@code depth} away from it. Standard sizes are typical catalogue sizes, not a specific product.
 */
public record FurnitureSpec(String key, String name, String category, double width, double depth, double height,
                            double frontClearance, Placement placement, String anchor, Priority priority,
                            String source, String note) {

    public enum Placement { WALL, CENTER, IN_FRONT_OF_ANCHOR, OPPOSITE_ANCHOR }

    public FurnitureSpec withPriority(Priority p, String n) {
        return new FurnitureSpec(key, name, category, width, depth, height, frontClearance, placement, anchor, p, source, n == null ? note : n);
    }

    public FurnitureSpec withKey(String k) {
        return new FurnitureSpec(k, name, category, width, depth, height, frontClearance, placement, anchor, priority, source, note);
    }
}

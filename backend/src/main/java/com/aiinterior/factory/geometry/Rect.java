package com.aiinterior.factory.geometry;

/** Axis-aligned rectangle on the floor plane, millimetres. */
public record Rect(double x, double y, double w, double d) {

    public double maxX() {
        return x + w;
    }

    public double maxY() {
        return y + d;
    }

    public boolean intersects(Rect o) {
        double eps = 0.5;
        return x < o.maxX() - eps && o.x < maxX() - eps && y < o.maxY() - eps && o.y < maxY() - eps;
    }

    public boolean within(double length, double width) {
        double eps = 0.5;
        return x >= -eps && y >= -eps && maxX() <= length + eps && maxY() <= width + eps;
    }

    public double area() {
        return w * d;
    }
}

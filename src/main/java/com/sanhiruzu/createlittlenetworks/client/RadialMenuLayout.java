package com.sanhiruzu.createlittlenetworks.client;

/** GUI-scaled geometry; the first wedge is centered at twelve o'clock. */
public record RadialMenuLayout(float innerRadius, float outerRadius) {
    public static RadialMenuLayout fit(int width, int height) {
        float outer = Math.max(16, Math.min(100, Math.min((width - 24) / 2F, (height - 40) / 2F)));
        return new RadialMenuLayout(outer * .4F, outer);
    }

    public float labelRadius() { return (innerRadius + outerRadius) / 2F; }
    public float scale() { return outerRadius / 100F; }

    /** Keeps directional selection beyond the ring, with an inactive center. */
    public int segmentAt(double x, double y, int count) {
        if (count <= 0 || Math.hypot(x, y) < innerRadius) return -1;
        double angle = Math.toDegrees(Math.atan2(y, x)) + 90 + 180D / count;
        angle = (angle % 360 + 360) % 360;
        return Math.min(count - 1, (int) (angle * count / 360D));
    }
}

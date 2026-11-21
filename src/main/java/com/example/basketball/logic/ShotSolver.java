package com.example.basketball.logic;

import net.minecraft.util.math.Vec3d;

/** Решает траекторию мяча с теми же законами, что у ThrownEntity: pos += v; v *= 0.99; v.y -= 0.05. */
public final class ShotSolver {
    private static final double GRAVITY = 0.05;
    private static final double DRAG = 0.99;

    /** Начальная скорость, при которой мяч упадёт ровно в target (центр отверстия кольца). */
    public static Vec3d solve(Vec3d start, Vec3d target) {
        Vec3d flat = new Vec3d(target.x - start.x, 0, target.z - start.z);
        if (flat.lengthSquared() < 1e-6) return null;
        Vec3d u = flat.normalize();

        double[] speeds = {0.8, 0.95, 1.1, 1.25, 1.4, 1.55, 1.7};
        Vec3d best = null;
        double bestScore = Double.MAX_VALUE;

        for (int si = 0; si < speeds.length; si++) {
            double s = speeds[si];
            for (int deg = 25; deg <= 78; deg++) {
                double pr = Math.toRadians(deg);
                double vx = u.x * s * Math.cos(pr);
                double vz = u.z * s * Math.cos(pr);
                double vy = s * Math.sin(pr);
                double x = start.x, y = start.y, z = start.z;

                for (int t = 0; t < 160; t++) {
                    double nx = x + vx, ny = y + vy, nz = z + vz;
                    if (vy < 0 && ny < target.y) {
                        double f = (y - target.y) / (y - ny);
                        double cx = x + (nx - x) * f;
                        double cz = z + (nz - z) * f;
                        double err = Math.hypot(cx - target.x, cz - target.z);
                        double score = err + (deg < 45 ? 0.1 : 0.0) + si * 0.01;
                        if (score < bestScore) {
                            bestScore = score;
                            best = new Vec3d(s * Math.cos(pr) * u.x, s * Math.sin(pr), s * Math.cos(pr) * u.z);
                        }
                        break;
                    }
                    x = nx; y = ny; z = nz;
                    vx *= DRAG; vy *= DRAG; vz *= DRAG;
                    vy -= GRAVITY;
                }
            }
        }
        return best;
    }

    /** Скорость паса (почти по прямой) в указанную точку на фиксированной скорости. */
    public static Vec3d pass(Vec3d start, Vec3d target, double speed) {
        Vec3d flat = new Vec3d(target.x - start.x, 0, target.z - start.z);
        double hd = flat.length();
        if (hd < 1e-3) return null;
        Vec3d u = flat.multiply(1.0 / hd);

        Vec3d best = null;
        double bestErr = Double.MAX_VALUE;
        for (int deg = -15; deg <= 40; deg++) {
            double pr = Math.toRadians(deg);
            double vx = u.x * speed * Math.cos(pr);
            double vz = u.z * speed * Math.cos(pr);
            double vy = speed * Math.sin(pr);
            double x = start.x, y = start.y, z = start.z;
            for (int t = 0; t < 120; t++) {
                x += vx; y += vy; z += vz;
                vx *= DRAG; vy *= DRAG; vz *= DRAG;
                vy -= GRAVITY;
                if (Math.hypot(x - start.x, z - start.z) >= hd) {
                    double err = Math.abs(y - target.y);
                    if (err < bestErr) {
                        bestErr = err;
                        best = new Vec3d(u.x * speed * Math.cos(pr), speed * Math.sin(pr), u.z * speed * Math.cos(pr));
                    }
                    break;
                }
            }
        }
        return best;
    }

    private ShotSolver() { }
}

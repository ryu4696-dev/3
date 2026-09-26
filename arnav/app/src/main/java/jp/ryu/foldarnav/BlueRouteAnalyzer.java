package jp.ryu.foldarnav;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

public final class BlueRouteAnalyzer {
    private BlueRouteAnalyzer() {}

    private static final int[] NX = {-1,0,1,-1,1,-1,0,1};
    private static final int[] NY = {-1,-1,-1,0,0,1,1,1};

    public static void analyze(
            ByteBuffer buf,
            int width,
            int height,
            int rowStride,
            int pixelStride) {

        if (buf == null ||
                width < 160 ||
                height < 240 ||
                pixelStride < 3) return;

        final int gw = clamp(width / 4, 96, 180);
        final int gh = clamp(height / 4, 150, 280);

        boolean[] mask = new boolean[gw * gh];

        int minGy = (int)(gh * 0.18f);
        int maxGy = (int)(gh * 0.88f);

        for (int gy = minGy; gy <= maxGy; gy++) {
            float sy = (gy + 0.5f) * height / (float)gh;
            int py = clamp((int)sy, 0, height - 1);

            for (int gx = 1; gx < gw - 1; gx++) {
                float sx = (gx + 0.5f) * width / (float)gw;
                int px = clamp((int)sx, 0, width - 1);

                if (routePixelNearby(
                        buf,
                        width,
                        height,
                        rowStride,
                        pixelStride,
                        px,
                        py)) {

                    mask[gy * gw + gx] = true;
                }
            }
        }

        // One-cell dilation bridges car-icon and anti-aliasing gaps.
        boolean[] expanded = Arrays.copyOf(mask, mask.length);

        for (int y = minGy + 1; y < maxGy; y++) {
            for (int x = 1; x < gw - 1; x++) {
                int idx = y * gw + x;
                if (!mask[idx]) continue;

                for (int k = 0; k < 8; k++) {
                    expanded[
                            (y + NY[k]) * gw +
                            (x + NX[k])] = true;
                }
            }
        }

        int expectedX = gw / 2;
        int expectedY = (int)(gh * 0.78f);

        int seed = findSeed(
                expanded,
                gw,
                gh,
                expectedX,
                expectedY);

        if (seed < 0) return;

        int seedX = seed % gw;
        int seedY = seed / gw;

        int[] dist = new int[gw * gh];
        Arrays.fill(dist, -1);

        ArrayDeque<Integer> q = new ArrayDeque<>();
        dist[seed] = 0;
        q.add(seed);

        int maxDist = 0;
        int componentCount = 0;

        while (!q.isEmpty()) {
            int v = q.removeFirst();
            int x = v % gw;
            int y = v / gw;
            int d = dist[v];

            componentCount++;
            if (d > maxDist) maxDist = d;

            for (int k = 0; k < 8; k++) {
                int xx = x + NX[k];
                int yy = y + NY[k];

                if (xx <= 0 ||
                        xx >= gw - 1 ||
                        yy < minGy ||
                        yy > maxGy) continue;

                // Navigation map is heading-up. Ignore the branch well behind the car.
                if (yy > seedY + 5) continue;

                int ni = yy * gw + xx;
                if (!expanded[ni] || dist[ni] >= 0) continue;

                dist[ni] = d + 1;
                q.addLast(ni);
            }
        }

        if (componentCount < 18 || maxDist < 12) return;

        int bucketSize = 4;
        int bucketCount = maxDist / bucketSize + 1;

        long[] sumX = new long[bucketCount];
        long[] sumY = new long[bucketCount];
        int[] count = new int[bucketCount];

        for (int i = 0; i < dist.length; i++) {
            int d = dist[i];
            if (d < 0) continue;

            int b = d / bucketSize;
            if (b < 0 || b >= bucketCount) continue;

            int x = i % gw;
            int y = i / gw;

            sumX[b] += x;
            sumY[b] += y;
            count[b]++;
        }

        ArrayList<NavigationState.RoutePoint> points =
                new ArrayList<>();

        float lastX = seedX;
        int usableBuckets = 0;

        for (int b = 0; b < bucketCount; b++) {
            if (count[b] < 2) continue;

            float x = sumX[b] / (float)count[b];
            float y = sumY[b] / (float)count[b];

            // Reject a rare component jump caused by another purple UI element.
            if (usableBuckets > 1 &&
                    Math.abs(x - lastX) > gw * 0.30f) {
                continue;
            }

            lastX =
                    lastX * 0.38f +
                    x * 0.62f;

            float lateral =
                    (lastX - seedX) /
                    Math.max(1f, gw * 0.50f);

            float progress =
                    b / (float)Math.max(1, bucketCount - 1);

            points.add(
                    new NavigationState.RoutePoint(
                            clamp(lateral, -1.45f, 1.45f),
                            clamp(progress, 0f, 1f)));

            usableBuckets++;
        }

        if (points.size() < 6) return;

        points = smooth(points);

        float componentScore =
                clamp(componentCount / 120f, 0f, 1f);

        float lengthScore =
                clamp(maxDist / 70f, 0f, 1f);

        float confidence =
                0.52f * componentScore +
                0.48f * lengthScore;

        NavigationState.get().setRoute(
                points,
                confidence);
    }

    private static ArrayList<NavigationState.RoutePoint> smooth(
            ArrayList<NavigationState.RoutePoint> src) {

        if (src.size() < 5) return src;

        ArrayList<NavigationState.RoutePoint> out =
                new ArrayList<>(src.size());

        for (int i = 0; i < src.size(); i++) {
            float sum = 0f;
            float weight = 0f;

            for (int d = -2; d <= 2; d++) {
                int j = i + d;
                if (j < 0 || j >= src.size()) continue;

                float w = d == 0 ? 3f :
                        Math.abs(d) == 1 ? 2f : 1f;

                sum += src.get(j).lateral * w;
                weight += w;
            }

            NavigationState.RoutePoint p = src.get(i);

            out.add(
                    new NavigationState.RoutePoint(
                            sum / Math.max(1f, weight),
                            p.progress));
        }

        return out;
    }

    private static int findSeed(
            boolean[] mask,
            int w,
            int h,
            int expectedX,
            int expectedY) {

        int best = -1;
        float bestScore = Float.MAX_VALUE;

        int minY = (int)(h * 0.58f);
        int maxY = (int)(h * 0.90f);

        for (int y = minY; y <= maxY; y++) {
            for (int x = (int)(w * 0.15f);
                    x <= (int)(w * 0.85f);
                    x++) {

                int idx = y * w + x;
                if (!mask[idx]) continue;

                float dx = x - expectedX;
                float dy = (y - expectedY) * 0.72f;

                float score =
                        dx * dx +
                        dy * dy;

                if (score < bestScore) {
                    bestScore = score;
                    best = idx;
                }
            }
        }

        return best;
    }

    private static boolean routePixelNearby(
            ByteBuffer buf,
            int width,
            int height,
            int rowStride,
            int pixelStride,
            int x,
            int y) {

        final int[] offs = {-2,0,2};

        int hits = 0;

        for (int oy : offs) {
            int yy = clamp(y + oy, 0, height - 1);

            for (int ox : offs) {
                int xx = clamp(x + ox, 0, width - 1);

                int p =
                        yy * rowStride +
                        xx * pixelStride;

                if (p < 0 || p + 2 >= buf.limit()) continue;

                int r = buf.get(p) & 0xFF;
                int g = buf.get(p + 1) & 0xFF;
                int b = buf.get(p + 2) & 0xFF;

                if (isRouteColor(r, g, b)) {
                    hits++;
                    if (hits >= 2) return true;
                }
            }
        }

        return false;
    }

    private static boolean isRouteColor(
            int r,
            int g,
            int b) {

        boolean googleBlue =
                b > 145 &&
                g > 70 &&
                b - r > 42 &&
                b - g > 24;

        boolean indigo =
                b > 105 &&
                r >= 18 &&
                r < 185 &&
                g < 130 &&
                b - g > 55 &&
                b - r > 28;

        boolean deepPurple =
                b > 95 &&
                r > g + 8 &&
                b > r + 22 &&
                g < 105;

        return googleBlue || indigo || deepPurple;
    }

    private static int clamp(
            int v,
            int lo,
            int hi) {

        return Math.max(lo, Math.min(hi, v));
    }

    private static float clamp(
            float v,
            float lo,
            float hi) {

        return Math.max(lo, Math.min(hi, v));
    }
}

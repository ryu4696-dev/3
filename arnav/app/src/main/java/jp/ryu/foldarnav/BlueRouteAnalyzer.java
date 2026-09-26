package jp.ryu.foldarnav;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BlueRouteAnalyzer {
    private BlueRouteAnalyzer() {}

    public static void analyze(ByteBuffer buf, int width, int height, int rowStride, int pixelStride) {
        if (buf == null || width < 100 || height < 100 || pixelStride < 3) return;

        ArrayList<NavigationState.RoutePoint> out = new ArrayList<>();
        float prevX = width * 0.5f;
        int hitRows = 0;
        int testedRows = 0;

        int yStart = (int)(height * 0.92f);
        int yEnd = (int)(height * 0.18f);
        int stepY = Math.max(3, height / 180);
        int halfWindow = Math.max(70, width / 3);

        for (int y = yStart; y >= yEnd; y -= stepY) {
            testedRows++;
            long sumX = 0;
            int count = 0;
            int minX = Math.max(0, (int)prevX - halfWindow);
            int maxX = Math.min(width - 1, (int)prevX + halfWindow);
            int row = y * rowStride;

            for (int x = minX; x <= maxX; x += 2) {
                int p = row + x * pixelStride;
                if (p + 2 >= buf.limit()) break;
                int r = buf.get(p) & 0xFF;
                int g = buf.get(p + 1) & 0xFF;
                int b = buf.get(p + 2) & 0xFF;

                boolean googleBlue =
                        b > 150 &&
                        g > 75 &&
                        b - r > 45 &&
                        b - g > 25 &&
                        g - r > -20;
                boolean paleBlue =
                        b > 195 &&
                        g > 135 &&
                        r < 180 &&
                        b - r > 35;

                // Current Google Maps navigation route is often indigo/purple.
                boolean googleIndigo =
                        b > 120 &&
                        r >= 20 &&
                        r < 170 &&
                        g < 120 &&
                        b - r > 45 &&
                        b - g > 70;

                boolean deepPurple =
                        b > 105 &&
                        r > g + 10 &&
                        b > r + 30 &&
                        g < 95;

                if (googleBlue || paleBlue || googleIndigo || deepPurple) {
                    sumX += x;
                    count++;
                }
            }

            if (count >= 3) {
                float x = (float)sumX / count;
                if (Math.abs(x - prevX) < width * 0.28f || hitRows < 2) {
                    prevX = prevX * 0.62f + x * 0.38f;
                    float lateral = ((prevX / width) - 0.5f) * 2f;
                    float progress = (yStart - y) / (float)Math.max(1, yStart - yEnd);
                    out.add(new NavigationState.RoutePoint(lateral, progress));
                    hitRows++;
                }
            }
        }

        if (out.size() < 8) return;

        Collections.sort(out, (a,b) -> Float.compare(a.progress, b.progress));
        ArrayList<NavigationState.RoutePoint> simplified = new ArrayList<>();
        float next = 0f;
        for (NavigationState.RoutePoint p : out) {
            if (p.progress + 0.0001f >= next) {
                simplified.add(p);
                next += 0.045f;
            }
        }

        float confidence = Math.min(1f, hitRows / (float)Math.max(1, testedRows / 3));
        NavigationState.get().setRoute(simplified, confidence);
    }
}

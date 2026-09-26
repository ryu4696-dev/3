package jp.ryu.foldarnav;

import android.media.Image;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public final class RoadVision {
    private RoadVision() {}

    private static final class Sample {
        final float y;
        final float left;
        final float right;
        final float edgeScore;

        Sample(float y, float left, float right, float edgeScore) {
            this.y = y;
            this.left = left;
            this.right = right;
            this.edgeScore = edgeScore;
        }
    }

    private static final class Fit {
        final float m;
        final float b;
        Fit(float m, float b) {
            this.m = m;
            this.b = b;
        }
    }

    public static void analyze(Image image) {
        if (image == null || image.getPlanes().length == 0) return;

        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer y = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();

        int w = image.getWidth();
        int h = image.getHeight();

        if (w < 240 || h < 180) return;

        ArrayList<Sample> samples = new ArrayList<>();

        int yStart = (int)(h * 0.50f);
        int yEnd = (int)(h * 0.94f);
        int yStep = Math.max(7, h / 34);

        for (int row = yStart; row <= yEnd; row += yStep) {
            Sample s = detectPair(y, rowStride, pixelStride, w, h, row);
            if (s != null) samples.add(s);
        }

        NavigationState state = NavigationState.get();

        if (samples.size() < 5) {
            state.decayRoadConfidence();
            return;
        }

        Fit leftFit = fitLine(samples, true);
        Fit rightFit = fitLine(samples, false);

        float denominator = leftFit.m - rightFit.m;
        if (Math.abs(denominator) < 0.045f) {
            state.decayRoadConfidence();
            return;
        }

        float vanishY = (rightFit.b - leftFit.b) / denominator;
        float vanishX = leftFit.m * vanishY + leftFit.b;

        Sample top = samples.get(0);
        Sample bottom = samples.get(samples.size() - 1);

        float topWidth = top.right - top.left;
        float bottomWidth = bottom.right - bottom.left;
        float widening = bottomWidth / Math.max(1f, topWidth);

        boolean correctSlopes =
                leftFit.m < -0.035f &&
                rightFit.m > 0.035f;

        boolean plausibleVanish =
                vanishY > -h * 0.25f &&
                vanishY < h * 0.73f &&
                vanishX > -w * 0.15f &&
                vanishX < w * 1.15f;

        boolean widensTowardBottom =
                widening > 1.13f;

        float avgEdge = 0f;
        for (Sample s : samples) avgEdge += s.edgeScore;
        avgEdge /= samples.size();

        float sampleScore =
                clamp((samples.size() - 4f) / 8f, 0f, 1f);

        float wideningScore =
                clamp((widening - 1.05f) / 0.65f, 0f, 1f);

        float edgeScore =
                clamp((avgEdge - 30f) / 65f, 0f, 1f);

        float confidence =
                0.30f * sampleScore +
                0.35f * wideningScore +
                0.35f * edgeScore;

        if (!correctSlopes) confidence *= 0.30f;
        if (!plausibleVanish) confidence *= 0.18f;
        if (!widensTowardBottom) confidence *= 0.34f;

        if (confidence < 0.12f) {
            state.decayRoadConfidence();
            return;
        }

        float bottomCenter =
                (bottom.left + bottom.right) * 0.5f;

        float laneCenter =
                (bottomCenter - w * 0.5f) /
                (w * 0.5f);

        float normalizedVanishX =
                (vanishX / w - 0.5f) * 2f;

        float normalizedHorizonY =
                vanishY / h;

        float nearHalfWidth =
                (bottomWidth * 0.5f) / w;

        state.updateRoadGeometry(
                confidence,
                normalizedVanishX,
                normalizedHorizonY,
                nearHalfWidth,
                laneCenter);
    }

    private static Sample detectPair(
            ByteBuffer y,
            int rowStride,
            int pixelStride,
            int w,
            int h,
            int row) {

        int center = w / 2;

        int minLeft = Math.max(8, (int)(w * 0.04f));
        int maxLeft = center - (int)(w * 0.07f);

        int minRight = center + (int)(w * 0.07f);
        int maxRight = Math.min(w - 9, (int)(w * 0.96f));

        int bestLeftX = -1;
        int bestRightX = -1;
        int bestLeftEdge = 0;
        int bestRightEdge = 0;

        for (int x = minLeft; x <= maxLeft; x += 3) {
            int e = edge(y, rowStride, pixelStride, x, row);
            float centerBias =
                    1f - Math.abs(x - center * 0.58f) / Math.max(1f, w * 0.48f);
            int score = (int)(e * (0.78f + 0.22f * clamp(centerBias, 0f, 1f)));

            if (score > bestLeftEdge) {
                bestLeftEdge = score;
                bestLeftX = x;
            }
        }

        for (int x = minRight; x <= maxRight; x += 3) {
            int e = edge(y, rowStride, pixelStride, x, row);
            float centerBias =
                    1f - Math.abs(x - center * 1.42f) / Math.max(1f, w * 0.48f);
            int score = (int)(e * (0.78f + 0.22f * clamp(centerBias, 0f, 1f)));

            if (score > bestRightEdge) {
                bestRightEdge = score;
                bestRightX = x;
            }
        }

        if (bestLeftX < 0 || bestRightX < 0) return null;
        if (bestLeftEdge < 25 || bestRightEdge < 25) return null;

        int width = bestRightX - bestLeftX;
        if (width < w * 0.18f) return null;
        if (width > w * 0.92f) return null;

        float score =
                (bestLeftEdge + bestRightEdge) * 0.5f;

        return new Sample(
                row,
                bestLeftX,
                bestRightX,
                score);
    }

    private static Fit fitLine(
            ArrayList<Sample> samples,
            boolean left) {

        float sumY = 0f;
        float sumX = 0f;
        float sumYY = 0f;
        float sumYX = 0f;
        float n = samples.size();

        for (Sample s : samples) {
            float yy = s.y;
            float xx = left ? s.left : s.right;

            sumY += yy;
            sumX += xx;
            sumYY += yy * yy;
            sumYX += yy * xx;
        }

        float denom =
                n * sumYY -
                sumY * sumY;

        if (Math.abs(denom) < 0.001f) {
            return new Fit(0f, sumX / Math.max(1f, n));
        }

        float m =
                (n * sumYX -
                sumY * sumX) / denom;

        float b =
                (sumX - m * sumY) / n;

        return new Fit(m, b);
    }

    private static int edge(
            ByteBuffer b,
            int rowStride,
            int pixelStride,
            int x,
            int y) {

        int x0 = x - 5;
        int x1 = x + 5;

        if (x0 < 0) return 0;

        int a =
                y * rowStride +
                x0 * pixelStride;

        int c =
                y * rowStride +
                x1 * pixelStride;

        if (a < 0 || c >= b.limit()) return 0;

        int va = b.get(a) & 0xFF;
        int vc = b.get(c) & 0xFF;

        return Math.abs(vc - va);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

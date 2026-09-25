package jp.ryu.foldarnav;

import android.media.Image;
import java.nio.ByteBuffer;

public final class RoadVision {
    private RoadVision() {}

    public static void analyze(Image image) {
        if (image == null || image.getPlanes().length == 0) return;
        Image.Plane p = image.getPlanes()[0];
        ByteBuffer y = p.getBuffer();
        int rowStride = p.getRowStride();
        int pixelStride = p.getPixelStride();
        int w = image.getWidth();
        int h = image.getHeight();
        if (w < 200 || h < 200) return;

        float weightedMid = 0f;
        float weight = 0f;

        for (int row = (int)(h * 0.58f); row < (int)(h * 0.90f); row += Math.max(4, h / 80)) {
            int center = w / 2;
            int search = (int)(w * 0.42f);
            int left = -1, right = -1;
            int bestL = 0, bestR = 0;

            for (int x = Math.max(6, center - search); x < center - w / 14; x += 3) {
                int e = edge(y, rowStride, pixelStride, x, row);
                if (e > bestL) { bestL = e; left = x; }
            }
            for (int x = center + w / 14; x < Math.min(w - 6, center + search); x += 3) {
                int e = edge(y, rowStride, pixelStride, x, row);
                if (e > bestR) { bestR = e; right = x; }
            }

            if (left > 0 && right > 0 && bestL > 32 && bestR > 32 && right - left > w / 6) {
                float mid = ((left + right) * 0.5f - center) / (w * 0.5f);
                float rowWeight = 0.6f + (row / (float)h);
                weightedMid += mid * rowWeight;
                weight += rowWeight;
            }
        }

        if (weight > 0.5f) NavigationState.get().updateLaneCenter(weightedMid / weight);
    }

    private static int edge(ByteBuffer b, int rowStride, int pixelStride, int x, int y) {
        int a = y * rowStride + (x - 4) * pixelStride;
        int c = y * rowStride + (x + 4) * pixelStride;
        if (a < 0 || c >= b.limit()) return 0;
        int va = b.get(a) & 0xFF;
        int vc = b.get(c) & 0xFF;
        return Math.abs(vc - va);
    }
}

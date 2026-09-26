package jp.ryu.foldarnav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class NavigationState {
    public static final class RoutePoint {
        public final float lateral;
        public final float progress;

        public RoutePoint(float lateral, float progress) {
            this.lateral = lateral;
            this.progress = progress;
        }
    }

    private static final NavigationState INSTANCE = new NavigationState();
    public static NavigationState get() { return INSTANCE; }

    private final AtomicReference<List<RoutePoint>> route =
            new AtomicReference<>(Collections.emptyList());
    private final AtomicLong revision = new AtomicLong(0);

    public volatile float laneCenterOffset = 0f;
    public volatile float routeConfidence = 0f;
    public volatile long routeUpdatedAtMs = 0L;

    // Camera road geometry. Coordinates are normalized:
    // vanishX: -1..1, horizonY: 0..1, nearHalfWidth: 0..0.5.
    public volatile float roadConfidence = 0f;
    public volatile float roadVanishX = 0f;
    public volatile float roadHorizonY = 0.43f;
    public volatile float roadNearHalfWidth = 0.36f;
    public volatile long roadUpdatedAtMs = 0L;

    public volatile double latitude = 0d;
    public volatile double longitude = 0d;
    public volatile float speedMps = 0f;
    public volatile float bearingDeg = 0f;

    public volatile float imuAzimuthDeg = 0f;
    public volatile float imuPitchDeg = 0f;
    public volatile float imuRollDeg = 0f;

    private NavigationState() {}

    public void setRoute(List<RoutePoint> points, float confidence) {
        if (points == null || points.size() < 6) return;

        route.set(Collections.unmodifiableList(new ArrayList<>(points)));
        routeConfidence = clamp(confidence, 0f, 1f);
        routeUpdatedAtMs = android.os.SystemClock.elapsedRealtime();
        revision.incrementAndGet();
    }

    public List<RoutePoint> getRoute() {
        return route.get();
    }

    public long getRevision() {
        return revision.get();
    }

    public void updateLaneCenter(float value) {
        value = clamp(value, -1f, 1f);
        laneCenterOffset =
                laneCenterOffset * 0.84f +
                value * 0.16f;
    }

    public void updateRoadGeometry(
            float confidence,
            float vanishX,
            float horizonY,
            float nearHalfWidth,
            float laneCenter) {

        confidence = clamp(confidence, 0f, 1f);
        vanishX = clamp(vanishX, -1f, 1f);
        horizonY = clamp(horizonY, 0.18f, 0.72f);
        nearHalfWidth = clamp(nearHalfWidth, 0.16f, 0.49f);
        laneCenter = clamp(laneCenter, -1f, 1f);

        float fast = 0.23f;
        float slow = 0.12f;

        roadConfidence =
                roadConfidence * 0.72f +
                confidence * 0.28f;

        roadVanishX =
                roadVanishX * (1f - slow) +
                vanishX * slow;

        roadHorizonY =
                roadHorizonY * (1f - slow) +
                horizonY * slow;

        roadNearHalfWidth =
                roadNearHalfWidth * (1f - slow) +
                nearHalfWidth * slow;

        laneCenterOffset =
                laneCenterOffset * (1f - fast) +
                laneCenter * fast;

        roadUpdatedAtMs =
                android.os.SystemClock.elapsedRealtime();
    }

    public void decayRoadConfidence() {
        roadConfidence *= 0.88f;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

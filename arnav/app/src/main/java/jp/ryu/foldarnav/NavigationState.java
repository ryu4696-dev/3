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
        routeConfidence = confidence;
        revision.incrementAndGet();
    }

    public List<RoutePoint> getRoute() { return route.get(); }
    public long getRevision() { return revision.get(); }

    public void updateLaneCenter(float value) {
        value = Math.max(-1f, Math.min(1f, value));
        laneCenterOffset = laneCenterOffset * 0.82f + value * 0.18f;
    }
}

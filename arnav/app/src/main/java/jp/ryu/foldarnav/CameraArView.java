package jp.ryu.foldarnav;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.util.Size;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class CameraArView extends FrameLayout {
    private final Activity activity;
    private final TextureView textureView;
    private final RouteOverlay overlay;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageReader imageReader;

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private Size previewSize;
    private String cameraId;
    private int sensorOrientation = 90;
    private boolean resumed = false;
    private final Semaphore cameraLock = new Semaphore(1);

    public CameraArView(Activity activity) {
        super(activity);
        this.activity = activity;
        setBackgroundColor(Color.BLACK);

        textureView = new TextureView(activity);
        textureView.setSurfaceTextureListener(surfaceTextureListener);

        overlay = new RouteOverlay(activity);
        overlay.setWillNotDraw(false);

        addView(textureView, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(overlay, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void resumeCamera() {
        resumed = true;
        startBackgroundThread();
        if (textureView.isAvailable()) openCamera(textureView.getWidth(), textureView.getHeight());
    }

    public void pauseCamera() {
        resumed = false;
        closeCamera();
        stopBackgroundThread();
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    configureTransform(width, height);
                    if (resumed) openCamera(width, height);
                }

                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    configureTransform(width, height);
                }

                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return true;
                }

                @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                    overlay.postInvalidateOnAnimation();
                }
            };

    private void startBackgroundThread() {
        if (cameraThread != null) return;
        cameraThread = new HandlerThread("FoldARCamera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopBackgroundThread() {
        HandlerThread t = cameraThread;
        cameraThread = null;
        cameraHandler = null;
        if (t != null) {
            t.quitSafely();
            try { t.join(700); } catch (InterruptedException ignored) {}
        }
    }

    private void openCamera(int width, int height) {
        if (!resumed || cameraHandler == null) return;
        if (activity.checkSelfPermission(android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) return;
        if (cameraDevice != null) return;

        try {
            CameraManager manager = activity.getSystemService(CameraManager.class);
            chooseCamera(manager, width, height);
            if (cameraId == null || previewSize == null) {
                showError("背面カメラを取得できません");
                return;
            }

            if (!cameraLock.tryAcquire(1500, TimeUnit.MILLISECONDS)) {
                showError("カメラ初期化がタイムアウトしました");
                return;
            }

            manager.openCamera(cameraId, stateCallback, cameraHandler);
        } catch (Exception e) {
            cameraLock.release();
            showError("カメラ開始失敗: " + e.getClass().getSimpleName());
        }
    }

    private void chooseCamera(CameraManager manager, int viewW, int viewH) throws CameraAccessException {
        cameraId = null;
        previewSize = null;

        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;

            Integer so = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (so != null) sensorOrientation = so;

            StreamConfigurationMap map =
                    c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) continue;

            Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
            if (sizes == null || sizes.length == 0) continue;

            previewSize = choosePreviewSize(sizes, viewW, viewH);
            cameraId = id;
            break;
        }
    }

    private static Size choosePreviewSize(Size[] sizes, int viewW, int viewH) {
        double target = viewH == 0 ? 16.0 / 9.0 : (double)Math.max(viewW, viewH) / Math.max(1, Math.min(viewW, viewH));
        Size best = sizes[0];
        double bestScore = Double.MAX_VALUE;

        for (Size s : sizes) {
            long area = (long)s.getWidth() * s.getHeight();
            if (area > 4000L * 3000L) continue;

            double ar = (double)Math.max(s.getWidth(), s.getHeight()) / Math.min(s.getWidth(), s.getHeight());
            double score = Math.abs(ar - target) * 10.0;
            score += Math.abs(area - 1280L * 720L) / (1280.0 * 720.0);

            if (score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice camera) {
            cameraLock.release();
            cameraDevice = camera;
            createCameraSession();
        }

        @Override public void onDisconnected(CameraDevice camera) {
            cameraLock.release();
            camera.close();
            cameraDevice = null;
            showError("カメラが切断されました");
        }

        @Override public void onError(CameraDevice camera, int error) {
            cameraLock.release();
            camera.close();
            cameraDevice = null;
            showError("カメラエラー: " + error);
        }
    };

    private void createCameraSession() {
        try {
            SurfaceTexture st = textureView.getSurfaceTexture();
            if (st == null || cameraDevice == null || previewSize == null) return;

            st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(st);

            Size analysis = chooseAnalysisSize();
            imageReader = ImageReader.newInstance(
                    analysis.getWidth(), analysis.getHeight(),
                    android.graphics.ImageFormat.YUV_420_888, 2);

            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) RoadVision.analyze(image);
                } catch (Throwable ignored) {
                } finally {
                    if (image != null) image.close();
                }
            }, cameraHandler);

            previewRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.addTarget(imageReader.getSurface());
            previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);

            List<Surface> outputs = Arrays.asList(previewSurface, imageReader.getSurface());

            cameraDevice.createCaptureSession(outputs,
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession session) {
                            if (cameraDevice == null) return;
                            captureSession = session;
                            try {
                                session.setRepeatingRequest(
                                        previewRequestBuilder.build(),
                                        null,
                                        cameraHandler);
                                activity.runOnUiThread(() -> {
                                    configureTransform(textureView.getWidth(), textureView.getHeight());
                                    overlay.invalidate();
                                });
                            } catch (CameraAccessException e) {
                                showError("カメラプレビュー開始失敗");
                            }
                        }

                        @Override public void onConfigureFailed(CameraCaptureSession session) {
                            showError("カメラセッション構成に失敗しました");
                        }
                    }, cameraHandler);

        } catch (Exception e) {
            showError("カメラセッション失敗: " + e.getClass().getSimpleName());
        }
    }

    private Size chooseAnalysisSize() {
        return new Size(640, 480);
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (previewSize == null || viewWidth <= 0 || viewHeight <= 0) return;

        int rotation = activity.getDisplay() != null
                ? activity.getDisplay().getRotation()
                : Surface.ROTATION_0;

        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(
                0, 0,
                previewSize.getHeight(),
                previewSize.getWidth());

        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            bufferRect.offset(
                    centerX - bufferRect.centerX(),
                    centerY - bufferRect.centerY());

            matrix.setRectToRect(
                    viewRect,
                    bufferRect,
                    Matrix.ScaleToFit.FILL);

            float scale = Math.max(
                    (float)viewHeight / previewSize.getHeight(),
                    (float)viewWidth / previewSize.getWidth());

            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(
                    90f * (rotation - 2),
                    centerX, centerY);
        } else {
            int degrees = rotation == Surface.ROTATION_180 ? 180 : 0;
            matrix.postRotate(degrees, centerX, centerY);

            float srcW = previewSize.getWidth();
            float srcH = previewSize.getHeight();
            float scale = Math.max(viewWidth / srcW, viewHeight / srcH);
            matrix.postScale(scale, scale, centerX, centerY);
        }

        textureView.setTransform(matrix);
    }

    private void closeCamera() {
        try {
            if (!cameraLock.tryAcquire(1200, TimeUnit.MILLISECONDS)) return;

            if (captureSession != null) {
                try { captureSession.stopRepeating(); } catch (Exception ignored) {}
                captureSession.close();
                captureSession = null;
            }

            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }

            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (InterruptedException ignored) {
        } finally {
            if (cameraLock.availablePermits() == 0) cameraLock.release();
        }
    }

    private void showError(String message) {
        activity.runOnUiThread(() ->
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }

    private static final class RouteOverlay extends View {
        private final Paint glowOuter = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowMid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ribbon = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chevronGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chevron = new Paint(Paint.ANTI_ALIAS_FLAG);

        RouteOverlay(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);

            glowOuter.setStyle(Paint.Style.FILL);
            glowOuter.setColor(Color.argb(28, 70, 210, 255));

            glowMid.setStyle(Paint.Style.FILL);
            glowMid.setColor(Color.argb(58, 35, 170, 255));

            ribbon.setStyle(Paint.Style.FILL);

            core.setStyle(Paint.Style.STROKE);
            core.setStrokeCap(Paint.Cap.ROUND);
            core.setStrokeJoin(Paint.Join.ROUND);
            core.setColor(Color.argb(175, 210, 245, 255));

            chevronGlow.setStyle(Paint.Style.STROKE);
            chevronGlow.setStrokeCap(Paint.Cap.ROUND);
            chevronGlow.setStrokeJoin(Paint.Join.ROUND);
            chevronGlow.setColor(Color.argb(72, 25, 170, 255));

            chevron.setStyle(Paint.Style.STROKE);
            chevron.setStrokeCap(Paint.Cap.ROUND);
            chevron.setStrokeJoin(Paint.Join.ROUND);
            chevron.setColor(Color.argb(245, 105, 215, 255));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            final NavigationState nav = NavigationState.get();
            final List<NavigationState.RoutePoint> routePoints = nav.getRoute();

            if (routePoints == null || routePoints.size() < 6) return;
            if (nav.routeConfidence < 0.10f) return;

            final float w = getWidth();
            final float h = getHeight();
            if (w < 80f || h < 120f) return;

            long now = SystemClock.elapsedRealtime();

            float roadConfidence = nav.roadConfidence;
            if (now - nav.roadUpdatedAtMs > 2200L) {
                roadConfidence *= 0.30f;
            }

            boolean moving = nav.speedMps > 0.80f;

            // If the camera does not look like a road and the vehicle is not moving,
            // do not paste a route onto walls / ceilings / furniture.
            if (!moving && roadConfidence < 0.15f) return;

            float roadMix = clamp(
                    (roadConfidence - 0.08f) / 0.42f,
                    0f,
                    1f);

            float horizonNorm = lerp(
                    0.43f,
                    clamp(nav.roadHorizonY, 0.27f, 0.64f),
                    roadMix);

            float pitch = clamp(
                    nav.imuPitchDeg / 90f,
                    -0.55f,
                    0.55f);

            float horizonY =
                    h * horizonNorm +
                    pitch * h * 0.045f;

            horizonY = clamp(
                    horizonY,
                    h * 0.25f,
                    h * 0.66f);

            float bottomY = h * 0.965f;

            float vanishX =
                    w * 0.5f +
                    nav.roadVanishX * w * 0.48f * roadMix;

            float anchorX =
                    w * 0.5f +
                    nav.laneCenterOffset * w * 0.11f * roadMix;

            float roll = clamp(
                    nav.imuRollDeg / 45f,
                    -0.85f,
                    0.85f);

            ArrayList<PointF> centers = new ArrayList<>();
            ArrayList<Float> halfWidths = new ArrayList<>();

            float startLat = routePoints.get(0).lateral;
            float previousCurve = 0f;

            for (NavigationState.RoutePoint rp : routePoints) {
                float t = clamp(rp.progress, 0f, 1f);

                // Perspective: large separation near the car, compressed at the horizon.
                float p =
                        1f -
                        (float)Math.pow(1f - t, 1.62f);

                float y =
                        bottomY -
                        (bottomY - horizonY) * p;

                float baseX =
                        lerp(anchorX, vanishX, p);

                float rawCurve =
                        rp.lateral - startLat;

                float curve =
                        previousCurve * 0.42f +
                        rawCurve * 0.58f;

                previousCurve = curve;

                float curveScale =
                        w * (0.055f + 0.285f * p);

                float x =
                        baseX +
                        curve * curveScale;

                x +=
                        roll *
                        (bottomY - y) *
                        0.070f;

                centers.add(new PointF(x, y));

                float nearRoadHalf =
                        clamp(
                                nav.roadNearHalfWidth * w * 0.30f,
                                w * 0.050f,
                                w * 0.115f);

                float nearHalf =
                        lerp(w * 0.085f, nearRoadHalf, roadMix);

                float farHalf =
                        Math.max(3.5f, w * 0.009f);

                float hw =
                        lerp(
                                nearHalf,
                                farHalf,
                                (float)Math.pow(p, 0.90f));

                halfWidths.add(hw);
            }

            if (centers.size() < 5) return;

            Path outer =
                    buildRibbon(
                            centers,
                            halfWidths,
                            2.15f);

            Path mid =
                    buildRibbon(
                            centers,
                            halfWidths,
                            1.52f);

            Path main =
                    buildRibbon(
                            centers,
                            halfWidths,
                            1.00f);

            float visibility =
                    moving
                            ? clamp(0.72f + roadConfidence * 0.38f, 0.72f, 1f)
                            : clamp(0.55f + roadConfidence * 0.72f, 0.55f, 1f);

            glowOuter.setAlpha((int)(34f * visibility));
            glowMid.setAlpha((int)(70f * visibility));

            ribbon.setAlpha((int)(210f * visibility));
            ribbon.setShader(
                    new LinearGradient(
                            0f,
                            bottomY,
                            0f,
                            horizonY,
                            new int[]{
                                    Color.argb(178, 50, 210, 255),
                                    Color.argb(205, 25, 145, 255),
                                    Color.argb(232, 70, 115, 255)
                            },
                            new float[]{0f, 0.52f, 1f},
                            Shader.TileMode.CLAMP));

            canvas.drawPath(outer, glowOuter);
            canvas.drawPath(mid, glowMid);
            canvas.drawPath(main, ribbon);

            ribbon.setShader(null);

            Path centerLine = buildSmoothCenterline(centers);
            core.setStrokeWidth(
                    Math.max(2.0f, w * 0.0065f));
            core.setAlpha((int)(145f * visibility));
            canvas.drawPath(centerLine, core);

            drawChevrons(
                    canvas,
                    centers,
                    halfWidths,
                    visibility);
        }

        private void drawChevrons(
                Canvas canvas,
                ArrayList<PointF> centers,
                ArrayList<Float> halfWidths,
                float visibility) {

            if (centers.size() < 8) return;

            float[] fractions = {
                    0.17f,
                    0.30f,
                    0.43f,
                    0.57f,
                    0.70f
            };

            for (int n = 0; n < fractions.length; n++) {
                int idx =
                        clamp(
                                Math.round(
                                        fractions[n] *
                                        (centers.size() - 1)),
                                2,
                                centers.size() - 3);

                PointF prev = centers.get(idx - 1);
                PointF c = centers.get(idx);
                PointF next = centers.get(idx + 1);

                float dx = next.x - prev.x;
                float dy = next.y - prev.y;
                float len =
                        (float)Math.sqrt(
                                dx * dx +
                                dy * dy);

                if (len < 1f) continue;

                dx /= len;
                dy /= len;

                float nx = -dy;
                float ny = dx;

                float size =
                        Math.max(
                                halfWidths.get(idx) * 2.0f,
                                getWidth() * 0.030f);

                float tipX =
                        c.x + dx * size * 0.80f;
                float tipY =
                        c.y + dy * size * 0.80f;

                float backX =
                        c.x - dx * size * 0.62f;
                float backY =
                        c.y - dy * size * 0.62f;

                float wing =
                        size * 0.82f;

                Path v = new Path();
                v.moveTo(
                        backX + nx * wing,
                        backY + ny * wing);
                v.lineTo(tipX, tipY);
                v.lineTo(
                        backX - nx * wing,
                        backY - ny * wing);

                chevronGlow.setStrokeWidth(
                        Math.max(
                                9f,
                                size * 0.46f));

                chevron.setStrokeWidth(
                        Math.max(
                                4f,
                                size * 0.23f));

                chevronGlow.setAlpha(
                        (int)(90f * visibility));

                chevron.setAlpha(
                        (int)(245f * visibility));

                canvas.drawPath(v, chevronGlow);
                canvas.drawPath(v, chevron);
            }
        }

        private static Path buildSmoothCenterline(
                ArrayList<PointF> pts) {

            Path p = new Path();
            if (pts.isEmpty()) return p;

            p.moveTo(
                    pts.get(0).x,
                    pts.get(0).y);

            for (int i = 1; i < pts.size() - 1; i++) {
                PointF a = pts.get(i);
                PointF b = pts.get(i + 1);

                float mx =
                        (a.x + b.x) * 0.5f;
                float my =
                        (a.y + b.y) * 0.5f;

                p.quadTo(
                        a.x,
                        a.y,
                        mx,
                        my);
            }

            PointF last =
                    pts.get(pts.size() - 1);

            p.lineTo(last.x, last.y);

            return p;
        }

        private static Path buildRibbon(
                ArrayList<PointF> centers,
                ArrayList<Float> halfWidths,
                float widthScale) {

            int n = centers.size();

            ArrayList<PointF> left =
                    new ArrayList<>(n);

            ArrayList<PointF> right =
                    new ArrayList<>(n);

            for (int i = 0; i < n; i++) {
                PointF prev =
                        centers.get(
                                Math.max(0, i - 1));

                PointF next =
                        centers.get(
                                Math.min(n - 1, i + 1));

                float dx =
                        next.x - prev.x;

                float dy =
                        next.y - prev.y;

                float len =
                        (float)Math.sqrt(
                                dx * dx +
                                dy * dy);

                if (len < 0.001f) {
                    dx = 0f;
                    dy = -1f;
                    len = 1f;
                }

                dx /= len;
                dy /= len;

                float nx = -dy;
                float ny = dx;

                float hw =
                        halfWidths.get(i) *
                        widthScale;

                PointF c = centers.get(i);

                left.add(
                        new PointF(
                                c.x + nx * hw,
                                c.y + ny * hw));

                right.add(
                        new PointF(
                                c.x - nx * hw,
                                c.y - ny * hw));
            }

            Path path = new Path();

            PointF first = left.get(0);
            path.moveTo(first.x, first.y);

            for (int i = 1; i < left.size(); i++) {
                PointF p = left.get(i);
                path.lineTo(p.x, p.y);
            }

            for (int i = right.size() - 1; i >= 0; i--) {
                PointF p = right.get(i);
                path.lineTo(p.x, p.y);
            }

            path.close();

            return path;
        }

        private static float lerp(
                float a,
                float b,
                float t) {

            return a + (b - a) * t;
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
}

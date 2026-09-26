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
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint route = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);

        RouteOverlay(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);

            outline.setColor(Color.argb(205, 15, 20, 38));
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeCap(Paint.Cap.ROUND);
            outline.setStrokeJoin(Paint.Join.ROUND);

            route.setColor(Color.argb(225, 45, 115, 255));
            route.setStyle(Paint.Style.STROKE);
            route.setStrokeCap(Paint.Cap.ROUND);
            route.setStrokeJoin(Paint.Join.ROUND);

            arrow.setColor(Color.argb(240, 170, 215, 255));
            arrow.setStyle(Paint.Style.FILL);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            NavigationState nav = NavigationState.get();
            List<NavigationState.RoutePoint> points = nav.getRoute();
            if (points == null || points.size() < 4) return;

            float w = getWidth();
            float h = getHeight();
            if (w < 10 || h < 10) return;

            float roll = clamp(nav.imuRollDeg / 45f, -0.8f, 0.8f);
            float pitch = clamp(nav.imuPitchDeg / 60f, -0.45f, 0.45f);
            float lane = clamp(nav.laneCenterOffset, -0.7f, 0.7f);

            Path p = new Path();
            boolean first = true;

            ArrayList<PointF> screen = new ArrayList<>();

            for (NavigationState.RoutePoint rp : points) {
                float t = clamp(rp.progress, 0f, 1f);
                float perspective = (float)Math.pow(t, 0.72);

                float y = h * (0.90f - perspective * 0.66f);
                y += pitch * h * (0.05f + 0.05f * t);

                float spread = w * (0.42f - 0.22f * t);
                float x = w * 0.5f +
                        rp.lateral * spread +
                        lane * w * 0.12f;

                x += roll * (h * 0.55f - y) * 0.20f;

                screen.add(new PointF(x, y));

                if (first) {
                    p.moveTo(x, y);
                    first = false;
                } else {
                    p.lineTo(x, y);
                }
            }

            float base = Math.max(16f, w * 0.055f);
            outline.setStrokeWidth(base + Math.max(8f, w * 0.020f));
            route.setStrokeWidth(base);

            canvas.drawPath(p, outline);
            canvas.drawPath(p, route);

            if (screen.size() >= 7) {
                int idx = Math.min(screen.size() - 2, Math.max(3, screen.size() / 3));
                PointF a = screen.get(idx - 1);
                PointF b = screen.get(idx + 1);
                drawArrow(canvas, a, b, Math.max(18f, w * 0.05f));
            }
        }

        private void drawArrow(Canvas c, PointF a, PointF b, float size) {
            float dx = b.x - a.x;
            float dy = b.y - a.y;
            float len = (float)Math.sqrt(dx * dx + dy * dy);
            if (len < 1f) return;

            dx /= len;
            dy /= len;

            float px = -dy;
            float py = dx;

            float tx = b.x;
            float ty = b.y;

            Path tri = new Path();
            tri.moveTo(tx + dx * size, ty + dy * size);
            tri.lineTo(tx - dx * size * 0.8f + px * size * 0.7f,
                    ty - dy * size * 0.8f + py * size * 0.7f);
            tri.lineTo(tx - dx * size * 0.8f - px * size * 0.7f,
                    ty - dy * size * 0.8f - py * size * 0.7f);
            tri.close();

            c.drawPath(tri, arrow);
        }

        private static float clamp(float v, float lo, float hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }
}

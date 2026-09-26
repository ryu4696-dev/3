package jp.ryu.foldarnav;

import android.app.Activity;
import android.graphics.Color;
import android.media.Image;
import android.opengl.*;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import com.google.ar.core.*;
import com.google.ar.core.exceptions.*;

import java.nio.*;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ArNavigationView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private static final String TAG = "FoldARNav";

    private final Activity activity;
    private Session session;

    private boolean sessionResumed = false;
    private boolean textureNamesSet = false;
    private boolean viewportChanged = true;
    private boolean renderErrorShown = false;

    private int viewportW = 1, viewportH = 1;
    private int cameraTex = -1;

    private int bgProgram = 0, routeProgram = 0;
    private int bgPosLoc, bgUvLoc, bgTexLoc;
    private int routePosLoc, routeMvpLoc;

    private final float[] quad = {-1f,-1f, 1f,-1f, -1f,1f, 1f,1f};
    private final float[] uvs = new float[8];

    private final FloatBuffer quadBuffer;
    private final FloatBuffer uvBuffer;
    private FloatBuffer routeBuffer;
    private int routeVertexCount = 0;

    private long lastRevision = -1;
    private long lastAnchorMs = 0;
    private Pose basePose;
    private float groundY;
    private final float[] baseForward = new float[3];
    private final float[] baseRight = new float[3];
    private int frameCounter = 0;

    private final float[] view = new float[16];
    private final float[] proj = new float[16];
    private final float[] mvp = new float[16];

    public ArNavigationView(Activity activity) {
        super(activity);
        this.activity = activity;

        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setBackgroundColor(Color.BLACK);

        quadBuffer = buffer(quad);
        uvBuffer = buffer(new float[8]);
    }

    public synchronized void resumeAr() {
        try {
            if (session == null) {
                session = new Session(activity);

                Config c = new Config(session);
                c.setUpdateMode(Config.UpdateMode.BLOCKING);
                c.setFocusMode(Config.FocusMode.AUTO);

                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    c.setDepthMode(Config.DepthMode.AUTOMATIC);
                }

                session.configure(c);
            }

            if (!sessionResumed) {
                session.resume();
                sessionResumed = true;
                textureNamesSet = false;
            }

            super.onResume();
        } catch (SessionNotPausedException e) {
            sessionResumed = true;
            super.onResume();
        } catch (Exception e) {
            Log.e(TAG, "AR resume failed", e);
            activity.runOnUiThread(() ->
                    android.widget.Toast.makeText(
                            activity,
                            "AR開始失敗: " + e.getClass().getSimpleName(),
                            android.widget.Toast.LENGTH_LONG
                    ).show());
        }
    }

    public synchronized void pauseAr() {
        super.onPause();

        if (session != null && sessionResumed) {
            try {
                session.pause();
            } catch (Exception e) {
                Log.w(TAG, "AR pause failed", e);
            }
        }

        sessionResumed = false;
        textureNamesSet = false;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        cameraTex = createExternalTexture();
        textureNamesSet = false;

        bgProgram = linkChecked(BG_VS, BG_FS, "camera");
        bgPosLoc = GLES20.glGetAttribLocation(bgProgram, "aPos");
        bgUvLoc = GLES20.glGetAttribLocation(bgProgram, "aUv");
        bgTexLoc = GLES20.glGetUniformLocation(bgProgram, "uTex");

        routeProgram = linkChecked(ROUTE_VS, ROUTE_FS, "route");
        routePosLoc = GLES20.glGetAttribLocation(routeProgram, "aPos");
        routeMvpLoc = GLES20.glGetUniformLocation(routeProgram, "uMvp");

        Log.i(TAG, "GL surface created. cameraTex=" + cameraTex);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewportW = Math.max(1, width);
        viewportH = Math.max(1, height);
        viewportChanged = true;
        GLES20.glViewport(0, 0, viewportW, viewportH);

        Log.i(TAG, "GL surface changed " + viewportW + "x" + viewportH);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        final Session s = session;
        if (s == null || !sessionResumed || cameraTex <= 0) return;

        try {
            if (viewportChanged) {
                int rotation = activity.getDisplay() != null
                        ? activity.getDisplay().getRotation()
                        : Surface.ROTATION_0;

                s.setDisplayGeometry(rotation, viewportW, viewportH);
                viewportChanged = false;
            }

            // ARCore official sample style:
            // Set texture names from the GL thread, and only when needed.
            if (!textureNamesSet) {
                s.setCameraTextureNames(new int[]{cameraTex});
                textureNamesSet = true;
                Log.i(TAG, "Camera texture attached to ARCore: " + cameraTex);
            }

            Frame frame = s.update();

            // Timestamp 0 is normal only during camera startup.
            if (frame.getTimestamp() == 0L) return;

            drawCamera(frame, frame.getCameraTextureName());

            Camera camera = frame.getCamera();
            if (camera.getTrackingState() != TrackingState.TRACKING) return;

            frameCounter++;
            if (frameCounter % 8 == 0) {
                Image image = null;
                try {
                    image = frame.acquireCameraImage();
                    RoadVision.analyze(image);
                } catch (NotYetAvailableException ignored) {
                } finally {
                    if (image != null) image.close();
                }
            }

            NavigationState nav = NavigationState.get();
            long rev = nav.getRevision();
            long now = SystemClock.elapsedRealtime();

            if (rev != lastRevision || basePose == null || now - lastAnchorMs > 1400) {
                rebuildRoute(camera.getPose(), nav.getRoute(), nav.laneCenterOffset);
                lastRevision = rev;
                lastAnchorMs = now;
            }

            if (routeVertexCount >= 4) {
                camera.getViewMatrix(view, 0);
                camera.getProjectionMatrix(proj, 0, 0.05f, 110f);
                Matrix.multiplyMM(mvp, 0, proj, 0, view, 0);
                drawRoute();
            }

        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera unavailable while rendering", e);
            showRenderErrorOnce("カメラを利用できません");
        } catch (SessionPausedException e) {
            Log.w(TAG, "Frame requested while AR session paused", e);
        } catch (MissingGlContextException e) {
            Log.e(TAG, "Missing GL context", e);
            showRenderErrorOnce("AR描画コンテキストの初期化に失敗しました");
        } catch (Throwable e) {
            Log.e(TAG, "AR render failure", e);
            showRenderErrorOnce("AR描画エラー: " + e.getClass().getSimpleName());
        }
    }

    private void showRenderErrorOnce(String message) {
        if (renderErrorShown) return;
        renderErrorShown = true;

        activity.runOnUiThread(() ->
                android.widget.Toast.makeText(
                        activity,
                        message,
                        android.widget.Toast.LENGTH_LONG
                ).show());
    }

    private void rebuildRoute(
            Pose cam,
            List<NavigationState.RoutePoint> path,
            float laneOffset
    ) {
        if (path == null || path.size() < 6) {
            routeVertexCount = 0;
            return;
        }

        basePose = cam;
        groundY = cam.ty() - 1.25f;

        float[] f = cam.rotateVector(new float[]{0f, 0f, -1f});
        float[] r = cam.rotateVector(new float[]{1f, 0f, 0f});

        f[1] = 0f;
        r[1] = 0f;

        normalizeXZ(f);
        normalizeXZ(r);

        System.arraycopy(f, 0, baseForward, 0, 3);
        System.arraycopy(r, 0, baseRight, 0, 3);

        int n = Math.min(path.size(), 32);
        float[] vertices = new float[n * 2 * 3];
        int vi = 0;

        for (int i = 0; i < n; i++) {
            NavigationState.RoutePoint p =
                    path.get(i * path.size() / n);

            float t = Math.max(0f, Math.min(1f, p.progress));
            float depth =
                    3.5f + (float)Math.pow(t, 1.45) * 72f;

            float lateralScale =
                    1.1f + depth * 0.075f;

            float lateral =
                    p.lateral * lateralScale +
                    laneOffset * 0.9f;

            float halfWidth =
                    0.32f +
                    Math.min(0.22f, depth * 0.0025f);

            float cx =
                    cam.tx() +
                    baseForward[0] * depth +
                    baseRight[0] * lateral;

            float cz =
                    cam.tz() +
                    baseForward[2] * depth +
                    baseRight[2] * lateral;

            vertices[vi++] =
                    cx - baseRight[0] * halfWidth;
            vertices[vi++] =
                    groundY;
            vertices[vi++] =
                    cz - baseRight[2] * halfWidth;

            vertices[vi++] =
                    cx + baseRight[0] * halfWidth;
            vertices[vi++] =
                    groundY;
            vertices[vi++] =
                    cz + baseRight[2] * halfWidth;
        }

        routeBuffer = buffer(vertices);
        routeVertexCount = n * 2;
    }

    private void drawCamera(Frame frame, int textureId) {
        frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                quad,
                Coordinates2d.TEXTURE_NORMALIZED,
                uvs
        );

        uvBuffer.position(0);
        uvBuffer.put(uvs);
        uvBuffer.position(0);

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);

        GLES20.glUseProgram(bgProgram);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                textureId
        );

        GLES20.glUniform1i(bgTexLoc, 0);

        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(bgPosLoc);
        GLES20.glVertexAttribPointer(
                bgPosLoc,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                quadBuffer
        );

        uvBuffer.position(0);
        GLES20.glEnableVertexAttribArray(bgUvLoc);
        GLES20.glVertexAttribPointer(
                bgUvLoc,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                uvBuffer
        );

        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLE_STRIP,
                0,
                4
        );

        GLES20.glDisableVertexAttribArray(bgPosLoc);
        GLES20.glDisableVertexAttribArray(bgUvLoc);

        GLES20.glBindTexture(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                0
        );

        GLES20.glDepthMask(true);
    }

    private void drawRoute() {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(
                GLES20.GL_SRC_ALPHA,
                GLES20.GL_ONE_MINUS_SRC_ALPHA
        );

        GLES20.glUseProgram(routeProgram);
        GLES20.glUniformMatrix4fv(
                routeMvpLoc,
                1,
                false,
                mvp,
                0
        );

        routeBuffer.position(0);
        GLES20.glEnableVertexAttribArray(routePosLoc);
        GLES20.glVertexAttribPointer(
                routePosLoc,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                routeBuffer
        );

        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLE_STRIP,
                0,
                routeVertexCount
        );

        GLES20.glDisableVertexAttribArray(routePosLoc);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDepthMask(true);
    }

    private static int createExternalTexture() {
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);

        GLES20.glBindTexture(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                t[0]
        );

        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
        );

        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
        );

        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
        );

        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        );

        GLES20.glBindTexture(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                0
        );

        return t[0];
    }

    private static void normalizeXZ(float[] v) {
        float len =
                (float)Math.sqrt(
                        v[0] * v[0] +
                        v[2] * v[2]
                );

        if (len < 0.001f) {
            v[0] = 0f;
            v[2] = -1f;
            return;
        }

        v[0] /= len;
        v[2] /= len;
    }

    private static FloatBuffer buffer(float[] a) {
        ByteBuffer bb =
                ByteBuffer.allocateDirect(a.length * 4)
                        .order(ByteOrder.nativeOrder());

        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(a);
        fb.position(0);

        return fb;
    }

    private static int compileShader(
            int type,
            String source,
            String label
    ) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] ok = new int[1];
        GLES20.glGetShaderiv(
                shader,
                GLES20.GL_COMPILE_STATUS,
                ok,
                0
        );

        if (ok[0] == 0) {
            String log =
                    GLES20.glGetShaderInfoLog(shader);

            GLES20.glDeleteShader(shader);

            throw new RuntimeException(
                    label + " shader compile failed: " + log
            );
        }

        return shader;
    }

    private static int linkChecked(
            String vs,
            String fs,
            String label
    ) {
        int v =
                compileShader(
                        GLES20.GL_VERTEX_SHADER,
                        vs,
                        label + "-vs"
                );

        int f =
                compileShader(
                        GLES20.GL_FRAGMENT_SHADER,
                        fs,
                        label + "-fs"
                );

        int p = GLES20.glCreateProgram();

        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);

        int[] ok = new int[1];
        GLES20.glGetProgramiv(
                p,
                GLES20.GL_LINK_STATUS,
                ok,
                0
        );

        String log =
                GLES20.glGetProgramInfoLog(p);

        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);

        if (ok[0] == 0) {
            GLES20.glDeleteProgram(p);

            throw new RuntimeException(
                    label + " program link failed: " + log
            );
        }

        return p;
    }

    private static final String BG_VS =
            "attribute vec2 aPos;\n" +
            "attribute vec2 aUv;\n" +
            "varying vec2 vUv;\n" +
            "void main(){\n" +
            "  vUv=aUv;\n" +
            "  gl_Position=vec4(aPos,0.0,1.0);\n" +
            "}";

    private static final String BG_FS =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES uTex;\n" +
            "varying vec2 vUv;\n" +
            "void main(){\n" +
            "  gl_FragColor=texture2D(uTex,vUv);\n" +
            "}";

    private static final String ROUTE_VS =
            "uniform mat4 uMvp;\n" +
            "attribute vec3 aPos;\n" +
            "void main(){\n" +
            "  gl_Position=uMvp*vec4(aPos,1.0);\n" +
            "}";

    private static final String ROUTE_FS =
            "precision mediump float;\n" +
            "void main(){\n" +
            "  gl_FragColor=vec4(0.10,0.55,1.0,0.78);\n" +
            "}";
}

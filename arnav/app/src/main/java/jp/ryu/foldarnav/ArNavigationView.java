package jp.ryu.foldarnav;

import android.app.Activity;
import android.graphics.Color;
import android.media.Image;
import android.opengl.*;
import android.os.SystemClock;
import android.view.Surface;
import com.google.ar.core.*;
import com.google.ar.core.exceptions.*;

import java.nio.*;
import java.util.List;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ArNavigationView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private final Activity activity;
    private Session session;
    private boolean viewportChanged = true;
    private int viewportW = 1, viewportH = 1;
    private int cameraTex = -1;
    private int bgProgram = 0, routeProgram = 0;
    private int bgPosLoc, bgUvLoc, bgTexLoc;
    private int routePosLoc, routeMvpLoc;

    private final float[] quad = {-1f,-1f, 1f,-1f, -1f,1f, 1f,1f};
    private final float[] uvs = new float[8];
    private FloatBuffer quadBuffer;
    private FloatBuffer uvBuffer;
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
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setPreserveEGLContextOnPause(true);
        setBackgroundColor(Color.BLACK);
        quadBuffer = buffer(quad);
        uvBuffer = buffer(new float[8]);
    }

    public void resumeAr() {
        queueEvent(() -> {});
        try {
            if (session == null) {
                session = new Session(activity);
                Config c = new Config(session);
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    c.setDepthMode(Config.DepthMode.AUTOMATIC);
                }
                c.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                session.configure(c);
            }
            session.resume();
            onResume();
        } catch (Exception e) {
            activity.runOnUiThread(() ->
                    android.widget.Toast.makeText(activity,
                            "AR開始失敗: " + e.getClass().getSimpleName(),
                            android.widget.Toast.LENGTH_LONG).show());
        }
    }

    public void pauseAr() {
        super.onPause();
        if (session != null) session.pause();
    }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f,0f,0f,1f);
        cameraTex = createExternalTexture();
        bgProgram = link(BG_VS, BG_FS);
        bgPosLoc = GLES20.glGetAttribLocation(bgProgram, "aPos");
        bgUvLoc = GLES20.glGetAttribLocation(bgProgram, "aUv");
        bgTexLoc = GLES20.glGetUniformLocation(bgProgram, "uTex");

        routeProgram = link(ROUTE_VS, ROUTE_FS);
        routePosLoc = GLES20.glGetAttribLocation(routeProgram, "aPos");
        routeMvpLoc = GLES20.glGetUniformLocation(routeProgram, "uMvp");

        if (session != null) session.setCameraTextureName(cameraTex);
    }

    @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewportW = Math.max(1, width);
        viewportH = Math.max(1, height);
        viewportChanged = true;
        GLES20.glViewport(0,0,viewportW,viewportH);
    }

    @Override public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        Session s = session;
        if (s == null || cameraTex < 0) return;

        try {
            if (viewportChanged) {
                int rotation = activity.getDisplay() != null
                        ? activity.getDisplay().getRotation() : Surface.ROTATION_0;
                s.setDisplayGeometry(rotation, viewportW, viewportH);
                viewportChanged = false;
            }

            s.setCameraTextureName(cameraTex);
            Frame frame = s.update();
            drawCamera(frame);

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
                camera.getViewMatrix(view,0);
                camera.getProjectionMatrix(proj,0,0.05f,110f);
                Matrix.multiplyMM(mvp,0,proj,0,view,0);
                drawRoute();
            }
        } catch (CameraNotAvailableException e) {
            activity.runOnUiThread(() ->
                    android.widget.Toast.makeText(activity,
                            "カメラを利用できません", android.widget.Toast.LENGTH_SHORT).show());
        } catch (Throwable ignored) {
        }
    }

    private void rebuildRoute(Pose cam, List<NavigationState.RoutePoint> path, float laneOffset) {
        if (path == null || path.size() < 6) {
            routeVertexCount = 0;
            return;
        }
        basePose = cam;
        groundY = cam.ty() - 1.25f;

        float[] f = cam.rotateVector(new float[]{0f,0f,-1f});
        float[] r = cam.rotateVector(new float[]{1f,0f,0f});
        f[1]=0f; r[1]=0f;
        normalizeXZ(f); normalizeXZ(r);
        System.arraycopy(f,0,baseForward,0,3);
        System.arraycopy(r,0,baseRight,0,3);

        int n = Math.min(path.size(), 32);
        float[] vertices = new float[n * 2 * 3];
        int vi = 0;

        for (int i=0;i<n;i++) {
            NavigationState.RoutePoint p = path.get(i * path.size() / n);
            float t = Math.max(0f, Math.min(1f, p.progress));
            float depth = 3.5f + (float)Math.pow(t, 1.45) * 72f;
            float lateralScale = 1.1f + depth * 0.075f;
            float lateral = p.lateral * lateralScale + laneOffset * 0.9f;
            float halfWidth = 0.32f + Math.min(0.22f, depth * 0.0025f);

            float cx = cam.tx() + baseForward[0]*depth + baseRight[0]*lateral;
            float cz = cam.tz() + baseForward[2]*depth + baseRight[2]*lateral;

            vertices[vi++] = cx - baseRight[0]*halfWidth;
            vertices[vi++] = groundY;
            vertices[vi++] = cz - baseRight[2]*halfWidth;

            vertices[vi++] = cx + baseRight[0]*halfWidth;
            vertices[vi++] = groundY;
            vertices[vi++] = cz + baseRight[2]*halfWidth;
        }

        routeBuffer = buffer(vertices);
        routeVertexCount = n * 2;
    }

    private void drawCamera(Frame frame) {
        frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                quad,
                Coordinates2d.TEXTURE_NORMALIZED,
                uvs);
        uvBuffer.position(0);
        uvBuffer.put(uvs).position(0);

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glUseProgram(bgProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTex);
        GLES20.glUniform1i(bgTexLoc,0);

        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(bgPosLoc);
        GLES20.glVertexAttribPointer(bgPosLoc,2,GLES20.GL_FLOAT,false,0,quadBuffer);

        uvBuffer.position(0);
        GLES20.glEnableVertexAttribArray(bgUvLoc);
        GLES20.glVertexAttribPointer(bgUvLoc,2,GLES20.GL_FLOAT,false,0,uvBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(bgPosLoc);
        GLES20.glDisableVertexAttribArray(bgUvLoc);
    }

    private void drawRoute() {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(routeProgram);
        GLES20.glUniformMatrix4fv(routeMvpLoc,1,false,mvp,0);

        routeBuffer.position(0);
        GLES20.glEnableVertexAttribArray(routePosLoc);
        GLES20.glVertexAttribPointer(routePosLoc,3,GLES20.GL_FLOAT,false,0,routeBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,routeVertexCount);
        GLES20.glDisableVertexAttribArray(routePosLoc);

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDepthMask(true);
    }

    private static int createExternalTexture() {
        int[] t = new int[1];
        GLES20.glGenTextures(1,t,0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,t[0]);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        return t[0];
    }

    private static void normalizeXZ(float[] v) {
        float len = (float)Math.sqrt(v[0]*v[0] + v[2]*v[2]);
        if (len < 0.001f) { v[0]=0f; v[2]=-1f; return; }
        v[0]/=len; v[2]/=len;
    }

    private static FloatBuffer buffer(float[] a) {
        ByteBuffer bb = ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(a).position(0);
        return fb;
    }

    private static int shader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s,src);
        GLES20.glCompileShader(s);
        return s;
    }

    private static int link(String vs, String fs) {
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p,shader(GLES20.GL_VERTEX_SHADER,vs));
        GLES20.glAttachShader(p,shader(GLES20.GL_FRAGMENT_SHADER,fs));
        GLES20.glLinkProgram(p);
        return p;
    }

    private static final String BG_VS =
            "attribute vec2 aPos;\n" +
            "attribute vec2 aUv;\n" +
            "varying vec2 vUv;\n" +
            "void main(){ vUv=aUv; gl_Position=vec4(aPos,0.0,1.0); }";

    private static final String BG_FS =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES uTex;\n" +
            "varying vec2 vUv;\n" +
            "void main(){ gl_FragColor=texture2D(uTex,vUv); }";

    private static final String ROUTE_VS =
            "uniform mat4 uMvp;\n" +
            "attribute vec3 aPos;\n" +
            "void main(){ gl_Position=uMvp*vec4(aPos,1.0); }";

    private static final String ROUTE_FS =
            "precision mediump float;\n" +
            "void main(){ gl_FragColor=vec4(0.10,0.55,1.0,0.78); }";
}

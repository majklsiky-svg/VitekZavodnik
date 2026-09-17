package cz.vitekzavodnik.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Stable OpenGL ES 2.0 renderer designed to run on a broad range of Android devices.
 * It intentionally uses very small, conservative shaders and catches render errors so
 * a driver quirk cannot terminate the whole app.
 */
public final class SafeGameRenderer implements GLSurfaceView.Renderer {
    private static final float DEG = (float) (Math.PI / 180.0);

    private final SharedPreferences prefs;
    private final AudioEngine audio;
    private final List<Item> items = new ArrayList<>();

    private int program;
    private int aPos;
    private int uMvp;
    private int uColor;
    private boolean glReady;
    private SimpleMesh cube;

    private final float[] proj = new float[16];
    private final float[] view = new float[16];
    private final float[] vp = new float[16];
    private final float[] model = new float[16];
    private final float[] mvp = new float[16];

    private volatile float moveX;
    private volatile float moveY;
    private volatile float camDx;
    private volatile float camDy;
    private volatile boolean jumpReq;
    private volatile boolean actionReq;
    private volatile boolean paused = true;

    private float px = -18f;
    private float py = 0f;
    private float pz = 16f;
    private float yaw = 25f;
    private float vY = 0f;
    private float walk = 0f;
    private float camYaw = 35f;
    private float camPitch = 18f;
    private float camDist = 8.5f;
    private boolean grounded = true;
    private boolean inKart = false;
    private float kartSpeed = 0f;
    private float raceTime = 0f;
    private float bestTime = -1f;
    private int stage = 0;
    private int minerals = 0;
    private int tools = 0;
    private int coins = 0;
    private int checkpoint = 0;
    private long lastMs = 0L;
    private long messageUntil = 0L;
    private String message = "";

    private volatile GameSnapshot snapshot = new GameSnapshot(0, 0, 0, 0, false, 0, 8, 0, -1, "Vítej!");

    private static final class Item {
        static final int MINERAL = 0;
        static final int TOOL = 1;
        static final int COIN = 2;
        final int type;
        final float x;
        final float z;
        boolean active = true;
        Item(int type, float x, float z) {
            this.type = type;
            this.x = x;
            this.z = z;
        }
    }

    private static final float[][] GATES = {
            {-12, 10}, {-2, 8}, {10, 5}, {17, -3},
            {12, -14}, {0, -18}, {-13, -12}, {-20, 0}
    };

    public SafeGameRenderer(Context context) {
        Context app = context.getApplicationContext();
        prefs = app.getSharedPreferences("vitek_zavodnik_save", Context.MODE_PRIVATE);
        audio = new AudioEngine(app);
        stage = prefs.getInt("stage", 0);
        coins = prefs.getInt("coins", 0);
        bestTime = prefs.getFloat("best", -1f);
        if (stage >= 1) minerals = 5;
        if (stage >= 2) tools = 3;
        populate();
        show("Najdi 5 minerálů!", 3200);
    }

    private void populate() {
        items.clear();
        float[][] ms = {{-14, 4}, {-7, -5}, {2, -12}, {13, -9}, {16, 10}};
        for (float[] p : ms) items.add(new Item(Item.MINERAL, p[0], p[1]));
        float[][] ts = {{-4, 15}, {8, 13}, {18, 2}};
        for (float[] p : ts) items.add(new Item(Item.TOOL, p[0], p[1]));
        for (int i = 0; i < 24; i++) {
            double a = i * 1.57;
            float r = 6f + (i % 5) * 3.3f;
            items.add(new Item(Item.COIN, (float) Math.cos(a) * r, (float) Math.sin(a) * r));
        }
        if (stage >= 1) for (Item i : items) if (i.type == Item.MINERAL) i.active = false;
        if (stage >= 2) for (Item i : items) if (i.type == Item.TOOL) i.active = false;
        updateSnapshot(SystemClock.uptimeMillis());
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
            GLES20.glClearColor(0.52f, 0.78f, 0.95f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            program = buildProgram(VS, FS);
            if (program == 0) {
                glReady = false;
                return;
            }
            aPos = GLES20.glGetAttribLocation(program, "aPosition");
            uMvp = GLES20.glGetUniformLocation(program, "uMVP");
            uColor = GLES20.glGetUniformLocation(program, "uColor");
            cube = SimpleMesh.cube();
            glReady = aPos >= 0 && uMvp >= 0 && uColor >= 0;
            lastMs = SystemClock.uptimeMillis();
        } catch (Throwable ignored) {
            glReady = false;
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        try {
            int h = Math.max(1, height);
            GLES20.glViewport(0, 0, width, h);
            Matrix.perspectiveM(proj, 0, 56f, (float) width / h, 0.1f, 100f);
        } catch (Throwable ignored) {
            glReady = false;
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        try {
            long now = SystemClock.uptimeMillis();
            if (lastMs == 0L) lastMs = now;
            float dt = Math.min(0.033f, Math.max(0.001f, (now - lastMs) / 1000f));
            lastMs = now;
            if (!paused) update(dt, now);

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (!glReady || program == 0 || cube == null) return;

            GLES20.glUseProgram(program);
            float cr = camYaw * DEG;
            float pr = camPitch * DEG;
            float targetY = py + (inKart ? 1.0f : 1.7f);
            float ex = px - (float) Math.sin(cr) * camDist * (float) Math.cos(pr);
            float ez = pz - (float) Math.cos(cr) * camDist * (float) Math.cos(pr);
            float ey = targetY + camDist * (float) Math.sin(pr);
            Matrix.setLookAtM(view, 0, ex, ey, ez, px, targetY, pz, 0, 1, 0);
            Matrix.multiplyMM(vp, 0, proj, 0, view, 0);

            drawWorld(now);
            drawCollectibles(now);
            if (inKart) {
                drawKart(px, py, pz, yaw);
                drawSeatedPlayer(px, py, pz, yaw);
            } else {
                drawPlayer(px, py, pz, yaw);
                drawKart(-18, 0, 16, 25);
            }
            if (stage == 2 && inKart) drawCheckpoint();
        } catch (Throwable ignored) {
            // Never let a graphics-driver/runtime quirk kill the process.
            glReady = false;
        }
    }

    private void update(float dt, long now) {
        camYaw += camDx * 0.18f;
        camPitch = clamp(camPitch + camDy * 0.12f, 9f, 35f);
        camDx = 0f;
        camDy = 0f;

        if (actionReq) {
            actionReq = false;
            action();
        }

        if (!inKart) {
            float mag = (float) Math.sqrt(moveX * moveX + moveY * moveY);
            if (mag > 0.08f) {
                float mx = moveX / Math.max(1f, mag);
                float fw = -moveY / Math.max(1f, mag);
                float a = camYaw * DEG;
                float dx = mx * (float) Math.cos(a) + fw * (float) Math.sin(a);
                float dz = -mx * (float) Math.sin(a) + fw * (float) Math.cos(a);
                px += dx * 5f * dt;
                pz += dz * 5f * dt;
                yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
                walk += dt * 8f * mag;
            }
            if (jumpReq && grounded) {
                grounded = false;
                vY = 5.5f;
                audio.jump();
            }
            jumpReq = false;
            if (!grounded) {
                vY -= 12.5f * dt;
                py += vY * dt;
                if (py <= 0) {
                    py = 0;
                    vY = 0;
                    grounded = true;
                }
            }
        } else {
            jumpReq = false;
            float throttle = clamp(-moveY, -1f, 1f);
            float steer = clamp(moveX, -1f, 1f);
            kartSpeed += throttle * (throttle >= 0 ? 9f : 12f) * dt;
            kartSpeed *= (float) Math.pow(0.45f, dt);
            kartSpeed = clamp(kartSpeed, -3f, 10.5f);
            if (Math.abs(kartSpeed) > 0.12f) {
                yaw += steer * (55f + Math.abs(kartSpeed) * 2f) * dt * Math.signum(kartSpeed);
            }
            float a = yaw * DEG;
            px += (float) Math.sin(a) * kartSpeed * dt;
            pz += (float) Math.cos(a) * kartSpeed * dt;
            camYaw += shortest(yaw - camYaw) * dt * 1.2f;
            audio.setEnginePitch(Math.abs(kartSpeed) / 10.5f);
            if (stage == 2) {
                raceTime += dt;
                if (raceTime > 75f) {
                    show("Čas vypršel – zkus to znovu!", 2600);
                    resetRace();
                }
            }
        }

        px = clamp(px, -27f, 27f);
        pz = clamp(pz, -27f, 27f);
        collect();
        if (stage == 2 && inKart) race();
        updateSnapshot(now);
    }

    private void action() {
        float d = dist2(px, pz, -18, 16);
        if (!inKart && stage >= 2 && d < 10f) {
            inKart = true;
            py = 0;
            kartSpeed = 0;
            audio.startEngine();
            if (stage == 2) {
                checkpoint = 0;
                raceTime = 0;
                show("Závod! Projeď 8 modrými bránami.", 2500);
            }
            return;
        }
        if (inKart && stage >= 3) {
            inKart = false;
            kartSpeed = 0;
            audio.stopEngine();
            show("Volná jízda dokončena.", 1500);
        }
    }

    private void collect() {
        for (Item i : items) {
            if (!i.active) continue;
            if (i.type == Item.MINERAL && stage != 0) continue;
            if (i.type == Item.TOOL && stage != 1) continue;
            if (dist2(px, pz, i.x, i.z) > 2.1f) continue;
            i.active = false;
            audio.collect();
            if (i.type == Item.MINERAL) {
                minerals++;
                show("Minerál " + minerals + "/5", 1100);
                if (minerals >= 5) {
                    stage = 1;
                    save();
                    audio.success();
                    show("Super! Najdi 3 díly pro mechanika.", 3000);
                }
            } else if (i.type == Item.TOOL) {
                tools++;
                show("Díl " + tools + "/3", 1100);
                if (tools >= 3) {
                    stage = 2;
                    save();
                    audio.success();
                    show("Motokára čeká v depu!", 3000);
                }
            } else {
                coins++;
                prefs.edit().putInt("coins", coins).apply();
            }
        }
    }

    private void race() {
        if (checkpoint >= GATES.length) return;
        float[] g = GATES[checkpoint];
        if (dist2(px, pz, g[0], g[1]) < 9f) {
            checkpoint++;
            audio.collect();
            if (checkpoint >= GATES.length) {
                stage = 3;
                inKart = false;
                audio.stopEngine();
                if (bestTime < 0 || raceTime < bestTime) bestTime = raceTime;
                prefs.edit().putInt("stage", stage).putFloat("best", bestTime).putInt("coins", coins).apply();
                audio.success();
                show("CÍL! Výborná jízda!", 4500);
            } else {
                show("Brána " + checkpoint + "/8", 900);
            }
        }
    }

    private void resetRace() {
        checkpoint = 0;
        raceTime = 0;
        px = -18;
        pz = 16;
        yaw = 25;
        kartSpeed = 0;
    }

    private void save() {
        prefs.edit().putInt("stage", stage).putInt("coins", coins).apply();
    }

    private void updateSnapshot(long now) {
        snapshot = new GameSnapshot(stage, minerals, tools, coins, inKart, checkpoint, GATES.length,
                raceTime, bestTime, now < messageUntil ? message : "");
    }

    private void drawWorld(long now) {
        drawCube(0, -0.55f, 0, 60, 1, 60, 0, 0.25f, 0.60f, 0.28f, 1);
        drawCube(0, -0.02f, 0, 6, 0.08f, 54, 0, 0.20f, 0.22f, 0.25f, 1);
        drawCube(0, -0.01f, 0, 46, 0.08f, 5, 0, 0.20f, 0.22f, 0.25f, 1);
        drawCube(-15, 0, 7, 25, 0.07f, 3.5f, 34, 0.24f, 0.25f, 0.28f, 1);
        drawCube(15, 0, -8, 23, 0.07f, 3.5f, -38, 0.24f, 0.25f, 0.28f, 1);

        drawHouse(-20, -12, 0.72f, 0.18f, 0.12f);
        drawHouse(18, -12, 0.10f, 0.45f, 0.75f);
        drawHouse(18, 13, 0.82f, 0.55f, 0.10f);

        // Depot
        drawCube(-18, 1.45f, 18, 8, 2.8f, 5, 0, 0.78f, 0.23f, 0.08f, 1);
        drawCube(-18, 3.0f, 18, 8.6f, 0.25f, 5.6f, 0, 0.12f, 0.14f, 0.17f, 1);
        drawCube(-18, 1.1f, 15.45f, 4.8f, 2.1f, 0.15f, 0, 0.10f, 0.13f, 0.16f, 1);

        for (int i = -24; i <= 24; i += 6) {
            drawTree(i, -25);
            drawTree(i, 25);
            if (i % 12 == 0) {
                drawTree(-25, i);
                drawTree(25, i);
            }
        }

        for (int i = 0; i < 12; i++) {
            double a = i * 0.93;
            float r = 17 + (i % 3) * 3;
            float x = (float) Math.cos(a) * r;
            float z = (float) Math.sin(a) * r;
            drawCube(x, 0.55f, z, 1.3f, 1.1f, 1.3f, i * 17f, 0.43f, 0.39f, 0.34f, 1);
        }
    }

    private void drawCollectibles(long now) {
        float bob = (float) Math.sin(now * 0.004) * 0.18f;
        for (Item i : items) {
            if (!i.active) continue;
            if (i.type == Item.MINERAL && stage != 0) continue;
            if (i.type == Item.TOOL && stage != 1) continue;
            if (i.type == Item.MINERAL) {
                drawCube(i.x, 0.75f + bob, i.z, 0.65f, 1.2f, 0.65f, now * 0.06f, 0.62f, 0.28f, 0.90f, 1);
            } else if (i.type == Item.TOOL) {
                drawCube(i.x, 0.65f + bob, i.z, 1.0f, 0.28f, 0.28f, now * 0.04f, 0.80f, 0.82f, 0.86f, 1);
                drawCube(i.x, 0.65f + bob, i.z, 0.28f, 1.0f, 0.28f, now * 0.04f, 0.25f, 0.28f, 0.31f, 1);
            } else {
                drawCube(i.x, 0.55f + bob, i.z, 0.45f, 0.12f, 0.45f, now * 0.08f, 0.98f, 0.76f, 0.05f, 1);
            }
        }
    }

    private void drawCheckpoint() {
        if (checkpoint >= GATES.length) return;
        float[] g = GATES[checkpoint];
        drawCube(g[0] - 1.8f, 1.7f, g[1], 0.22f, 3.4f, 0.22f, 0, 0.08f, 0.63f, 1f, 1);
        drawCube(g[0] + 1.8f, 1.7f, g[1], 0.22f, 3.4f, 0.22f, 0, 0.08f, 0.63f, 1f, 1);
        drawCube(g[0], 3.35f, g[1], 3.8f, 0.22f, 0.22f, 0, 0.08f, 0.63f, 1f, 1);
    }

    private void drawHouse(float x, float z, float r, float g, float b) {
        drawCube(x, 1.2f, z, 4.2f, 2.4f, 3.6f, 0, r, g, b, 1);
        drawCube(x, 2.55f, z, 4.7f, 0.35f, 4.0f, 0, 0.25f, 0.18f, 0.12f, 1);
        drawCube(x, 0.9f, z - 1.83f, 1.1f, 1.8f, 0.15f, 0, 0.22f, 0.30f, 0.38f, 1);
    }

    private void drawTree(float x, float z) {
        drawCube(x, 0.9f, z, 0.45f, 1.8f, 0.45f, 0, 0.34f, 0.20f, 0.10f, 1);
        drawCube(x, 2.35f, z, 2.2f, 2.2f, 2.2f, 25, 0.12f, 0.46f, 0.18f, 1);
    }

    private void drawPlayer(float x, float y, float z, float r) {
        float swing = (float) Math.sin(walk) * 20f;
        drawPart(x, y, z, r, -0.22f, 0.55f, 0, 0.34f, 1.05f, 0.38f, swing, 0.06f, 0.09f, 0.14f, 1);
        drawPart(x, y, z, r, 0.22f, 0.55f, 0, 0.34f, 1.05f, 0.38f, -swing, 0.06f, 0.09f, 0.14f, 1);
        drawPart(x, y, z, r, 0, 1.55f, 0, 1.02f, 1.28f, 0.58f, 0, 0.11f, 0.28f, 0.68f, 1);
        drawPart(x, y, z, r, -0.68f, 1.55f, 0, 0.30f, 1.12f, 0.32f, -swing, 0.10f, 0.24f, 0.60f, 1);
        drawPart(x, y, z, r, 0.68f, 1.55f, 0, 0.30f, 1.12f, 0.32f, swing, 0.10f, 0.24f, 0.60f, 1);
        drawPart(x, y, z, r, 0, 2.55f, 0, 0.92f, 0.92f, 0.82f, 0, 0.97f, 0.78f, 0.60f, 1);
        drawPart(x, y, z, r, 0, 3.08f, 0, 1.02f, 0.25f, 0.94f, 0, 0.08f, 0.34f, 0.82f, 1);
        drawPart(x, y, z, r, 0, 3.02f, 0.46f, 1.35f, 0.12f, 0.42f, 0, 0.10f, 0.40f, 0.90f, 1);
        drawPart(x, y, z, r, 0, 1.78f, -0.31f, 0.92f, 0.12f, 0.10f, 0, 0.95f, 0.82f, 0.06f, 1);
    }

    private void drawSeatedPlayer(float x, float y, float z, float r) {
        drawPart(x, y, z, r, 0, 1.30f, 0, 0.95f, 1.08f, 0.56f, 0, 0.11f, 0.28f, 0.68f, 1);
        drawPart(x, y, z, r, 0, 2.15f, 0, 0.86f, 0.86f, 0.78f, 0, 0.97f, 0.78f, 0.60f, 1);
        drawPart(x, y, z, r, 0, 2.63f, 0, 0.98f, 0.22f, 0.90f, 0, 0.08f, 0.34f, 0.82f, 1);
    }

    private void drawKart(float x, float y, float z, float r) {
        drawPart(x, y, z, r, 0, 0.48f, 0, 2.3f, 0.38f, 1.35f, 0, 0.08f, 0.22f, 0.72f, 1);
        drawPart(x, y, z, r, 0, 0.70f, 0, 1.25f, 0.42f, 0.72f, 0, 0.95f, 0.79f, 0.08f, 1);
        float[][] ws = {{-0.95f, -0.58f}, {0.95f, -0.58f}, {-0.95f, 0.58f}, {0.95f, 0.58f}};
        for (float[] w : ws) {
            drawPart(x, y, z, r, w[0], 0.30f, w[1], 0.42f, 0.42f, 0.28f, 0, 0.03f, 0.04f, 0.05f, 1);
        }
    }

    private void drawPart(float x, float y, float z, float parentYaw,
                          float ox, float oy, float oz,
                          float sx, float sy, float sz, float rotX,
                          float r, float g, float b, float a) {
        float ang = parentYaw * DEG;
        float rx = ox * (float) Math.cos(ang) + oz * (float) Math.sin(ang);
        float rz = -ox * (float) Math.sin(ang) + oz * (float) Math.cos(ang);
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x + rx, y + oy, z + rz);
        Matrix.rotateM(model, 0, parentYaw, 0, 1, 0);
        if (rotX != 0) Matrix.rotateM(model, 0, rotX, 1, 0, 0);
        Matrix.scaleM(model, 0, sx, sy, sz);
        drawCurrent(r, g, b, a);
    }

    private void drawCube(float x, float y, float z, float sx, float sy, float sz, float rotY,
                          float r, float g, float b, float a) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        if (rotY != 0) Matrix.rotateM(model, 0, rotY, 0, 1, 0);
        Matrix.scaleM(model, 0, sx, sy, sz);
        drawCurrent(r, g, b, a);
    }

    private void drawCurrent(float r, float g, float b, float a) {
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glUniform4f(uColor, r, g, b, a);
        cube.draw(aPos);
    }

    private void show(String text, long ms) {
        message = text;
        messageUntil = SystemClock.uptimeMillis() + ms;
    }

    public void setMove(float x, float y) {
        moveX = x;
        moveY = y;
    }

    public void addCamera(float x, float y) {
        camDx += x;
        camDy += y;
    }

    public void requestJump() {
        jumpReq = true;
    }

    public void requestAction() {
        actionReq = true;
    }

    public void setPaused(boolean value) {
        paused = value;
        if (value) audio.stopEngine();
        else if (inKart) audio.startEngine();
    }

    public GameSnapshot getSnapshot() {
        return snapshot;
    }

    public void resetGame() {
        stage = 0;
        minerals = 0;
        tools = 0;
        coins = 0;
        checkpoint = 0;
        bestTime = -1;
        raceTime = 0;
        inKart = false;
        kartSpeed = 0;
        px = -18;
        py = 0;
        pz = 16;
        yaw = 25;
        prefs.edit().clear().apply();
        populate();
        show("Nová hra – najdi 5 minerálů!", 3000);
    }

    public void release() {
        audio.release();
    }

    private static float dist2(float a, float b, float c, float d) {
        float x = a - c;
        float z = b - d;
        return x * x + z * z;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float shortest(float a) {
        while (a > 180) a -= 360;
        while (a < -180) a += 360;
        return a;
    }

    private static int buildProgram(String vertexSource, String fragmentSource) {
        try {
            int vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource);
            int fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            if (vertex == 0 || fragment == 0) return 0;
            int p = GLES20.glCreateProgram();
            if (p == 0) return 0;
            GLES20.glAttachShader(p, vertex);
            GLES20.glAttachShader(p, fragment);
            GLES20.glLinkProgram(p);
            int[] ok = new int[1];
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
            GLES20.glDeleteShader(vertex);
            GLES20.glDeleteShader(fragment);
            if (ok[0] == 0) {
                GLES20.glDeleteProgram(p);
                return 0;
            }
            return p;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int compile(int type, String source) {
        try {
            int shader = GLES20.glCreateShader(type);
            if (shader == 0) return 0;
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] ok = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                GLES20.glDeleteShader(shader);
                return 0;
            }
            return shader;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static final String VS =
            "uniform mat4 uMVP;" +
            "attribute vec3 aPosition;" +
            "void main(){gl_Position=uMVP*vec4(aPosition,1.0);}";

    private static final String FS =
            "precision mediump float;" +
            "uniform vec4 uColor;" +
            "void main(){gl_FragColor=uColor;}";

    private static final class SimpleMesh {
        private final FloatBuffer vertices;
        private final int count;

        private SimpleMesh(float[] data) {
            vertices = ByteBuffer.allocateDirect(data.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            vertices.put(data).position(0);
            count = data.length / 3;
        }

        static SimpleMesh cube() {
            float n = -0.5f, p = 0.5f;
            float[] v = {
                    n,n,p,  p,n,p,  p,p,p,   n,n,p,  p,p,p,  n,p,p,
                    p,n,n,  n,n,n,  n,p,n,   p,n,n,  n,p,n,  p,p,n,
                    n,n,n,  n,n,p,  n,p,p,   n,n,n,  n,p,p,  n,p,n,
                    p,n,p,  p,n,n,  p,p,n,   p,n,p,  p,p,n,  p,p,p,
                    n,p,p,  p,p,p,  p,p,n,   n,p,p,  p,p,n,  n,p,n,
                    n,n,n,  p,n,n,  p,n,p,   n,n,n,  p,n,p,  n,n,p
            };
            return new SimpleMesh(v);
        }

        void draw(int positionLocation) {
            if (positionLocation < 0) return;
            vertices.position(0);
            GLES20.glVertexAttribPointer(positionLocation, 3, GLES20.GL_FLOAT, false, 12, vertices);
            GLES20.glEnableVertexAttribArray(positionLocation);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);
            GLES20.glDisableVertexAttribArray(positionLocation);
        }
    }
}

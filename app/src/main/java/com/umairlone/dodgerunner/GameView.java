package com.umairlone.dodgerunner;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Dodge Runner - a tiny endless runner drawn entirely with Canvas.
 *
 * Tap to jump, tap again in mid-air for a double jump, and stay alive as the
 * obstacles get faster. No images, no sound files, no third-party libraries.
 */
public class GameView extends View implements Choreographer.FrameCallback {

    private static final int STATE_READY = 0;
    private static final int STATE_PLAYING = 1;
    private static final int STATE_OVER = 2;

    private static final int SKY_TOP = Color.parseColor("#101B33");
    private static final int SKY_MID = Color.parseColor("#2C4470");
    private static final int SKY_LOW = Color.parseColor("#C97B4A");
    private static final int HILL_FAR = Color.parseColor("#22304D");
    private static final int HILL_NEAR = Color.parseColor("#1A2540");
    private static final int GROUND = Color.parseColor("#141B2A");
    private static final int GROUND_LINE = Color.parseColor("#2C3852");
    private static final int PLAYER = Color.parseColor("#4EE1C1");
    private static final int PLAYER_DARK = Color.parseColor("#2BB39A");
    private static final int OBSTACLE = Color.parseColor("#F26B5B");
    private static final int OBSTACLE_DARK = Color.parseColor("#C74C40");
    private static final int TEXT = Color.parseColor("#F2F6FF");
    private static final int ACCENT = Color.parseColor("#FFC48A");

    private static final float GRAVITY = 3.4f;      // in screen-heights per second squared
    private static final float JUMP_SPEED = 1.25f;  // in screen-heights per second
    private static final float BASE_SPEED = 0.55f;  // in screen-widths per second
    private static final float MAX_EXTRA_SPEED = 0.65f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF panelRect = new RectF();
    private final Path hillPath = new Path();
    private final Random random = new Random();
    private final List<Obstacle> obstacles = new ArrayList<Obstacle>();
    private final SharedPreferences prefs;

    private Shader skyShader;

    private float w, h, groundY;
    private int state = STATE_READY;
    private boolean loopActive = false;
    private long lastFrameNanos = 0L;

    private float uiTime = 0f;
    private float elapsed = 0f;
    private float speed = BASE_SPEED;
    private float scoreFloat = 0f;
    private float overTimer = 0f;
    private float spawnTimer = 1.2f;
    private float shake = 0f;

    private float playerX, playerW, playerH, playerBottom, playerVy;
    private int jumpsUsed = 0;

    private float cloudOffset = 0f;
    private float hillOffset = 0f;
    private float groundOffset = 0f;

    private int best = 0;
    private boolean newBest = false;

    private static class Obstacle {
        float x, y, w, h;
    }

    public GameView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("dodge_runner", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        setFocusable(true);
        setKeepScreenOn(true);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        super.onSizeChanged(width, height, oldw, oldh);
        w = width;
        h = height;
        groundY = h * 0.80f;
        playerH = h * 0.105f;
        playerW = playerH * 0.82f;
        playerX = w * 0.16f;
        if (state != STATE_PLAYING) {
            playerBottom = groundY;
        }
        skyShader = new LinearGradient(
                0f, 0f, 0f, groundY,
                new int[]{SKY_TOP, SKY_MID, SKY_LOW},
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP);
        bgPaint.setShader(skyShader);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        resume();
    }

    @Override
    protected void onDetachedFromWindow() {
        pause();
        super.onDetachedFromWindow();
    }

    public void resume() {
        if (loopActive) {
            return;
        }
        loopActive = true;
        lastFrameNanos = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void pause() {
        loopActive = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    // ------------------------------------------------------------------ game loop

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!loopActive) {
            return;
        }
        if (lastFrameNanos == 0L) {
            lastFrameNanos = frameTimeNanos;
        }
        float dt = (frameTimeNanos - lastFrameNanos) / 1000000000f;
        lastFrameNanos = frameTimeNanos;
        if (dt < 0f) {
            dt = 0f;
        }
        if (dt > 0.05f) {
            dt = 0.05f;
        }
        uiTime += dt;

        if (state == STATE_PLAYING) {
            update(dt);
        } else {
            overTimer += dt;
            idleScroll(dt);
        }
        if (shake > 0f) {
            shake -= dt * 3.2f;
            if (shake < 0f) {
                shake = 0f;
            }
        }

        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void idleScroll(float dt) {
        float px = BASE_SPEED * w * 0.35f * dt;
        cloudOffset = (cloudOffset + px * 0.12f) % (w * 2f);
        hillOffset = (hillOffset + px * 0.25f) % (w * 0.9f);
        groundOffset = (groundOffset + px) % (w * 0.12f);
        playerBottom = groundY - Math.abs((float) Math.sin(uiTime * 2.4f)) * h * 0.008f;
    }

    private void update(float dt) {
        elapsed += dt;
        speed = BASE_SPEED + Math.min(MAX_EXTRA_SPEED, elapsed * 0.018f);
        float pxPerSec = speed * w;

        scoreFloat += dt * (12f + elapsed * 0.6f);
        cloudOffset = (cloudOffset + pxPerSec * 0.12f * dt) % (w * 2f);
        hillOffset = (hillOffset + pxPerSec * 0.25f * dt) % (w * 0.9f);
        groundOffset = (groundOffset + pxPerSec * dt) % (w * 0.12f);

        // player physics
        playerVy += GRAVITY * h * dt;
        playerBottom += playerVy * dt;
        if (playerBottom >= groundY) {
            playerBottom = groundY;
            playerVy = 0f;
            jumpsUsed = 0;
        }

        // obstacles
        spawnTimer -= dt;
        if (spawnTimer <= 0f) {
            spawnObstacle();
        }

        Iterator<Obstacle> it = obstacles.iterator();
        while (it.hasNext()) {
            Obstacle o = it.next();
            o.x -= pxPerSec * dt;
            if (o.x + o.w < -w * 0.05f) {
                it.remove();
                continue;
            }
            if (hitsPlayer(o)) {
                gameOver();
                return;
            }
        }
    }

    private void spawnObstacle() {
        Obstacle o = new Obstacle();
        o.h = h * (0.085f + random.nextFloat() * 0.105f);
        o.w = w * (0.055f + random.nextFloat() * 0.045f);
        o.x = w + o.w;
        o.y = groundY - o.h;
        obstacles.add(o);
        // Keep the horizontal gap proportional to speed so it stays fair.
        spawnTimer = (w * (0.44f + random.nextFloat() * 0.5f)) / (speed * w);
    }

    private boolean hitsPlayer(Obstacle o) {
        float pw = playerW * 0.68f;
        float ph = playerH * 0.78f;
        float pLeft = playerX + (playerW - pw) * 0.5f;
        float pRight = pLeft + pw;
        float pTop = playerBottom - playerH + (playerH - ph) * 0.5f;
        float pBottom = pTop + ph;

        float oLeft = o.x + o.w * 0.16f;
        float oRight = o.x + o.w * 0.84f;
        float oTop = o.y + o.h * 0.10f;
        float oBottom = o.y + o.h;

        return pRight > oLeft && pLeft < oRight && pBottom > oTop && pTop < oBottom;
    }

    private void gameOver() {
        state = STATE_OVER;
        overTimer = 0f;
        shake = 1f;
        int score = (int) scoreFloat;
        newBest = score > best;
        if (newBest) {
            best = score;
            prefs.edit().putInt("best", best).apply();
        }
        buzz();
    }

    private void reset() {
        obstacles.clear();
        elapsed = 0f;
        speed = BASE_SPEED;
        scoreFloat = 0f;
        overTimer = 0f;
        spawnTimer = 1.1f;
        shake = 0f;
        playerVy = 0f;
        jumpsUsed = 0;
        playerBottom = groundY;
        newBest = false;
    }

    // --------------------------------------------------------------------- input

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            onTap();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void onTap() {
        if (state == STATE_READY) {
            reset();
            state = STATE_PLAYING;
            return;
        }
        if (state == STATE_OVER) {
            if (overTimer > 0.45f) {
                reset();
                state = STATE_PLAYING;
            }
            return;
        }
        if (jumpsUsed < 2) {
            playerVy = -JUMP_SPEED * h;
            jumpsUsed++;
        }
    }

    private void buzz() {
        try {
            Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(40);
            }
        } catch (Exception ignored) {
            // Vibration is a nicety; never let it break the game.
        }
    }

    // ------------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas canvas) {
        if (w <= 0f || h <= 0f) {
            return;
        }
        canvas.save();
        if (shake > 0.01f) {
            canvas.translate((random.nextFloat() - 0.5f) * shake * 20f,
                    (random.nextFloat() - 0.5f) * shake * 20f);
        }
        drawSky(canvas);
        drawHills(canvas);
        drawClouds(canvas);
        drawGround(canvas);
        drawObstacles(canvas);
        drawPlayer(canvas);
        canvas.restore();

        drawHud(canvas);
        drawOverlays(canvas);
    }

    private void drawSky(Canvas canvas) {
        if (skyShader != null) {
            bgPaint.setShader(skyShader);
            canvas.drawRect(0f, 0f, w, groundY, bgPaint);
        } else {
            canvas.drawColor(SKY_TOP);
        }
        paint.setShader(null);
        paint.setColor(0x55FFC48A);
        canvas.drawCircle(w * 0.76f, groundY - h * 0.40f, h * 0.075f, paint);
        paint.setColor(0x22FFD9A0);
        canvas.drawCircle(w * 0.76f, groundY - h * 0.40f, h * 0.13f, paint);
    }

    private void drawHills(Canvas canvas) {
        drawHillLayer(canvas, HILL_FAR, h * 0.11f, w * 0.9f, hillOffset);
        drawHillLayer(canvas, HILL_NEAR, h * 0.065f, w * 0.6f, hillOffset * 1.5f);
    }

    private void drawHillLayer(Canvas canvas, int color, float amp, float period, float offset) {
        float startX = -(offset % period) - period;
        hillPath.reset();
        hillPath.moveTo(startX, groundY);
        for (int i = 0; i < 4; i++) {
            float x0 = startX + i * period;
            hillPath.quadTo(x0 + period * 0.25f, groundY - amp, x0 + period * 0.5f, groundY);
            hillPath.quadTo(x0 + period * 0.75f, groundY + amp * 0.30f, x0 + period, groundY);
        }
        hillPath.lineTo(startX + 4 * period, groundY + h);
        hillPath.lineTo(startX, groundY + h);
        hillPath.close();
        paint.setShader(null);
        paint.setColor(color);
        canvas.drawPath(hillPath, paint);
    }

    private void drawClouds(Canvas canvas) {
        paint.setShader(null);
        paint.setColor(0x14FFFFFF);
        float baseX = -(cloudOffset % (w * 2f));
        for (int i = 0; i < 4; i++) {
            float cx = baseX + i * (w * 0.55f) + w * 0.15f;
            float cy = h * 0.15f + (i % 2 == 0 ? 0f : h * 0.08f);
            float r = h * (i % 2 == 0 ? 0.028f : 0.022f);
            canvas.drawCircle(cx, cy, r, paint);
            canvas.drawCircle(cx + r * 1.1f, cy + r * 0.2f, r * 0.8f, paint);
            canvas.drawCircle(cx - r * 1.1f, cy + r * 0.25f, r * 0.7f, paint);
        }
    }

    private void drawGround(Canvas canvas) {
        paint.setShader(null);
        paint.setColor(GROUND);
        canvas.drawRect(0f, groundY, w, h, paint);
        paint.setColor(GROUND_LINE);
        canvas.drawRect(0f, groundY, w, groundY + h * 0.005f, paint);

        float dashW = w * 0.06f;
        float step = w * 0.12f;
        float y = groundY + h * 0.035f;
        float x = -(groundOffset % step) - step;
        while (x < w) {
            canvas.drawRect(x, y, x + dashW, y + h * 0.006f, paint);
            x += step;
        }
    }

    private void drawObstacles(Canvas canvas) {
        paint.setShader(null);
        for (Obstacle o : obstacles) {
            float r = o.w * 0.28f;
            paint.setColor(OBSTACLE_DARK);
            canvas.drawRoundRect(o.x, o.y, o.x + o.w, o.y + o.h, r, r, paint);
            paint.setColor(OBSTACLE);
            canvas.drawRoundRect(o.x + o.w * 0.12f, o.y, o.x + o.w * 0.88f,
                    o.y + o.h * 0.93f, r * 0.8f, r * 0.8f, paint);
            paint.setColor(0x33FFFFFF);
            canvas.drawRoundRect(o.x + o.w * 0.20f, o.y + o.h * 0.12f, o.x + o.w * 0.42f,
                    o.y + o.h * 0.55f, r * 0.4f, r * 0.4f, paint);
        }
    }

    private void drawPlayer(Canvas canvas) {
        float left = playerX;
        float top = playerBottom - playerH;
        float cx = left + playerW * 0.5f;
        float cy = top + playerH * 0.5f;

        // shadow shrinks as the runner leaves the ground
        float air = Math.max(0f, (groundY - playerBottom) / (h * 0.25f));
        paint.setShader(null);
        paint.setColor(0x33000000);
        float shadowW = playerW * (1f - air * 0.45f);
        panelRect.set(cx - shadowW * 0.6f, groundY - h * 0.012f,
                cx + shadowW * 0.6f, groundY + h * 0.012f);
        canvas.drawOval(panelRect, paint);

        canvas.save();
        float tilt = Math.max(-14f, Math.min(14f, playerVy / (h * 3.4f) * 14f));
        canvas.rotate(tilt, cx, cy);

        paint.setColor(PLAYER_DARK);
        panelRect.set(left, top + playerH * 0.12f, left + playerW, playerBottom + playerH * 0.06f);
        canvas.drawRoundRect(panelRect, playerH * 0.24f, playerH * 0.24f, paint);

        paint.setColor(PLAYER);
        panelRect.set(left, top, left + playerW, playerBottom);
        canvas.drawRoundRect(panelRect, playerH * 0.24f, playerH * 0.24f, paint);

        paint.setColor(0x33FFFFFF);
        panelRect.set(left + playerW * 0.18f, top + playerH * 0.12f,
                left + playerW * 0.40f, top + playerH * 0.62f);
        canvas.drawRoundRect(panelRect, playerH * 0.1f, playerH * 0.1f, paint);

        paint.setColor(Color.parseColor("#0E1626"));
        canvas.drawCircle(left + playerW * 0.70f, top + playerH * 0.34f,
                playerW * 0.10f, paint);
        canvas.restore();
    }

    private void drawHud(Canvas canvas) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setColor(TEXT);
        textPaint.setTextSize(h * 0.075f);
        textPaint.setShadowLayer(h * 0.008f, 0f, h * 0.004f, 0x99000000);
        canvas.drawText(String.valueOf((int) scoreFloat), w * 0.5f, h * 0.115f, textPaint);

        textPaint.setTextSize(h * 0.030f);
        textPaint.setColor(0x99F2F6FF);
        canvas.drawText("BEST " + best, w * 0.5f, h * 0.155f, textPaint);
        textPaint.clearShadowLayer();
    }

    private void drawOverlays(Canvas canvas) {
        float pulse = 0.55f + 0.45f * (float) Math.sin(uiTime * 4f);
        if (pulse < 0f) {
            pulse = 0f;
        }

        if (state == STATE_READY) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setColor(TEXT);
            textPaint.setTextSize(h * 0.085f);
            textPaint.setShadowLayer(h * 0.01f, 0f, h * 0.005f, 0xAA000000);
            canvas.drawText("DODGE", w * 0.5f, h * 0.30f, textPaint);
            textPaint.setColor(PLAYER);
            canvas.drawText("RUNNER", w * 0.5f, h * 0.385f, textPaint);

            textPaint.setTextSize(h * 0.034f);
            textPaint.setColor(TEXT);
            textPaint.setAlpha((int) (255 * pulse));
            canvas.drawText("TAP TO START", w * 0.5f, h * 0.50f, textPaint);

            textPaint.setAlpha(0x88);
            textPaint.setTextSize(h * 0.026f);
            canvas.drawText("Tap again in mid-air to double jump",
                    w * 0.5f, h * 0.555f, textPaint);
            textPaint.setAlpha(255);
            textPaint.clearShadowLayer();
            return;
        }

        if (state == STATE_OVER) {
            paint.setShader(null);
            paint.setColor(0xCC070C18);
            canvas.drawRect(0f, 0f, w, h, paint);

            float top = h * 0.29f;
            float bottom = h * 0.63f;
            paint.setColor(0xF2162033);
            panelRect.set(w * 0.10f, top, w * 0.90f, bottom);
            canvas.drawRoundRect(panelRect, h * 0.03f, h * 0.03f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(h * 0.0035f);
            paint.setColor(0x33FFFFFF);
            canvas.drawRoundRect(panelRect, h * 0.03f, h * 0.03f, paint);
            paint.setStyle(Paint.Style.FILL);

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setColor(OBSTACLE);
            textPaint.setTextSize(h * 0.048f);
            canvas.drawText("GAME OVER", w * 0.5f, top + h * 0.075f, textPaint);

            textPaint.setColor(TEXT);
            textPaint.setTextSize(h * 0.075f);
            canvas.drawText(String.valueOf((int) scoreFloat), w * 0.5f,
                    top + h * 0.165f, textPaint);

            textPaint.setTextSize(h * 0.028f);
            textPaint.setColor(0x99F2F6FF);
            canvas.drawText("BEST " + best, w * 0.5f, top + h * 0.205f, textPaint);

            if (newBest) {
                textPaint.setColor(ACCENT);
                textPaint.setTextSize(h * 0.030f);
                canvas.drawText("NEW BEST!", w * 0.5f, top + h * 0.245f, textPaint);
            }

            textPaint.setColor(TEXT);
            textPaint.setTextSize(h * 0.032f);
            textPaint.setAlpha((int) (255 * pulse));
            canvas.drawText("TAP TO PLAY AGAIN", w * 0.5f, bottom - h * 0.030f, textPaint);
            textPaint.setAlpha(255);
        }
    }
}

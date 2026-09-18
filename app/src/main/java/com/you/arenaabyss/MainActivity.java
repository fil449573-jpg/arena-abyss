package com.you.arenaabyss;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import java.util.*;

public class MainActivity extends Activity {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        gameView = new GameView(this);
        setContentView(gameView);
        hideSystemUI();
    }

    private void hideSystemUI() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) gameView.paused = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.paused = false;
    }

    // ==================== ИГРА ====================
    static class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
        Thread thread;
        volatile boolean running = false;
        volatile boolean paused = false;
        Game game = new Game();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float scale = 1, ox, oy;
        List<Btn> btns = new ArrayList<>();

        static class Btn {
            float x, y, r;
            String label;
            int code;
            boolean down;
            Btn(float x, float y, float r, String l, int c) {
                this.x = x; this.y = y; this.r = r;
                this.label = l; this.code = c;
            }
        }

        public GameView(Context c) {
            super(c);
            getHolder().addCallback(this);
            setFocusable(true);
        }

        @Override
        public void surfaceCreated(SurfaceHolder h) {
            thread = new Thread(this);
            running = true;
            thread.start();
        }

        @Override
        public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) {
            scale = Math.min((float)w / 480f, (float)ht / 320f);
            ox = (w - 480 * scale) / 2;
            oy = (ht - 320 * scale) / 2;
            layout();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder h) {
            running = false;
            try { thread.join(); } catch (Exception e) {}
        }

        void layout() {
            btns.clear();
            float m = 20 * scale, r = 34 * scale, st = 70 * scale;
            float bx = m + r + st, by = getHeight() - m - r - st;
            btns.add(new Btn(bx, by - st, r, "▲", 0));
            btns.add(new Btn(bx, by + st, r, "▼", 1));
            btns.add(new Btn(bx - st, by, r, "◀", 2));
            btns.add(new Btn(bx + st, by, r, "▶", 3));
            float ax = getWidth() - m - r - st, ay = getHeight() - m - r;
            btns.add(new Btn(ax, ay, r, "⚔", 10));
            btns.add(new Btn(ax - st * 1.2f, ay, r, "💨", 11));
            btns.add(new Btn(ax, ay - st * 1.2f, r, "🔥", 12));
            btns.add(new Btn(ax - st * 1.2f, ay - st * 1.2f, r, "🛡", 13));
            btns.add(new Btn(ax + st * 1.2f, ay - st * 0.6f, r * 1.1f, "💥", 14));
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN ||
                a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_UP ||
                a == MotionEvent.ACTION_POINTER_UP || a == MotionEvent.ACTION_CANCEL) {
                for (Btn b : btns) b.down = false;
                int pc = e.getPointerCount();
                for (int i = 0; i < pc; i++) {
                    float tx = e.getX(i), ty = e.getY(i);
                    for (Btn b : btns) {
                        if (Math.hypot(tx - b.x, ty - b.y) <= b.r) b.down = true;
                    }
                }
                applyBtns();
            }
            return true;
        }

        void applyBtns() {
            Game g = game;
            g.keyUp = g.keyDown = g.keyLeft = g.keyRight = false;
            for (Btn b : btns) {
                if (!b.down) continue;
                switch (b.code) {
                    case 0: g.keyUp = true; break;
                    case 1: g.keyDown = true; break;
                    case 2: g.keyLeft = true; break;
                    case 3: g.keyRight = true; break;
                    case 10: g.attack(); b.down = false; break;
                    case 11: g.doDash(); b.down = false; break;
                    case 12: g.doRage(); b.down = false; break;
                    case 13: g.doShield(); b.down = false; break;
                    case 14: g.doNova(); b.down = false; break;
                }
            }
        }

        @Override
        public void run() {
            long last = System.nanoTime();
            while (running) {
                long now = System.nanoTime();
                float dt = (now - last) / 1_000_000_000f;
                last = now;
                if (dt > 0.05f) dt = 0.05f;
                if (!paused) {
                    gUpdate(dt);
                    draw();
                }
                try { Thread.sleep(16); } catch (Exception e) {}
            }
        }

        void gUpdate(float dt) {
            game.update(dt);
            if (game.awaitingUpgrade) {
                String[] ids = {"dash", "shield", "rage", "nova"};
                game.applyUpgrade(ids[(int)(Math.random() * 4)]);
            }
        }

        void draw() {
            SurfaceHolder h = getHolder();
            Canvas c = h.lockCanvas();
            if (c == null) return;
            try {
                c.drawColor(Color.BLACK);
                c.save();
                c.translate(ox, oy);
                c.scale(scale, scale);
                game.render(c, p);
                c.restore();

                for (Btn b : btns) {
                    p.setColor(b.down ? 0xFFFFA040 : 0xAA300000);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(b.x, b.y, b.r, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(3 * scale);
                    p.setColor(0xFFFFCC44);
                    c.drawCircle(b.x, b.y, b.r, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFFCC44);
                    p.setTextSize(b.r * 0.9f);
                    p.setTextAlign(Paint.Align.CENTER);
                    c.drawText(b.label, b.x, b.y + b.r * 0.3f, p);
                }
            } finally {
                h.unlockCanvasAndPost(c);
            }
        }
    }

    // ==================== ЛОГИКА ====================
    static class Game {
        static final int CW = 480, CH = 320;

        float px = 240, py = 160, pr = 11, facing = 0;
        float hp = 100, maxHp = 100;
        float atkT = 0, atkCd = 0, dashT = 0, dashCd = 0, rageT = 0, rageCd = 0;
        float shieldT = 0, shieldCd = 0, novaCd = 0, invuln = 0, regenT = 0;
        int kills = 0, score = 0, lvl = 1, xp = 0, xpNext = 30;
        int lvlDash = 1, lvlShield = 1, lvlRage = 1, lvlNova = 1;
        int world = 1, stage = 1;

        boolean keyUp, keyDown, keyLeft, keyRight;
        boolean running = true, gameOver = false, worldComplete = false, awaitingUpgrade = false;

        List<Enemy> enemies = new ArrayList<>();
        List<Particle> parts = new ArrayList<>();
        List<DmgText> dmgTexts = new ArrayList<>();
        Random rng = new Random();
        float waveMsg = 2, screenShake = 0;
        String waveText = "МИР 1 · ЭТАП 1";

        static class Enemy {
            float x, y, hp, maxHp, r, speed, dmg;
            int color, score;
            float atkCd, flash;
            boolean alive = true;
            Enemy(float x, float y, float hp, float r, float sp, float dm, int c, int s) {
                this.x = x; this.y = y;
                this.hp = hp; this.maxHp = hp;
                this.r = r; this.speed = sp;
                this.dmg = dm; this.color = c; this.score = s;
            }
        }

        static class Particle {
            float x, y, vx, vy, life, size;
            int color;
            Particle(float x, float y, float vx, float vy, float l, int c, float s) {
                this.x = x; this.y = y;
                this.vx = vx; this.vy = vy;
                this.life = l; this.color = c; this.size = s;
            }
        }

        static class DmgText {
            float x, y, life;
            String text;
            int color, size;
            DmgText(float x, float y, String t, int c, int s) {
                this.x = x; this.y = y; this.text = t;
                this.color = c; this.size = s; this.life = 0.8f;
            }
        }

        Game() { spawnStage(1, 1); }

        void spawnStage(int w, int s) {
            world = w; stage = s;
            enemies.clear(); parts.clear(); dmgTexts.clear();
            if (s == 15) {
                enemies.add(new Enemy(240, -40, 700 * (1 + w * 0.6f), 32, 40, 30 * (1 + w * 0.15f), 0xFFAA0088, 1000 * w));
                for (int i = 0; i < 2 + w; i++) enemies.add(makeEnemy(w, s));
                waveText = "⚠ БОСС МИРА " + w + " ⚠";
            } else {
                int count = 3 + s + (int)(w * 1.5f);
                for (int i = 0; i < count; i++) enemies.add(makeEnemy(w, s));
                waveText = "МИР " + w + " · ЭТАП " + s;
            }
            waveMsg = 2;
        }

        Enemy makeEnemy(int w, int s) {
            int total = (w - 1) * 15 + s;
            float scale = 1 + total * 0.12f;
            int maxIdx = Math.min(6, 2 + w / 2);
            int idx = rng.nextInt(maxIdx + 1);
            float[] hpArr = {22, 14, 70, 20, 30, 120, 200};
            float[] spArr = {60, 120, 38, 50, 160, 55, 60};
            float[] rArr = {10, 7, 15, 8, 8, 18, 22};
            float[] dmArr = {8, 5, 18, 10, 15, 22, 30};
            int[] colArr = {0xFFAA4444, 0xFFCC8844, 0xFF884488, 0xFF44AA88, 0xFF444444, 0xFFAA33AA, 0xFF880044};
            int[] scArr = {10, 15, 30, 25, 40, 60, 100};
            float x, y;
            int side = rng.nextInt(4);
            if (side == 0) { x = rng.nextFloat() * CW; y = -20; }
            else if (side == 1) { x = CW + 20; y = rng.nextFloat() * CH; }
            else if (side == 2) { x = rng.nextFloat() * CW; y = CH + 20; }
            else { x = -20; y = rng.nextFloat() * CH; }
            return new Enemy(x, y, hpArr[idx] * scale, rArr[idx], spArr[idx], dmArr[idx] * (1 + total * 0.06f), colArr[idx], scArr[idx]);
        }

        void update(float dt) {
            if (!running || awaitingUpgrade) return;
            atkCd = dec(atkCd, dt); atkT = dec(atkT, dt);
            dashCd = dec(dashCd, dt); dashT = dec(dashT, dt);
            rageCd = dec(rageCd, dt); rageT = dec(rageT, dt);
            shieldCd = dec(shieldCd, dt); shieldT = dec(shieldT, dt);
            invuln = dec(invuln, dt); novaCd = dec(novaCd, dt);

            float dx = 0, dy = 0;
            if (keyUp) dy -= 1;
            if (keyDown) dy += 1;
            if (keyLeft) dx -= 1;
            if (keyRight) dx += 1;
            float len = (float)Math.hypot(dx, dy);
            if (len > 0) { dx /= len; dy /= len; facing = (float)Math.atan2(dy, dx); }

            float speed = dashT > 0 ? 650 : (rageT > 0 ? 220 : 170);
            px += dx * speed * dt;
            py += dy * speed * dt;
            px = Math.max(11, Math.min(CW - 11, px));
            py = Math.max(11, Math.min(CH - 11, py));

            Iterator<Enemy> it = enemies.iterator();
            while (it.hasNext()) {
                Enemy e = it.next();
                if (!e.alive) { it.remove(); continue; }
                if (e.flash > 0) e.flash -= dt;
                e.atkCd -= dt;
                float edx = px - e.x, edy = py - e.y;
                float dist = (float)Math.hypot(edx, edy);
                if (dist < 0.0001f) dist = 1;
                if (dist > pr + e.r) {
                    e.x += (edx / dist) * e.speed * dt;
                    e.y += (edy / dist) * e.speed * dt;
                } else if (e.atkCd <= 0) {
                    e.atkCd = 1;
                    if (invuln > 0) continue;
                    if (shieldT > 0) shieldT -= 0.3f;
                    else {
                        hp -= e.dmg;
                        invuln = 0.7f;
                        screenShake = 10;
                    }
                }
            }

            Iterator<Particle> pit = parts.iterator();
            while (pit.hasNext()) {
                Particle p = pit.next();
                p.x += p.vx * dt; p.y += p.vy * dt;
                p.vy += 180 * dt;
                p.vx *= 0.96f; p.vy *= 0.96f;
                p.life -= dt;
                if (p.life <= 0) pit.remove();
            }
            Iterator<DmgText> dit = dmgTexts.iterator();
            while (dit.hasNext()) {
                DmgText d = dit.next();
                d.y -= 30 * dt;
                d.life -= dt;
                if (d.life <= 0) dit.remove();
            }

            if (screenShake > 0) screenShake = Math.max(0, screenShake - dt * 40);
            if (waveMsg > 0) waveMsg -= dt;

            if (hp <= 0) { running = false; gameOver = true; return; }

            if (enemies.isEmpty() && running) {
                if (stage == 15) { running = false; worldComplete = true; }
                else spawnStage(world, stage + 1);
            }
        }

        void attack() {
            if (atkCd > 0 || dashT > 0) return;
            atkCd = rageT > 0 ? 0.16f : 0.28f;
            atkT = 0.2f;
            for (Enemy e : enemies) {
                if (e.hp <= 0) continue;
                float dx = e.x - px, dy = e.y - py;
                float dist = (float)Math.hypot(dx, dy);
                if (dist > 50 + e.r) continue;
                boolean inArc = true;
                if (dist > 18) {
                    float ang = (float)Math.atan2(dy, dx);
                    float diff = ang - facing;
                    while (diff > Math.PI) diff -= (float)Math.PI * 2;
                    while (diff < -Math.PI) diff += (float)Math.PI * 2;
                    inArc = Math.abs(diff) < 1.2f;
                }
                if (!inArc) continue;
                boolean crit = rng.nextFloat() < 0.25f;
                int dmg = (int)((12 + rng.nextFloat() * 6) * (rageT > 0 ? 2 : 1) * (crit ? 2.5f : 1));
                e.hp -= dmg;
                e.flash = 0.15f;
                dmgTexts.add(new DmgText(e.x, e.y - e.r, String.valueOf(dmg), crit ? 0xFFFFCC44 : 0xFFFFFFFF, crit ? 16 : 12));
                for (int k = 0; k < (crit ? 16 : 6); k++) {
                    float a = rng.nextFloat() * (float)Math.PI * 2;
                    float sp = 60 + rng.nextFloat() * 180;
                    parts.add(new Particle(e.x, e.y, (float)Math.cos(a) * sp, (float)Math.sin(a) * sp, 0.4f, crit ? 0xFFFFCC44 : 0xFFFF6666, crit ? 3 : 2));
                }
                if (e.hp <= 0) killEnemy(e);
                break;
            }
        }

        void doDash() {
            if (dashCd > 0) return;
            dashCd = Math.max(0.5f, 1.1f - lvlDash * 0.08f);
            dashT = 0.16f;
            invuln = 0.28f;
        }

        void doShield() {
            if (shieldCd > 0) return;
            shieldT = 0.9f + lvlShield * 0.15f;
            shieldCd = Math.max(0.8f, 1.8f - lvlShield * 0.1f);
        }

        void doRage() {
            if (rageCd > 0) return;
            rageT = 4 + lvlRage * 0.5f;
            rageCd = Math.max(6f, 12f - lvlRage * 0.8f);
        }

        void doNova() {
            if (novaCd > 0) return;
            novaCd = Math.max(4f, 8f - lvlNova * 0.4f);
            screenShake = 15;
            Iterator<Enemy> it = enemies.iterator();
            while (it.hasNext()) {
                Enemy e = it.next();
                if (Math.hypot(e.x - px, e.y - py) < 130) {
                    e.hp -= 60 + lvlNova * 20;
                    e.flash = 0.15f;
                    if (e.hp <= 0) killEnemy(e);
                }
            }
        }

        void killEnemy(Enemy e) {
            e.alive = false;
            kills++;
            score += e.score;
            xp += (int)(e.score * 0.3f);
            for (int k = 0; k < 20; k++) {
                float a = rng.nextFloat() * (float)Math.PI * 2;
                float sp = 80 + rng.nextFloat() * 200;
                parts.add(new Particle(e.x, e.y, (float)Math.cos(a) * sp, (float)Math.sin(a) * sp, 0.6f, rng.nextFloat() < 0.5f ? 0xFFFF0000 : 0xFF880000, 2));
            }
            if (xp >= xpNext) {
                xp -= xpNext;
                lvl++;
                xpNext = (int)(xpNext * 1.35f);
                awaitingUpgrade = true;
                running = false;
            }
        }

        void applyUpgrade(String id) {
            if (id.equals("dash")) lvlDash++;
            else if (id.equals("shield")) lvlShield++;
            else if (id.equals("rage")) lvlRage++;
            else if (id.equals("nova")) lvlNova++;
            awaitingUpgrade = false;
            running = true;
        }

        static float dec(float v, float d) { return v > 0 ? v - d : 0; }

        // ==================== РЕНДЕР ====================
        void render(Canvas c, Paint p) {
            int shakeX = 0, shakeY = 0;
            if (screenShake > 0) {
                shakeX = (int)((rng.nextFloat() - 0.5f) * screenShake);
                shakeY = (int)((rng.nextFloat() - 0.5f) * screenShake);
            }
            c.save();
            c.translate(shakeX, shakeY);

            p.setShader(new RadialGradient(CW / 2f, CH / 2f, CW * 0.7f, 0xFF2A1410, 0xFF0A0508, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, CW, CH, p);
            p.setShader(null);

            p.setColor(0x80000000);
            p.setStrokeWidth(1);
            for (int x = 0; x < CW; x += 32) c.drawLine(x, 0, x, CH, p);
            for (int y = 0; y < CH; y += 32) c.drawLine(0, y, CW, y, p);

            for (Enemy e : enemies) {
                if (e.hp <= 0) continue;
                p.setColor(e.flash > 0 ? 0xFFFFFFFF : e.color);
                if (e.r >= 15) c.drawRect(e.x - e.r, e.y - e.r, e.x + e.r, e.y + e.r, p);
                else c.drawCircle(e.x, e.y, e.r, p);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xFF000000);
                p.setStrokeWidth(2);
                if (e.r >= 15) c.drawRect(e.x - e.r, e.y - e.r, e.x + e.r, e.y + e.r, p);
                else c.drawCircle(e.x, e.y, e.r, p);
                p.setStyle(Paint.Style.FILL);

                p.setColor(0xFFFF0000);
                c.drawCircle(e.x - e.r * 0.35f, e.y - e.r * 0.15f, e.r * 0.16f, p);
                c.drawCircle(e.x + e.r * 0.35f, e.y - e.r * 0.15f, e.r * 0.16f, p);

                if (e.hp < e.maxHp) {
                    p.setColor(0xFF440000);
                    c.drawRect(e.x - e.r, e.y - e.r - 8, e.x + e.r, e.y - e.r - 4, p);
                    p.setColor(0xFFFF4444);
                    c.drawRect(e.x - e.r, e.y - e.r - 8, e.x - e.r + 2 * e.r * (e.hp / e.maxHp), e.y - e.r - 4, p);
                }
            }

            if (rageT > 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0x80FF8800);
                p.setStrokeWidth(3);
                c.drawCircle(px, py, 16, p);
                p.setStyle(Paint.Style.FILL);
            }
            if (shieldT > 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0x8066EEFF);
                p.setStrokeWidth(3);
                c.drawArc(new RectF(px - 20, py - 20, px + 20, py + 20),
                    (float)Math.toDegrees(facing) - 60, 120, false, p);
                p.setStyle(Paint.Style.FILL);
            }

            p.setColor(0xFF44CCFF);
            c.drawCircle(px, py, 11, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0xFF000000);
            p.setStrokeWidth(2);
            c.drawCircle(px, py, 11, p);
            p.setStyle(Paint.Style.FILL);

            float fdx = (float)Math.cos(facing), fdy = (float)Math.sin(facing);
            p.setColor(0xFFCC0000);
            p.setStrokeWidth(2);
            c.drawLine(px + fdx * 4, py + fdy * 4, px + fdx * 11, py + fdy * 11, p);

            if (atkT > 0) {
                float prog = 1 - atkT / 0.2f;
                float arcStart = facing - 1.3f + prog * 2.6f;
                float ex = px + (float)Math.cos(arcStart) * 45;
                float ey = py + (float)Math.sin(arcStart) * 45;
                p.setColor(0xFFFFFFFF);
                p.setStrokeWidth(4);
                c.drawLine(px + (float)Math.cos(arcStart) * 13, py + (float)Math.sin(arcStart) * 13, ex, ey, p);
            }

            for (Particle pa : parts) {
                int alpha = (int)(Math.min(1, pa.life * 2) * 255);
                p.setColor((alpha << 24) | (pa.color & 0xFFFFFF));
                c.drawRect(pa.x - pa.size / 2, pa.y - pa.size / 2, pa.x + pa.size / 2, pa.y + pa.size / 2, p);
            }

            for (DmgText d : dmgTexts) {
                int alpha = (int)(Math.min(1, d.life * 1.5f) * 255);
                p.setTextSize(d.size);
                p.setTextAlign(Paint.Align.CENTER);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                p.setColor(0xFF000000);
                c.drawText(d.text, d.x, d.y, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor((alpha << 24) | (d.color & 0xFFFFFF));
                c.drawText(d.text, d.x, d.y, p);
            }

            p.setShader(new RadialGradient(CW / 2f, CH / 2f, 100, 0x00000000, 0xD9000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, CW, CH, p);
            p.setShader(null);

            if (waveMsg > 0) {
                int alpha = (int)(Math.min(1, waveMsg / 0.5f) * 255);
                p.setTextSize(32);
                p.setTextAlign(Paint.Align.CENTER);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(5);
                p.setColor(0xFF000000);
                c.drawText(waveText, CW / 2, CH / 2, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor((alpha << 24) | 0xFFFFCC44);
                c.drawText(waveText, CW / 2, CH / 2, p);
            }

            c.restore();

            p.setColor(0xFF200000);
            c.drawRect(10, 10, 250, 25, p);
            p.setColor(0xFFFF4444);
            c.drawRect(10, 10, 10 + 240 * (hp / maxHp), 25, p);
            p.setTextSize(12);
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(0xFFFFFFFF);
            c.drawText((int)Math.max(0, hp) + " / " + (int)maxHp, 15, 22, p);

            c.drawText("МИР " + world + " · ЭТАП " + stage + "/15", 15, 45, p);
            c.drawText("УБИЙСТВ: " + kills, 15, 60, p);
            c.drawText("ОЧКИ: " + score, 15, 75, p);
            c.drawText("УР. " + lvl, 15, 90, p);
        }
    }
}


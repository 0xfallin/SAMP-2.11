package com.gta.launcher.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;

import com.gta.game.R;

/**
 * Tiny SoundPool wrapper for the launcher menu (tick / select / back / toggle).
 * Reference counted so a recreated activity never loses the pool.
 */
public final class MenuSounds {

    public static final int NAV = 0;
    public static final int SELECT = 1;
    public static final int BACK = 2;
    public static final int TAB = 3;
    public static final int TOGGLE = 4;
    public static final int LAUNCH = 5;

    private static final int COUNT = 6;
    private static final long NAV_MIN_GAP_MS = 35;

    private static final int[] sampleIds = new int[COUNT];
    private static final boolean[] ready = new boolean[COUNT];
    private static final float[] gain = {0.55f, 0.85f, 0.75f, 0.7f, 0.7f, 0.9f};

    private static SoundPool pool;
    private static int refCount;
    private static boolean enabled = true;
    private static long lastNavMs;

    private MenuSounds() {
    }

    public static synchronized void init(Context context) {
        refCount++;
        if (pool != null) {
            return;
        }

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build();

        pool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status != 0) {
                return;
            }
            synchronized (MenuSounds.class) {
                for (int i = 0; i < COUNT; i++) {
                    if (sampleIds[i] == sampleId) {
                        ready[i] = true;
                    }
                }
            }
        });

        Context app = context.getApplicationContext();
        sampleIds[NAV] = pool.load(app, R.raw.menu_nav, 1);
        sampleIds[SELECT] = pool.load(app, R.raw.menu_select, 1);
        sampleIds[BACK] = pool.load(app, R.raw.menu_back, 1);
        sampleIds[TAB] = pool.load(app, R.raw.menu_tab, 1);
        sampleIds[TOGGLE] = pool.load(app, R.raw.menu_toggle, 1);
        sampleIds[LAUNCH] = pool.load(app, R.raw.menu_launch, 1);
    }

    public static synchronized void release() {
        if (refCount > 0) {
            refCount--;
        }
        if (refCount == 0 && pool != null) {
            pool.release();
            pool = null;
            for (int i = 0; i < COUNT; i++) {
                ready[i] = false;
                sampleIds[i] = 0;
            }
        }
    }

    public static synchronized void setEnabled(boolean value) {
        enabled = value;
    }

    public static synchronized void play(int sound) {
        if (!enabled || pool == null || sound < 0 || sound >= COUNT || !ready[sound]) {
            return;
        }
        if (sound == NAV) {
            long now = SystemClock.uptimeMillis();
            if (now - lastNavMs < NAV_MIN_GAP_MS) {
                return;
            }
            lastNavMs = now;
        }
        float v = gain[sound];
        pool.play(sampleIds[sound], v, v, 1, 0, 1f);
    }
}

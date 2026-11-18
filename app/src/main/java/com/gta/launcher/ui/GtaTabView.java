package com.gta.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.res.ResourcesCompat;

import com.gta.game.R;
import com.gta.launcher.util.MenuSounds;

/**
 * A parallelogram navigation tab inspired by the GTA V pause menu.
 * The active tab is filled white with black text and a lime accent edge.
 * Plays a menu sound, shrinks slightly while pressed and shows an accent
 * outline when focused with a D-pad / gamepad.
 */
public class GtaTabView extends AppCompatTextView {

    private static final int COLOR_FILL = 0xB80A0C0F;
    private static final int COLOR_FILL_PRESSED = 0xE6191C21;
    private static final int COLOR_ACTIVE = 0xFFF4F5F7;
    private static final int COLOR_ACCENT = 0xFFA9C62F;
    private static final int COLOR_TEXT = 0xFFE8EAED;
    private static final int COLOR_TEXT_ACTIVE = 0xFF0A0B0D;

    private final Path shape = new Path();
    private final Path accentShape = new Path();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float skew;
    private boolean active;
    private boolean lastPressed;
    private int clickSound = MenuSounds.TAB;

    public GtaTabView(Context context) {
        super(context);
        init();
    }

    public GtaTabView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GtaTabView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        skew = dp(14f);
        fillPaint.setStyle(Paint.Style.FILL);
        accentPaint.setStyle(Paint.Style.FILL);
        accentPaint.setColor(COLOR_ACCENT);
        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeWidth(dp(1.5f));
        focusPaint.setColor(COLOR_ACCENT);

        setGravity(Gravity.CENTER_VERTICAL);
        setSingleLine(true);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        setLetterSpacing(0.14f);
        setTextColor(COLOR_TEXT);
        setPadding((int) (skew + dp(18f)), 0, (int) dp(16f), 0);
        setClickable(true);
        setFocusable(true);
        // We play our own sound, so the default system click is turned off.
        setSoundEffectsEnabled(false);

        try {
            setTypeface(ResourcesCompat.getFont(getContext(), R.font.montserrat_bold));
        } catch (Exception ignored) {
        }
    }

    /** Which menu sound this tab plays when clicked (default: tab switch). */
    public void setClickSound(int sound) {
        this.clickSound = sound;
    }

    @Override
    public boolean performClick() {
        MenuSounds.play(clickSound);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        return super.performClick();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus && isShown() && hasWindowFocus()) {
            MenuSounds.play(MenuSounds.NAV);
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        setPivotX(skew);
        setPivotY(h / 2f);

        shape.reset();
        shape.moveTo(skew, 0f);
        shape.lineTo(w, 0f);
        shape.lineTo(w - skew, h);
        shape.lineTo(0f, h);
        shape.close();

        float accentWidth = dp(4f);
        accentShape.reset();
        accentShape.moveTo(skew, 0f);
        accentShape.lineTo(skew + accentWidth, 0f);
        accentShape.lineTo(accentWidth, h);
        accentShape.lineTo(0f, h);
        accentShape.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean pressed = isPressed();

        if (active) {
            fillPaint.setColor(COLOR_ACTIVE);
        } else if (pressed) {
            fillPaint.setColor(COLOR_FILL_PRESSED);
        } else {
            fillPaint.setColor(COLOR_FILL);
        }
        canvas.drawPath(shape, fillPaint);

        if (active) {
            canvas.drawPath(accentShape, accentPaint);
        }
        if (isFocused() && !isInTouchMode()) {
            canvas.drawPath(shape, focusPaint);
        }

        super.onDraw(canvas);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        boolean pressed = isPressed();
        if (pressed != lastPressed) {
            lastPressed = pressed;
            animate().scaleX(pressed ? 0.97f : 1f).scaleY(pressed ? 0.97f : 1f)
                    .setDuration(pressed ? 70 : 140)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
        invalidate();
    }

    public void setActive(boolean active) {
        boolean changed = this.active != active;
        this.active = active;
        setTextColor(active ? COLOR_TEXT_ACTIVE : COLOR_TEXT);

        float target = active ? dp(6f) : 0f;
        if (changed && isLaidOut()) {
            animate().translationX(target)
                    .setDuration(160)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            setTranslationX(target);
        }
        setElevation(active ? dp(3f) : 0f);
        invalidate();
    }

    public boolean isActive() {
        return active;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}

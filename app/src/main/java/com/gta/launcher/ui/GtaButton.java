package com.gta.launcher.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
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
 * Angled GTA V style button. Supports the white "primary" style, a translucent
 * outlined style, a lime accent style and a flat dark style. Plays a menu
 * sound on click, shrinks while pressed and shows an outline when focused
 * with a D-pad / gamepad.
 */
public class GtaButton extends AppCompatTextView {

    public static final int STYLE_PRIMARY = 0;
    public static final int STYLE_OUTLINE = 1;
    public static final int STYLE_ACCENT = 2;
    public static final int STYLE_DARK = 3;

    private static final int COLOR_ACCENT = 0xFFA9C62F;
    private static final int COLOR_WHITE = 0xFFF4F5F7;
    private static final int COLOR_BLACK = 0xFF0A0B0D;

    private int buttonStyle = STYLE_PRIMARY;
    private float skew;
    private boolean lastPressed;
    private int clickSound = MenuSounds.SELECT;

    public GtaButton(Context context) {
        super(context);
        init(null);
    }

    public GtaButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public GtaButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        skew = dp(12f);
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setAllCaps(true);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        setLetterSpacing(0.15f);
        setIncludeFontPadding(false);
        setPadding((int) dp(22f), 0, (int) dp(22f), 0);
        setMinHeight((int) dp(44f));
        setMinWidth((int) dp(96f));
        setClickable(true);
        setFocusable(true);
        // We play our own sound, so the default system click is turned off.
        setSoundEffectsEnabled(false);

        try {
            setTypeface(ResourcesCompat.getFont(getContext(), R.font.montserrat_bold));
        } catch (Exception ignored) {
        }

        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.GtaButton);
            int style = a.getInt(R.styleable.GtaButton_gtaButtonStyle, STYLE_PRIMARY);
            skew = a.getDimension(R.styleable.GtaButton_gtaButtonSkew, skew);
            a.recycle();
            setButtonStyle(style);
        } else {
            setButtonStyle(STYLE_PRIMARY);
        }

        // The angled shape is a real background Drawable: the View framework
        // sizes and draws it itself, so it no longer depends on onSizeChanged()
        // / onDraw() ordering (the buttons were rendering with no fill).
        setBackground(new SkewBackground());
    }

    /** Which menu sound this button plays when clicked (default: select). */
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
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        boolean pressed = isPressed();
        if (pressed != lastPressed) {
            lastPressed = pressed;
            animate().scaleX(pressed ? 0.96f : 1f).scaleY(pressed ? 0.96f : 1f)
                    .setDuration(pressed ? 70 : 140)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
        invalidate();
    }

    public void setButtonStyle(int style) {
        this.buttonStyle = style;
        int textColor;
        switch (style) {
            case STYLE_OUTLINE:
            case STYLE_DARK:
                textColor = COLOR_WHITE;
                break;
            default:
                textColor = COLOR_BLACK;
                break;
        }
        setTextColor(textColor);
        invalidate();
    }

    public int getButtonStyle() {
        return buttonStyle;
    }

    /** Angled GTA V style button background, drawn from the Drawable's own bounds. */
    private final class SkewBackground extends Drawable {
        private final Path path = new Path();
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean pressed;
        private boolean focused;

        SkewBackground() {
            fill.setStyle(Paint.Style.FILL);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1f));
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean p = false;
            boolean f = false;
            for (int s : state) {
                if (s == android.R.attr.state_pressed) {
                    p = true;
                } else if (s == android.R.attr.state_focused) {
                    f = true;
                }
            }
            if (p != pressed || f != focused) {
                pressed = p;
                focused = f;
                invalidateSelf();
                return true;
            }
            return false;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) {
                return;
            }
            float l = b.left, t = b.top, r = b.right, bt = b.bottom;
            path.reset();
            path.moveTo(l + skew, t);
            path.lineTo(r, t);
            path.lineTo(r - skew, bt);
            path.lineTo(l, bt);
            path.close();

            boolean drawStroke = false;
            switch (buttonStyle) {
                case STYLE_OUTLINE:
                    fill.setColor(pressed ? 0x33FFFFFF : 0x8A0A0C0F);
                    stroke.setColor(pressed ? 0xCCFFFFFF : 0x66FFFFFF);
                    drawStroke = true;
                    break;
                case STYLE_ACCENT:
                    fill.setColor(pressed ? COLOR_WHITE : COLOR_ACCENT);
                    break;
                case STYLE_DARK:
                    fill.setColor(pressed ? 0xCC262A30 : 0xCC000000);
                    stroke.setColor(0x1FFFFFFF);
                    drawStroke = true;
                    break;
                case STYLE_PRIMARY:
                default:
                    fill.setColor(pressed ? COLOR_ACCENT : COLOR_WHITE);
                    break;
            }

            canvas.drawPath(path, fill);
            if (drawStroke) {
                canvas.drawPath(path, stroke);
            }
            if (focused && !isInTouchMode()) {
                stroke.setColor(COLOR_ACCENT);
                stroke.setStrokeWidth(dp(1.5f));
                canvas.drawPath(path, stroke);
                stroke.setStrokeWidth(dp(1f));
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}

package tv.glass.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** Shared, allocation-light material and type for the ambient launcher. */
final class NativeTheme {
    private static Typeface light, regular, medium;
    static synchronized void init(Context context) {
        if (regular != null) return;
        try {
            light = new Typeface.Builder(context.getAssets(), "fonts/outfit.ttf")
                    .setFontVariationSettings("'wght' 300").setWeight(300).build();
            regular = new Typeface.Builder(context.getAssets(), "fonts/outfit.ttf")
                    .setFontVariationSettings("'wght' 400").setWeight(400).build();
            medium = new Typeface.Builder(context.getAssets(), "fonts/outfit.ttf")
                    .setFontVariationSettings("'wght' 500").setWeight(500).build();
        } catch (RuntimeException ignored) {
            light = Typeface.create("sans-serif-light", Typeface.NORMAL);
            regular = Typeface.create("sans-serif", Typeface.NORMAL);
            medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }
    }
    static Typeface light() { return light != null ? light : Typeface.DEFAULT; }
    static Typeface regular() { return regular != null ? regular : Typeface.DEFAULT; }
    static Typeface medium() { return medium != null ? medium : Typeface.DEFAULT; }
    static int px(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().widthPixels / 1280f);
    }
    /** Text size: screen-scaled like {@link #px}, plus the user's font size
     *  setting (capped at 1.3x so fixed-height rows do not clip). */
    static int textPx(Context c, float value) {
        float scale = Math.max(1f, Math.min(1.3f, c.getResources().getConfiguration().fontScale));
        return Math.round(px(c, value) * scale);
    }
    /** Lets screen readers treat a glass row with a drawn toggle as a switch. */
    static void switchSemantics(View row, boolean checked) {
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Switch");
                info.setCheckable(true);
                info.setChecked(checked);
            }
        });
    }
    /** Speak a change only when a screen reader is on. */
    static void announce(View v, CharSequence text) {
        android.view.accessibility.AccessibilityManager am = (android.view.accessibility.AccessibilityManager)
                v.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am != null && am.isEnabled()) v.announceForAccessibility(text);
    }
    static LiquidGlassDrawable glass(Context c, float radius, boolean panel) {
        return new LiquidGlassDrawable(panel ? LiquidGlassDrawable.Style.PANEL : LiquidGlassDrawable.Style.SURFACE,
                px(c, radius), c.getResources().getDisplayMetrics().density);
    }
    static LiquidGlassDrawable row(Context c, boolean selected, float radius) {
        return new LiquidGlassDrawable(selected ? LiquidGlassDrawable.Style.SELECTED : LiquidGlassDrawable.Style.ROW,
                px(c, radius), c.getResources().getDisplayMetrics().density);
    }
    /** Set a row's glass state, reusing its drawable so focus moves do not
     *  allocate. Restores full opacity (some rows hide the idle plate). */
    static void setRow(View v, boolean selected, float radius) {
        android.graphics.drawable.Drawable bg = v.getBackground();
        if (bg instanceof LiquidGlassDrawable) {
            LiquidGlassDrawable.Style s = ((LiquidGlassDrawable) bg).style();
            if (s == LiquidGlassDrawable.Style.ROW || s == LiquidGlassDrawable.Style.SELECTED) {
                ((LiquidGlassDrawable) bg).setSelected(selected);
                bg.setAlpha(255);
                return;
            }
        }
        v.setBackground(row(v.getContext(), selected, radius));
    }
    /** The shared right-edge glass sheet used by Control Center and Bluetooth. */
    static FrameLayout sheet(Context c) {
        FrameLayout card = new FrameLayout(c);
        card.setBackground(glass(c, 30, true));
        card.setElevation(px(c, 18));
        card.setClipToOutline(true);
        return card;
    }
    static FrameLayout.LayoutParams sheetParams(Context c) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(c, ControlCenterPanel.SHEET_W), -1, Gravity.END);
        lp.topMargin = px(c, 24); lp.bottomMargin = px(c, 24); lp.setMarginEnd(px(c, 24));
        return lp;
    }
    static void sheetIn(View card) {
        card.animate().cancel();
        card.setAlpha(0f); card.setTranslationX(px(card.getContext(), 36));
        card.animate().alpha(1f).translationX(0f).setDuration(200).setInterpolator(new DecelerateInterpolator(1.6f)).withLayer().start();
    }
    static Toggle toggle(Context c, boolean on) { return new Toggle(c, on); }

    /** Small on/off switch drawn to match the glass rows. */
    static final class Toggle extends View {
        private final boolean on;
        private boolean focusedStyle;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Toggle(Context c, boolean on) { super(c); this.on = on; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void setFocusedStyle(boolean f) { focusedStyle = f; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            paint.setColor(on ? (focusedStyle ? 0xFF2E7D5B : 0xFF6ED69A) : (focusedStyle ? 0x33000000 : 0x33FFFFFF));
            canvas.drawRoundRect(0, 0, w, h, r, r, paint);
            paint.setColor(on ? 0xFFFFFFFF : (focusedStyle ? 0xFF6B6570 : 0xFFE6E1EA));
            float knob = r - px(getContext(), 3);
            canvas.drawCircle(on ? w - r : r, r, knob, paint);
        }
    }
    private NativeTheme() { }
}

package tv.glass.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

/**
 * Liquid-glass surface: an optional blurred backdrop, a tint, a soft sheen
 * near the top-left and a specular rim that is bright on the top-left and
 * bottom-right edges, like light caught in thick glass.
 *
 * <p>Android 11 has no live backdrop blur, so sheets receive a downsampled,
 * pre-blurred capture of the window from {@link GlassBackdrop}. Panels keep
 * the same dark tint with or without one; the blur fades in underneath and
 * the tint thins a little, so text contrast never depends on what is behind.
 *
 * <p>Rows switch between idle and selected with {@link #setSelected}: both
 * shader sets are built once per size, so focus moves allocate nothing.
 */
final class LiquidGlassDrawable extends Drawable {
    enum Style { PANEL, SURFACE, ROW, SELECTED }

    /** Panel tint is opaque RGB; its alpha comes from {@link #PANEL_ALPHA}. */
    private static final int[] PANEL_FILL = {0xFF3B3C43, 0xFF181C27, 0xFF28222E};
    private static final int PANEL_ALPHA = 0xE8;
    private static final int PANEL_ALPHA_ON_BACKDROP = 0xAD;
    private static final int[] SURFACE_FILL = {0x2EFFFFFF, 0x0FFFFFFF, 0x1FFFFFFF};
    private static final int[] ROW_FILL = {0x29FFFFFF, 0x0DFFFFFF, 0x1AFFFFFF};
    private static final int[] SELECTED_FILL = {0xFFF4F2F6, 0xFFE6E4EA, 0xFFEDEBF0};
    private static final float[] FILL_STOPS = {0f, .5f, 1f};
    private static final int[] RIM = {0xD9FFFFFF, 0x29FFFFFF, 0x0DFFFFFF, 0x73FFFFFF};
    private static final int[] RIM_QUIET = {0x99FFFFFF, 0x1AFFFFFF, 0x08FFFFFF, 0x4DFFFFFF};
    private static final float[] RIM_STOPS = {0f, .25f, .6f, 1f};
    private static final long BACKDROP_FADE_MS = 180;

    private Style style;
    private final float radius;
    private final float rimWidth;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    /** Shaders per style ordinal for the current bounds; rows fill two slots. */
    private final Shader[] fillShaders = new Shader[Style.values().length];
    private final Shader[] sheenShaders = new Shader[Style.values().length];
    private final Shader[] rimShaders = new Shader[Style.values().length];
    private Paint backdropPaint;
    private Matrix backdropMatrix;
    private int[] location;
    private Bitmap backdrop;
    private float backdropScale = 1f;
    private long backdropShownAt;
    private View host;
    private ColorFilter colorFilter;
    private int alpha = 255;

    LiquidGlassDrawable(Style style, float radius, float density) {
        this.style = style;
        this.radius = radius;
        this.rimWidth = Math.max(1f, density * (style == Style.PANEL ? 1.1f : 0.9f));
        fill.setStyle(Paint.Style.FILL);
        sheen.setStyle(Paint.Style.FILL);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(rimWidth);
    }

    Style style() { return style; }

    /** Switch a row between idle and selected without allocating. */
    void setSelected(boolean selected) {
        if (style != Style.ROW && style != Style.SELECTED) return;
        Style next = selected ? Style.SELECTED : Style.ROW;
        if (next == style) return;
        style = next;
        Rect b = getBounds();
        if (!b.isEmpty() && fillShaders[next.ordinal()] == null) buildShaders(b, next);
        invalidateSelf();
    }

    /** Show a blurred window capture behind this surface, fading it in.
     *  {@code scale} is capture pixels per window pixel; {@code view} is the
     *  view this drawable is the background of, used to align the capture. */
    void setBackdrop(Bitmap bitmap, float scale, View view) {
        if (bitmap == backdrop && view == host) return;
        boolean wasShowing = backdrop != null;
        backdrop = bitmap;
        backdropScale = scale;
        host = view;
        if (bitmap != null) {
            if (backdropPaint == null) {
                backdropPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                backdropMatrix = new Matrix();
                location = new int[2];
                ColorMatrix tone = new ColorMatrix();
                tone.setSaturation(1.5f);
                // Darker than the scene, so a bright tile behind stays muted.
                ColorMatrix darken = new ColorMatrix();
                darken.setScale(.78f, .78f, .78f, 1f);
                tone.postConcat(darken);
                backdropPaint.setColorFilter(new ColorMatrixColorFilter(tone));
            }
            backdropPaint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            // A replacement capture swaps in place; only the first one fades.
            if (!wasShowing) backdropShownAt = SystemClock.uptimeMillis();
        } else if (backdropPaint != null) {
            backdropPaint.setShader(null);
        }
        invalidateSelf();
    }

    @Override protected void onBoundsChange(Rect b) {
        super.onBoundsChange(b);
        java.util.Arrays.fill(fillShaders, null);
        java.util.Arrays.fill(sheenShaders, null);
        java.util.Arrays.fill(rimShaders, null);
        if (b.isEmpty()) return;
        buildShaders(b, style);
        // Rows prepare the other state too, so focus changes are free.
        if (style == Style.ROW) buildShaders(b, Style.SELECTED);
        else if (style == Style.SELECTED) buildShaders(b, Style.ROW);
    }

    private void buildShaders(Rect b, Style s) {
        int[] colors;
        switch (s) {
            case PANEL: colors = PANEL_FILL; break;
            case SURFACE: colors = SURFACE_FILL; break;
            case SELECTED: colors = SELECTED_FILL; break;
            default: colors = ROW_FILL;
        }
        int i = s.ordinal();
        fillShaders[i] = new LinearGradient(b.left, b.top, b.right, b.bottom,
                colors, FILL_STOPS, Shader.TileMode.CLAMP);
        rimShaders[i] = new LinearGradient(b.left, b.top, b.right, b.bottom,
                s == Style.SELECTED || s == Style.PANEL ? RIM : RIM_QUIET,
                RIM_STOPS, Shader.TileMode.CLAMP);
        RadialGradient glow = new RadialGradient(0f, 0f, 1f,
                new int[]{s == Style.SELECTED ? 0x66FFFFFF : 0x2EFFFFFF, 0x00FFFFFF},
                null, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(b.width() * .9f, b.height() * .6f);
        m.postTranslate(b.left + b.width() * .2f, b.top - b.height() * .1f);
        glow.setLocalMatrix(m);
        sheenShaders[i] = glow;
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty() || alpha == 0) return;
        int i = style.ordinal();
        if (fillShaders[i] == null) buildShaders(b, style);
        rect.set(b);
        int fillAlpha = 255;
        if (style == Style.PANEL) {
            fillAlpha = PANEL_ALPHA;
            if (backdrop != null && host != null) {
                float t = Math.min(1f, (SystemClock.uptimeMillis() - backdropShownAt) / (float) BACKDROP_FADE_MS);
                // Align the capture with the screen. The resting position is
                // used, so the short slide-in moves the blur with the sheet.
                host.getLocationInWindow(location);
                float x = location[0] - host.getTranslationX();
                float y = location[1] - host.getTranslationY();
                float inv = 1f / backdropScale;
                backdropMatrix.setScale(inv, inv);
                backdropMatrix.postTranslate(-x, -y);
                backdropPaint.getShader().setLocalMatrix(backdropMatrix);
                backdropPaint.setAlpha(Math.round(255 * t * alpha / 255f));
                canvas.drawRoundRect(rect, radius, radius, backdropPaint);
                fillAlpha = Math.round(PANEL_ALPHA + (PANEL_ALPHA_ON_BACKDROP - PANEL_ALPHA) * t);
                if (t < 1f) invalidateSelf();
            }
        }
        fill.setShader(fillShaders[i]);
        fill.setAlpha(fillAlpha * alpha / 255);
        canvas.drawRoundRect(rect, radius, radius, fill);
        sheen.setShader(sheenShaders[i]);
        sheen.setAlpha(alpha);
        canvas.drawRoundRect(rect, radius, radius, sheen);
        rim.setShader(rimShaders[i]);
        rim.setAlpha(alpha);
        float inset = rimWidth / 2f;
        rect.inset(inset, inset);
        float r = Math.max(0f, radius - inset);
        canvas.drawRoundRect(rect, r, r, rim);
    }

    @Override public void getOutline(Outline outline) {
        Rect b = getBounds();
        if (b.isEmpty()) outline.setEmpty();
        else outline.setRoundRect(b, radius);
        // Elevated sheets cast a full shadow; smaller glass casts a soft one.
        outline.setAlpha(style == Style.PANEL ? 1f : 0.5f);
    }

    @Override public void setAlpha(int a) {
        a = Math.max(0, Math.min(255, a));
        if (a == alpha) return;
        alpha = a;
        invalidateSelf();
    }

    @Override public int getAlpha() { return alpha; }

    @Override public void setColorFilter(ColorFilter cf) {
        colorFilter = cf;
        fill.setColorFilter(cf);
        sheen.setColorFilter(cf);
        rim.setColorFilter(cf);
        // The backdrop keeps its own tone filter; dim it with the rest by
        // folding the external filter in only when one is set.
        if (backdropPaint != null && cf != null) backdropPaint.setColorFilter(cf);
        invalidateSelf();
    }

    @Override public ColorFilter getColorFilter() { return colorFilter; }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

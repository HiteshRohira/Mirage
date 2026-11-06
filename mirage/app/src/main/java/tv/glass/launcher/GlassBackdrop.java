package tv.glass.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Backdrop blur for glass sheets on Android 11, which has no live blur.
 * Copies the launcher window at 1/8 scale with {@link PixelCopy} (a GPU copy,
 * no redraw), blurs it on a background-priority thread, then hands it to the
 * sheet that is showing when the blur finishes. Until then the sheet shows
 * its own dark tint, and the blur fades in underneath.
 *
 * <p>A capture belongs to the window it was taken from: sheets in any other
 * window (the Settings-key Control Center over an app) never receive it, and
 * it is dropped when the launcher pauses, so a sheet never shows a stale
 * picture of Home. Other windows cannot capture what is behind them, so
 * their sheets keep the tint alone.
 */
final class GlassBackdrop {
    static final float SCALE = 1f / 8f;
    private static final ExecutorService BLUR = Executors.newSingleThreadExecutor(r -> new Thread(() -> {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        r.run();
    }, "GlassBackdrop"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Bitmap latest;
    private static WeakReference<Activity> owner = new WeakReference<>(null);
    /** The sheet currently showing; a finished capture is delivered here. */
    private static WeakReference<View> target = new WeakReference<>(null);
    private static int generation;

    private GlassBackdrop() { }

    /** Capture the launcher window now and blur it, then give it to the sheet
     *  showing at that point. Call while the sheet is still hidden (the copy
     *  is of the last drawn frame) so the capture shows only what is behind. */
    static void captureFor(Activity activity, View sheet) {
        target = new WeakReference<>(sheet);
        // Drop any earlier capture this sheet still holds; the new one fades in.
        Drawable bg = sheet.getBackground();
        if (bg instanceof LiquidGlassDrawable) ((LiquidGlassDrawable) bg).setBackdrop(null, SCALE, sheet);
        if (!(activity instanceof LauncherActivity)) return;
        Window window = activity.getWindow();
        View decor = window.getDecorView();
        if (decor.getWidth() == 0 || !decor.isAttachedToWindow()) return;
        int w = Math.max(1, Math.round(decor.getWidth() * SCALE));
        int h = Math.max(1, Math.round(decor.getHeight() * SCALE));
        final Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            return;
        }
        final int id = ++generation;
        final WeakReference<Activity> source = new WeakReference<>(activity);
        try {
            PixelCopy.request(window, bitmap, result -> {
                if (result != PixelCopy.SUCCESS || id != generation) { bitmap.recycle(); return; }
                BLUR.execute(() -> {
                    boolean ok;
                    try {
                        LegacyGridBlur.apply(bitmap, 3, 3);
                        ok = true;
                    } catch (Throwable t) {
                        ok = false;
                    }
                    final boolean blurred = ok;
                    MAIN.post(() -> {
                        if (!blurred || id != generation || source.get() == null) { bitmap.recycle(); return; }
                        latest = bitmap;
                        owner = source;
                        View sheet2 = target.get();
                        if (sheet2 != null) attach(sheet2);
                    });
                });
            }, MAIN);
        } catch (IllegalArgumentException e) {
            bitmap.recycle();
        }
    }

    /** Point a sheet at the current capture, if it belongs to the sheet's
     *  window. Also makes this sheet the one a pending capture goes to. */
    static void attach(View sheet) {
        target = new WeakReference<>(sheet);
        Drawable bg = sheet.getBackground();
        if (!(bg instanceof LiquidGlassDrawable)) return;
        Activity source = owner.get();
        boolean usable = latest != null && !latest.isRecycled() && source != null
                && sheet.getContext() == source;
        ((LiquidGlassDrawable) bg).setBackdrop(usable ? latest : null, SCALE, sheet);
    }

    /** Forget the capture (the launcher paused, so what is behind may change).
     *  Drawables still holding it keep a valid bitmap; it is never recycled
     *  here, only released for collection. */
    static void clear() {
        generation++;
        latest = null;
        owner = new WeakReference<>(null);
        target = new WeakReference<>(null);
    }
}

package add;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/**
 * The grab zone at the top of a sheet.
 *
 * <p>Pulling down past a short distance closes the sheet. The window itself travels
 * with the finger, so the card slides off the bottom of the screen instead of being
 * clipped by the edge of a window that ends where the card does.
 *
 * <p>On the two sheets that carry a list -- presets and layers -- pulling up grows that
 * list until the card meets the top of the screen, and a release snaps to whichever end
 * is nearer. A sheet whose controls already fit does not move upward: it has no more
 * height to give. Dragging down while the list is grown collapses it first, then carries
 * the sheet off, so a thumb never has to guess which gesture it is making.
 */
final class SheetDrag implements View.OnTouchListener {

    private final Dialog dialog;
    private final View header;
    private final View panel;
    private final View expandable;
    private final int baseHeight;
    private final float density;
    private final int touchSlop;
    private final int fling;
    private final String key;
    private final SharedPreferences prefs;

    private VelocityTracker tracker;
    private float startY;
    private int startHeight;
    private int limit;
    private int windowY;
    private boolean moved;

    SheetDrag(Dialog dialog, View header, View panel, View expandable, int baseHeight) {
        this.dialog = dialog;
        this.header = header;
        this.panel = panel;
        this.expandable = expandable;
        this.baseHeight = baseHeight;
        this.density = panel.getResources().getDisplayMetrics().density;
        this.touchSlop = ViewConfiguration.get(panel.getContext()).getScaledTouchSlop();
        this.fling = Math.round(900 * density);
        // The list's tag names the sheet, so its height survives a dismissal and a
        // restart in the same preferences file as the palette and the saved looks.
        String tag = expandable == null ? null : String.valueOf(expandable.getTag());
        this.key = tag == null || "null".equals(tag) ? null : "editor_sheet_" + tag;
        this.prefs = key == null ? null : panel.getContext()
                .getSharedPreferences("compress_settings", Context.MODE_PRIVATE);
    }

    /** Reopens the list at the height it was left, once the dialog has a size. */
    void restore() {
        if (key == null || prefs == null) {
            return;
        }
        if (panel.getHeight() == 0) {
            if (dialog.isShowing()) {
                panel.postDelayed(this::restore, 32);
            }
            return;
        }
        limit = maxHeight();
        if (prefs.getBoolean(key, false) && limit > baseHeight) {
            setHeight(limit);
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                begin(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                follow(event);
                return true;
            case MotionEvent.ACTION_UP:
                settle(event);
                return true;
            case MotionEvent.ACTION_CANCEL:
                springBack();
                return true;
            default:
                return true;
        }
    }

    private void begin(MotionEvent event) {
        if (tracker != null) {
            tracker.recycle();
        }
        tracker = VelocityTracker.obtain();
        tracker.addMovement(event);
        startY = event.getRawY();
        startHeight = height();
        windowY = windowOffset();
        limit = maxHeight();
        moved = false;
        disallow(true);
    }

    private void follow(MotionEvent event) {
        if (tracker == null) {
            return;
        }
        tracker.addMovement(event);
        float dy = event.getRawY() - startY;
        if (!moved && Math.abs(dy) < touchSlop) {
            return;
        }
        moved = true;
        if (expandable == null) {
            moveWindow((int) Math.max(0, dy));
            return;
        }
        if (dy < 0) {
            setHeight(startHeight + (int) -dy);
            moveWindow(0);
            return;
        }
        int room = Math.max(0, startHeight - baseHeight);
        int used = (int) Math.min(dy, room);
        setHeight(startHeight - used);
        moveWindow((int) dy - used);
    }

    private void settle(MotionEvent event) {
        if (tracker == null) {
            return;
        }
        tracker.addMovement(event);
        tracker.computeCurrentVelocity(1000);
        float velocity = tracker.getYVelocity();
        int threshold = Math.max(dp(80), Math.round(panel.getHeight() * 0.28f));
        if (windowY > threshold || (velocity > fling && windowY > touchSlop)) {
            slideOut();
            return;
        }
        if (!moved) {
            // A tap on the header toggles the list, for a thumb that does not drag.
            disallow(false);
            if (expandable != null) {
                limit = maxHeight();
                if (limit > baseHeight) {
                    int target = height() > baseHeight ? baseHeight : limit;
                    animateHeight(target);
                    remember(target > baseHeight);
                }
            }
            finish();
            return;
        }
        disallow(false);
        if (windowY > 0) {
            animateWindow(0);
        }
        if (expandable != null) {
            limit = maxHeight();
            int now = height();
            int span = limit - baseHeight;
            boolean open = span > 0 && (velocity < -fling
                    ? now > baseHeight
                    : now > baseHeight + span / 3);
            animateHeight(open ? limit : baseHeight);
            remember(open);
        }
        finish();
    }

    private void springBack() {
        disallow(false);
        if (windowY > 0) {
            animateWindow(0);
        }
        if (expandable != null) {
            animateHeight(baseHeight);
        }
        finish();
    }

    private void slideOut() {
        disallow(false);
        int target = panel.getHeight() + dp(48);
        ValueAnimator animator = ValueAnimator.ofInt(windowY, target);
        animator.setDuration(200);
        animator.setInterpolator(new AccelerateInterpolator());
        animator.addUpdateListener(animation -> moveWindow((int) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
                finish();
            }
        });
        animator.start();
    }

    private void animateWindow(int target) {
        if (target == windowY) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofInt(windowY, target);
        animator.setDuration(200);
        animator.setInterpolator(target > windowY
                ? new AccelerateInterpolator() : new DecelerateInterpolator());
        animator.addUpdateListener(animation -> moveWindow((int) animation.getAnimatedValue()));
        animator.start();
    }

    private void animateHeight(int target) {
        if (expandable == null) {
            return;
        }
        int from = height();
        if (from == target) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofInt(from, target);
        animator.setDuration(180);
        animator.setInterpolator(target > from
                ? new DecelerateInterpolator() : new AccelerateInterpolator());
        animator.addUpdateListener(animation -> setHeight((int) animation.getAnimatedValue()));
        animator.start();
    }

    /** The tallest the list may become: the card reaches the top of the screen. */
    private int maxHeight() {
        if (expandable == null) {
            return 0;
        }
        int screen = panel.getResources().getDisplayMetrics().heightPixels;
        int rest = Math.max(0, panel.getHeight() - height());
        return Math.max(baseHeight, screen - dp(64) - rest);
    }

    private int height() {
        if (expandable == null) {
            return 0;
        }
        return expandable.getLayoutParams().height;
    }

    private void setHeight(int value) {
        if (expandable == null) {
            return;
        }
        int clamped = Math.max(baseHeight, Math.min(value, limit));
        ViewGroup.LayoutParams params = expandable.getLayoutParams();
        if (params.height == clamped) {
            return;
        }
        params.height = clamped;
        expandable.setLayoutParams(params);
    }

    private void moveWindow(int value) {
        int y = Math.max(0, value);
        Window window = dialog.getWindow();
        if (window == null || !dialog.isShowing()) {
            return;
        }
        WindowManager.LayoutParams params = window.getAttributes();
        if (params.y != y) {
            params.y = y;
            window.setAttributes(params);
        }
        windowY = y;
    }

    private int windowOffset() {
        Window window = dialog.getWindow();
        return window == null ? 0 : window.getAttributes().y;
    }

    /** Keeps the platform ScrollView behind a dialog from stealing the gesture. */
    private void disallow(boolean value) {
        ViewParent parent = header.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(value);
        }
    }

    private void remember(boolean open) {
        if (key == null || prefs == null) {
            return;
        }
        prefs.edit().putBoolean(key, open).apply();
    }

    private int dp(int value) {
        return Math.round(value * density);
    }

    private void finish() {
        if (tracker != null) {
            tracker.recycle();
            tracker = null;
        }
    }
}

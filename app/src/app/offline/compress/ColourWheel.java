package app.offline.compress;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * A hue-and-saturation wheel: the angle picks the hue and the distance from the centre
 * the saturation. Brightness belongs to the sheet's own bar, so the disc stays fully
 * lit even when the chosen colour is black -- a wheel that turns black with the value
 * reads as broken.
 *
 * <p>The disc is rendered once into a bitmap at the size it is laid out and cached;
 * only the selection ring is redrawn as the thumb moves.
 */
final class ColourWheel extends View {

    interface Listener {
        void onColour(int colour);
    }

    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private Bitmap disc;
    private int discSize;
    private float hue;
    private float saturation;
    private float brightness = 1f;
    private Listener listener;

    ColourWheel(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(2.5f));
        ring.setColor(Color.WHITE);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(dp(1.5f));
        outline.setColor(0x99000000);
        setContentDescription("Colour wheel");
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setColour(int colour) {
        float[] hsv = new float[3];
        Color.colorToHSV(colour, hsv);
        hue = hsv[0];
        saturation = hsv[1];
        brightness = hsv[2];
        invalidate();
    }

    void setBrightness(float value) {
        brightness = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    float brightness() {
        return brightness;
    }

    int colour() {
        return Color.HSVToColor(new float[]{hue, saturation, brightness});
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int size = Math.min(getWidth(), getHeight());
        if (size <= 0) {
            return;
        }
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = size / 2f - dp(1.5f);
        if (disc == null || discSize != size) {
            buildDisc(size);
        }
        canvas.drawBitmap(disc, cx - size / 2f, cy - size / 2f, discPaint);

        double angle = Math.toRadians(hue);
        float mx = cx + (float) Math.cos(angle) * saturation * radius;
        float my = cy + (float) Math.sin(angle) * saturation * radius;
        canvas.drawCircle(mx, my, dp(7), outline);
        canvas.drawCircle(mx, my, dp(6), ring);
    }

    private void buildDisc(int size) {
        int[] pixels = new int[size * size];
        float radius = size / 2f;
        float[] hsv = new float[3];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x + 0.5f - radius;
                float dy = y + 0.5f - radius;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float edge = radius - dist;
                if (edge <= 0f) {
                    continue;
                }
                hsv[0] = (float) Math.toDegrees(Math.atan2(dy, dx));
                if (hsv[0] < 0f) {
                    hsv[0] += 360f;
                }
                hsv[1] = Math.min(1f, dist / radius);
                hsv[2] = 1f;
                int alpha = Math.round(Math.min(1f, edge) * 255f);
                pixels[y * size + x] = Color.HSVToColor(alpha, hsv);
            }
        }
        disc = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
        discSize = size;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                pick(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                pick(event);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            default:
                return true;
        }
    }

    private void pick(MotionEvent event) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.max(1f, Math.min(getWidth(), getHeight()) / 2f - dp(1.5f));
        float dx = event.getX() - cx;
        float dy = event.getY() - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > radius) {
            dx = dx * radius / dist;
            dy = dy * radius / dist;
            dist = radius;
        }
        saturation = Math.min(1f, dist / radius);
        hue = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (hue < 0f) {
            hue += 360f;
        }
        invalidate();
        if (listener != null) {
            listener.onColour(colour());
        }
    }

    private float dp(float value) {
        return value * density;
    }
}

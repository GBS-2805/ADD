package app.offline.compress;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * The editor's stage: one photo, any number of type lines, and six effects.
 *
 * The canvas is the photo's own rectangle, letterboxed inside the view; with no photo
 * it is a 4:5 card. A line stores its position and its size as fractions of that
 * rectangle, so the preview and the export agree whatever the pixels are -- the photo
 * is fitted to the same shape in both, and the export simply runs the same draw calls
 * against a bigger canvas.
 */
public final class EditorView extends View {

    static final String[] EFFECTS = {"None", "Mono", "Warm", "Cool", "Fade", "Contrast"};

    /** One line of type. Mutable on purpose: the activity edits it through the view. */
    static final class Layer {
        String text;
        Typeface face;
        String faceLabel;
        float sizeFrac;
        int colour;
        float cx;
        float cy;

        Layer(String text, Typeface face, String faceLabel, float sizeFrac, int colour,
              float cx, float cy) {
            this.text = text;
            this.face = face;
            this.faceLabel = faceLabel;
            this.sizeFrac = sizeFrac;
            this.colour = colour;
            this.cx = cx;
            this.cy = cy;
        }
    }

    interface Listener {
        void onSelect(Layer layer);

        void onEdit(Layer layer);
    }

    private static final long LONG_PRESS = 450;
    private static final float CARD_ASPECT = 1080f / 1350f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Layer> layers = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;

    private Bitmap image;
    private int effect;
    private ColorMatrixColorFilter filter;
    private Layer selected;
    private Listener listener;

    private Layer pressed;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private boolean dragging;
    private boolean moved;
    private int selectionColour = Color.WHITE;

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (pressed != null && !moved && listener != null) {
                selected = pressed;
                listener.onEdit(pressed);
                invalidate();
            }
        }
    };

    public EditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        paint.setTextAlign(Paint.Align.CENTER);
        fillPaint.setColor(Color.WHITE);
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(dp(1.5f));
        selectionPaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{dp(6), dp(4)}, 0));
        setFocusable(true);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setImage(Bitmap bitmap) {
        image = bitmap;
        invalidate();
    }

    boolean hasImage() {
        return image != null;
    }

    int layerCount() {
        return layers.size();
    }

    Layer selected() {
        return selected;
    }

    void setSelectionColour(int colour) {
        selectionColour = colour;
        selectionPaint.setColor(colour);
    }

    Layer addText(String text, Typeface face, String label, float sizeFrac, int colour) {
        float offset = 0.04f * layers.size();
        Layer layer = new Layer(text, face, label, sizeFrac, colour, 0.5f, 0.45f + offset);
        if (layer.cy > 0.82f) {
            layer.cy = 0.82f;
        }
        layers.add(layer);
        selected = layer;
        invalidate();
        return layer;
    }

    void removeSelected() {
        if (selected != null) {
            layers.remove(selected);
            selected = null;
            invalidate();
        }
    }

    void clearText() {
        layers.clear();
        selected = null;
        invalidate();
    }

    void setFace(Typeface face, String label) {
        if (selected != null) {
            selected.face = face;
            selected.faceLabel = label;
            invalidate();
        }
    }

    void setSize(float sizeFrac) {
        if (selected != null) {
            selected.sizeFrac = sizeFrac;
            invalidate();
        }
    }

    void setColour(int colour) {
        if (selected != null) {
            selected.colour = colour;
            invalidate();
        }
    }

    void setEffect(int index) {
        effect = index;
        filter = null;
        invalidate();
    }

    int effect() {
        return effect;
    }

    void editText(Layer layer, String text) {
        if (layer != null) {
            layer.text = text;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            return;
        }
        RectF rect = canvasRect(width, height);
        canvas.drawRect(rect, fillPaint);
        drawPhoto(canvas, rect);
        for (Layer layer : layers) {
            drawLayer(canvas, layer, rect);
        }
        if (selected != null && layers.contains(selected)) {
            selectionPaint.setColor(selectionColour);
            canvas.drawRect(bounds(selected, rect), selectionPaint);
        }
    }

    /**
     * The photo's shape, fitted inside the view. The type is laid out against this
     * rectangle, not the view, which is what makes the export match the preview.
     */
    private RectF canvasRect(int width, int height) {
        float aspect = image == null
                ? CARD_ASPECT
                : image.getWidth() / (float) image.getHeight();
        float w = width;
        float h = width / aspect;
        if (h > height) {
            h = height;
            w = height * aspect;
        }
        float left = (width - w) / 2f;
        float top = (height - h) / 2f;
        return new RectF(left, top, left + w, top + h);
    }

    private void drawPhoto(Canvas canvas, RectF rect) {
        if (image == null) {
            return;
        }
        paint.reset();
        paint.setAntiAlias(true);
        paint.setColorFilter(filter());
        canvas.drawBitmap(image, null, rect, paint);
        paint.setColorFilter(null);
    }

    private ColorMatrixColorFilter filter() {
        if (effect == 0) {
            return null;
        }
        if (filter != null) {
            return filter;
        }
        ColorMatrix matrix = new ColorMatrix();
        switch (effect) {
            case 1:
                matrix.setSaturation(0f);
                break;
            case 2:
                matrix.set(new ColorMatrix(new float[]{
                        1.06f, 0, 0, 0, 10,
                        0, 1.01f, 0, 0, 3,
                        0, 0, 0.90f, 0, -4,
                        0, 0, 0, 1, 0}));
                break;
            case 3:
                matrix.set(new ColorMatrix(new float[]{
                        0.90f, 0, 0, 0, -2,
                        0, 0.99f, 0, 0, 2,
                        0, 0, 1.10f, 0, 6,
                        0, 0, 0, 1, 0}));
                break;
            case 4:
                matrix.set(new ColorMatrix(new float[]{
                        0.82f, 0, 0, 0, 34,
                        0, 0.82f, 0, 0, 34,
                        0, 0, 0.82f, 0, 34,
                        0, 0, 0, 1, 0}));
                break;
            case 5:
                matrix.set(new ColorMatrix(new float[]{
                        1.22f, 0, 0, 0, -28,
                        0, 1.22f, 0, 0, -28,
                        0, 0, 1.22f, 0, -28,
                        0, 0, 0, 1, 0}));
                break;
            default:
                return null;
        }
        filter = new ColorMatrixColorFilter(matrix);
        return filter;
    }

    private void drawLayer(Canvas canvas, Layer layer, RectF rect) {
        int min = Math.round(Math.min(rect.width(), rect.height()));
        paint.reset();
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(layer.face);
        paint.setColor(layer.colour);
        paint.setTextSize(Math.max(dp(6), layer.sizeFrac * min));
        if (image != null) {
            paint.setShadowLayer(Math.max(1f, paint.getTextSize() * 0.06f),
                    0, Math.max(1f, paint.getTextSize() * 0.025f), 0x66000000);
        }
        List<String> lines = wrap(paint, layer.text, rect.width() * 0.9f);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float lineHeight = (metrics.descent - metrics.ascent) * 1.12f;
        float total = lineHeight * lines.size();
        float top = rect.top + layer.cy * rect.height() - total / 2f;
        float x = rect.left + layer.cx * rect.width();
        for (int i = 0; i < lines.size(); i++) {
            float baseline = top + (-metrics.ascent) + i * lineHeight;
            canvas.drawText(lines.get(i), x, baseline, paint);
        }
    }

    /** Words wrap inside 90% of the canvas; a typed newline always breaks. */
    private static List<String> wrap(Paint paint, String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        String value = text == null ? "" : text;
        for (String paragraph : value.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (paint.measureText(candidate) > maxWidth && line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private RectF bounds(Layer layer, RectF rect) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setTypeface(layer.face);
        paint.setTextSize(Math.max(dp(6),
                layer.sizeFrac * Math.min(rect.width(), rect.height())));
        List<String> lines = wrap(paint, layer.text, rect.width() * 0.9f);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float lineHeight = (metrics.descent - metrics.ascent) * 1.12f;
        float total = lineHeight * lines.size();
        float widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, paint.measureText(line));
        }
        float pad = dp(10);
        float cx = rect.left + layer.cx * rect.width();
        float cy = rect.top + layer.cy * rect.height();
        return new RectF(cx - widest / 2f - pad, cy - total / 2f - pad,
                cx + widest / 2f + pad, cy + total / 2f + pad);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        RectF rect = canvasRect(getWidth(), getHeight());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = pick(rect, event.getX(), event.getY());
                selected = pressed;
                dragging = pressed != null;
                moved = false;
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                handler.removeCallbacks(longPress);
                if (pressed != null) {
                    handler.postDelayed(longPress, LONG_PRESS);
                }
                invalidate();
                if (listener != null) {
                    // A tap on empty space reports the loss of a selection too.
                    listener.onSelect(pressed);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging && pressed != null) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    if (Math.abs(event.getX() - downX) > dp(4) || Math.abs(event.getY() - downY) > dp(4)) {
                        moved = true;
                        handler.removeCallbacks(longPress);
                    }
                    pressed.cx = clamp(pressed.cx + dx / rect.width(), 0.04f, 0.96f);
                    pressed.cy = clamp(pressed.cy + dy / rect.height(), 0.04f, 0.96f);
                    lastX = event.getX();
                    lastY = event.getY();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPress);
                dragging = false;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private Layer pick(RectF rect, float x, float y) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            if (bounds(layer, rect).contains(x, y)) {
                return layer;
            }
        }
        return null;
    }

    /** The export: the same layout on the photo's own pixels, capped at 2048 a side. */
    Bitmap render() {
        int width;
        int height;
        if (image != null) {
            float scale = Math.min(1f, 2048f / Math.max(image.getWidth(), image.getHeight()));
            width = Math.max(1, Math.round(image.getWidth() * scale));
            height = Math.max(1, Math.round(image.getHeight() * scale));
        } else {
            width = 1080;
            height = 1350;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        RectF rect = new RectF(0, 0, width, height);
        canvas.drawRect(rect, fillPaint);
        drawPhoto(canvas, rect);
        for (Layer layer : layers) {
            drawLayer(canvas, layer, rect);
        }
        return bitmap;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * density;
    }
}

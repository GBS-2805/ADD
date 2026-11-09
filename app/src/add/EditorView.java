package add;

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
 * The editor's stage: one photo, any number of type lines, seven photo presets and
 * five live adjustments.
 *
 * The canvas is the photo's own rectangle, letterboxed inside the view; with no photo
 * it is a 4:5 card. A line stores its position and its size as fractions of that
 * rectangle, so the preview and the export agree whatever the pixels are -- the photo
 * is fitted to the same shape in both, and the export simply runs the same draw calls
 * against a bigger canvas.
 */
public final class EditorView extends View {

    static final String[] PRESETS = {
            "Original", "Mono", "Warm", "Cool", "Fade", "Punch", "Film", "Custom"
    };
    static final int CUSTOM = 7;
    static final String[] ADJUSTMENTS = {
            "Exposure", "Contrast", "Saturation", "Warmth", "Fade"
    };

    /**
     * A preset is just the five adjustment values below, so a preset and a hand-tuned
     * look travel through exactly the same colour matrix.
     */
    private static final int[][] PRESET_ADJUST = {
            {0, 0, 0, 0, 0},
            {0, 0, -100, 0, 0},
            {4, 2, 4, 35, 0},
            {0, 2, 2, -35, 0},
            {6, -12, -4, 0, 55},
            {2, 22, 18, 4, 0},
            {2, 8, -8, 12, 20}
    };

    /** One line of type. Mutable on purpose: the activity edits it through the view. */
    static final class Layer {
        String text;
        Typeface face;
        String faceLabel;
        float sizeFrac;
        int colour;
        float cx;
        float cy;
        /** 0 left, 1 centre, 2 right, inside the line's own text block. */
        int align = 1;
        /** Em units, the same unit Paint#setLetterSpacing uses. */
        float letterSpacing;
        /** Multiplier on the font's natural line height. */
        float lineSpacing = 1.12f;
        int opacity = 255;
        boolean shadow = true;
        boolean visible = true;

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

    /**
     * One line's type treatment, the part a saved look carries: what the TYPE sheet
     * changes, without the text, face, size or position, which belong to the document
     * rather than the look.
     */
    static final class TypeStyle {
        final int colour;
        final int align;
        final float letterSpacing;
        final float lineSpacing;
        final int opacity;
        final boolean shadow;

        TypeStyle(int colour, int align, float letterSpacing, float lineSpacing,
                  int opacity, boolean shadow) {
            this.colour = colour;
            this.align = align;
            this.letterSpacing = letterSpacing;
            this.lineSpacing = lineSpacing;
            this.opacity = opacity;
            this.shadow = shadow;
        }
    }

    interface Listener {
        void onSelect(Layer layer);

        void onEdit(Layer layer);

        /** A change that alters the document, which retires the look undo. */
        void onChange();
    }

    private static final long LONG_PRESS = 450;
    private static final float CARD_ASPECT = 1080f / 1350f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Layer> layers = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;
    private final int[] adjust = new int[ADJUSTMENTS.length];
    private final int[] customAdjust = new int[ADJUSTMENTS.length];

    private Bitmap image;
    private int preset = 0;
    /** The saved look in front, so the band can name it until something is tuned. */
    private String lookName;
    private ColorMatrixColorFilter filter;
    private Layer selected;
    private Listener listener;
    private int rotation;
    private boolean flipHorizontal;
    private boolean flipVertical;
    private int canvasColour = Color.WHITE;

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
        paint.setTextAlign(Paint.Align.LEFT);
        fillPaint.setColor(canvasColour);
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(dp(1.5f));
        selectionPaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{dp(6), dp(4)}, 0));
        setFocusable(true);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Every edit comes through here: it repaints and tells the screen, which drops the
     * pending look undo. Selecting a line is not an edit and does not come this way.
     */
    private void changed() {
        invalidate();
        if (listener != null) {
            listener.onChange();
        }
    }

    void setImage(Bitmap bitmap) {
        image = bitmap;
        rotation = 0;
        flipHorizontal = false;
        flipVertical = false;
        changed();
    }

    int canvasColour() {
        return canvasColour;
    }

    void setCanvasColour(int colour) {
        canvasColour = colour;
        fillPaint.setColor(colour);
        changed();
    }

    void rotate90() {
        if (image != null) {
            rotation = (rotation + 90) % 360;
            changed();
        }
    }

    void flipHorizontal() {
        if (image != null) {
            flipHorizontal = !flipHorizontal;
            changed();
        }
    }

    void flipVertical() {
        if (image != null) {
            flipVertical = !flipVertical;
            changed();
        }
    }

    void resetTransform() {
        rotation = 0;
        flipHorizontal = false;
        flipVertical = false;
        changed();
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

    List<Layer> layerList() {
        return new ArrayList<>(layers);
    }

    void selectLayer(Layer layer) {
        if (layers.contains(layer)) {
            selected = layer;
            invalidate();
        }
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
        changed();
        return layer;
    }

    void removeSelected() {
        if (selected != null) {
            layers.remove(selected);
            selected = null;
            changed();
        }
    }

    Layer duplicateSelected() {
        if (selected == null) {
            return null;
        }
        Layer copy = new Layer(selected.text, selected.face, selected.faceLabel,
                selected.sizeFrac, selected.colour,
                clamp(selected.cx + 0.035f, 0.04f, 0.96f),
                clamp(selected.cy + 0.035f, 0.04f, 0.96f));
        copy.align = selected.align;
        copy.letterSpacing = selected.letterSpacing;
        copy.lineSpacing = selected.lineSpacing;
        copy.opacity = selected.opacity;
        copy.shadow = selected.shadow;
        copy.visible = selected.visible;
        layers.add(layers.indexOf(selected) + 1, copy);
        selected = copy;
        changed();
        return copy;
    }

    void centerSelected() {
        if (selected != null) {
            selected.cx = 0.5f;
            selected.cy = 0.5f;
            changed();
        }
    }

    /**
     * Moves a layer to a row in the layer panel, where row 0 is the front-most layer.
     * The paint order is the reverse of that panel, which this conversion handles.
     */
    void moveLayerToDisplay(Layer layer, int displayIndex) {
        int from = layers.indexOf(layer);
        if (from < 0 || layers.isEmpty()) {
            return;
        }
        int target = layers.size() - 1 - clamp(displayIndex, 0, layers.size() - 1);
        layers.remove(from);
        layers.add(target, layer);
        selected = layer;
        changed();
    }

    void clearText() {
        layers.clear();
        selected = null;
        changed();
    }

    void setFace(Typeface face, String label) {
        if (selected != null) {
            selected.face = face;
            selected.faceLabel = label;
            changed();
        }
    }

    void setSize(float sizeFrac) {
        if (selected != null) {
            selected.sizeFrac = sizeFrac;
            changed();
        }
    }

    void setColour(int colour) {
        if (selected != null) {
            selected.colour = colour;
            changed();
        }
    }

    void setAlign(int align) {
        if (selected != null) {
            selected.align = clamp(align, 0, 2);
            changed();
        }
    }

    void setLetterSpacing(float value) {
        if (selected != null) {
            selected.letterSpacing = clamp(value, -0.02f, 0.3f);
            changed();
        }
    }

    void setLineSpacing(float value) {
        if (selected != null) {
            selected.lineSpacing = clamp(value, 0.8f, 1.8f);
            changed();
        }
    }

    void setOpacity(int value) {
        if (selected != null) {
            selected.opacity = clamp(value, 0, 255);
            changed();
        }
    }

    void setShadow(boolean enabled) {
        if (selected != null) {
            selected.shadow = enabled;
            changed();
        }
    }

    void toggleVisible(Layer layer) {
        if (layer != null) {
            layer.visible = !layer.visible;
            changed();
        }
    }

    void applyPreset(int index) {
        lookName = null;
        if (index == CUSTOM) {
            System.arraycopy(customAdjust, 0, adjust, 0, adjust.length);
            preset = CUSTOM;
            filter = null;
            changed();
            return;
        }
        if (index < 0 || index >= PRESET_ADJUST.length) {
            return;
        }
        System.arraycopy(PRESET_ADJUST[index], 0, adjust, 0, adjust.length);
        preset = index;
        filter = null;
        changed();
    }

    int preset() {
        return preset;
    }

    String presetName() {
        if (preset == CUSTOM && lookName != null) {
            return lookName;
        }
        return PRESETS[preset < 0 || preset >= PRESETS.length ? CUSTOM : preset];
    }

    /** The saved look in front, or null once a slider hand-tunes it. */
    String lookName() {
        return lookName;
    }

    int adjustment(int which) {
        if (which < 0 || which >= adjust.length) {
            return 0;
        }
        return adjust[which];
    }

    void setAdjustment(int which, int value) {
        if (which < 0 || which >= adjust.length) {
            return;
        }
        adjust[which] = clamp(value, -100, 100);
        System.arraycopy(adjust, 0, customAdjust, 0, adjust.length);
        lookName = null;
        preset = CUSTOM;
        filter = null;
        changed();
    }

    /** The five values that make the current look, for saving and thumbnail previews. */
    int[] adjustments() {
        return adjust.clone();
    }

    /**
     * Applies a saved look, the same five values a preset carries. A saved look has a
     * name, so the band reports that name until a slider hand-tunes the look again.
     */
    void applyLook(int[] values, String name) {
        if (values == null || values.length != adjust.length) {
            return;
        }
        System.arraycopy(values, 0, adjust, 0, adjust.length);
        System.arraycopy(values, 0, customAdjust, 0, customAdjust.length);
        lookName = name;
        preset = CUSTOM;
        filter = null;
        changed();
    }

    /**
     * Every line's type treatment, in drawing order. A saved look keeps one per line,
     * so the whole poster -- not just the line that happened to be selected -- can come
     * back in one tap.
     */
    List<TypeStyle> typeStyles() {
        ArrayList<TypeStyle> styles = new ArrayList<>(layers.size());
        for (Layer layer : layers) {
            styles.add(styleOf(layer));
        }
        return styles;
    }

    /**
     * Applies a saved look's type treatments by position. A shorter list leaves the
     * lines it does not reach alone, so a poster of a different shape is never wiped
     * past the end of the look.
     */
    void applyTypeStyles(List<TypeStyle> styles) {
        if (styles == null) {
            return;
        }
        int count = Math.min(layers.size(), styles.size());
        for (int i = 0; i < count; i++) {
            TypeStyle style = styles.get(i);
            if (style == null) {
                continue;
            }
            Layer layer = layers.get(i);
            layer.colour = style.colour;
            layer.align = Math.max(0, Math.min(2, style.align));
            layer.letterSpacing = Math.max(-0.02f, Math.min(0.3f, style.letterSpacing));
            layer.lineSpacing = Math.max(0.8f, Math.min(1.8f, style.lineSpacing));
            layer.opacity = Math.max(0, Math.min(255, style.opacity));
            layer.shadow = style.shadow;
        }
        changed();
    }

    private TypeStyle styleOf(Layer layer) {
        return new TypeStyle(layer.colour, layer.align, layer.letterSpacing,
                layer.lineSpacing, layer.opacity, layer.shadow);
    }

    void editText(Layer layer, String text) {
        if (layer != null) {
            layer.text = text;
            changed();
        }
    }

    /**
     * A small rendered version of the current canvas with one preset applied. The
     * preset dialog uses these as its thumbnails, so the choice is judged on the
     * photograph rather than on a swatch.
     */
    Bitmap thumbnail(int presetIndex, int width, int height) {
        int[] values = presetIndex == CUSTOM || presetIndex < 0
                || presetIndex >= PRESET_ADJUST.length ? customAdjust : PRESET_ADJUST[presetIndex];
        return thumbnail(values, width, height);
    }

    /** The same, for a look that is not one of the built-ins -- a saved style. */
    Bitmap thumbnail(int[] values, int width, int height) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(fillPaint.getColor());
        RectF rect = canvasRect(w, h);
        drawComposition(canvas, rect, filterFor(values), false);
        return bitmap;
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
        drawComposition(canvas, rect, filter(), true);
    }

    /**
     * The photo's shape, fitted inside the view. The type is laid out against this
     * rectangle, not the view, which is what makes the export match the preview.
     */
    private RectF canvasRect(int width, int height) {
        float aspect = image == null ? CARD_ASPECT : imageAspect();
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

    private void drawComposition(Canvas canvas, RectF rect, ColorMatrixColorFilter look,
                                 boolean withSelection) {
        canvas.drawRect(rect, fillPaint);
        drawPhoto(canvas, rect, look);
        for (Layer layer : layers) {
            if (layer.visible) {
                drawLayer(canvas, layer, rect);
            }
        }
        if (withSelection && selected != null && selected.visible && layers.contains(selected)) {
            selectionPaint.setColor(selectionColour);
            canvas.drawRect(bounds(selected, rect), selectionPaint);
        }
    }

    private float imageAspect() {
        float aspect = image.getWidth() / (float) image.getHeight();
        if (rotation == 90 || rotation == 270) {
            aspect = 1f / aspect;
        }
        return aspect;
    }

    private void drawPhoto(Canvas canvas, RectF rect, ColorMatrixColorFilter look) {
        if (image == null) {
            return;
        }
        paint.reset();
        paint.setAntiAlias(true);
        paint.setColorFilter(look);
        canvas.save();
        canvas.translate(rect.centerX(), rect.centerY());
        canvas.rotate(rotation);
        canvas.scale(flipHorizontal ? -1f : 1f, flipVertical ? -1f : 1f);
        boolean turned = rotation == 90 || rotation == 270;
        float drawWidth = turned ? rect.height() : rect.width();
        float drawHeight = turned ? rect.width() : rect.height();
        canvas.drawBitmap(image, null,
                new RectF(-drawWidth / 2f, -drawHeight / 2f,
                        drawWidth / 2f, drawHeight / 2f), paint);
        canvas.restore();
        paint.setColorFilter(null);
    }

    private ColorMatrixColorFilter filter() {
        if (filter == null) {
            filter = filterFor(adjust);
        }
        return filter;
    }

    /**
     * One matrix for a preset or a hand-tuned look. Contrast and fade set the channel
     * scale and a common offset; warmth tilts red against blue; saturation is applied
     * first so the channel work still has colour to shape.
     */
    private ColorMatrixColorFilter filterFor(int[] values) {
        float contrast = 1f + values[1] / 125f;
        float fade = values[4] / 100f;
        float fadeScale = 1f - fade * 0.32f;
        float warmth = values[3] / 100f;
        float red = contrast * (1f + warmth * 0.18f) * fadeScale;
        float green = contrast * (1f + warmth * 0.03f) * fadeScale;
        float blue = contrast * (1f - warmth * 0.18f) * fadeScale;
        float offset = 128f * (1f - contrast) + values[0] * 0.9f + fade * 34f;
        ColorMatrix base = new ColorMatrix(new float[]{
                red, 0, 0, 0, offset,
                0, green, 0, 0, offset,
                0, 0, blue, 0, offset,
                0, 0, 0, 1, 0
        });
        float saturation = Math.max(0f, 1f + values[2] / 100f);
        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(saturation);
        sat.postConcat(base);
        return new ColorMatrixColorFilter(sat);
    }

    private void drawLayer(Canvas canvas, Layer layer, RectF rect) {
        int min = Math.round(Math.min(rect.width(), rect.height()));
        paint.reset();
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(layer.face);
        paint.setColor(layer.colour);
        paint.setAlpha(layer.opacity);
        paint.setLetterSpacing(layer.letterSpacing);
        paint.setTextSize(Math.max(dp(6), layer.sizeFrac * min));
        if (layer.shadow) {
            paint.setShadowLayer(Math.max(1f, paint.getTextSize() * 0.06f),
                    0, Math.max(1f, paint.getTextSize() * 0.025f), 0x66000000);
        } else {
            paint.clearShadowLayer();
        }
        List<String> lines = wrap(paint, layer.text, rect.width() * 0.9f);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float lineHeight = (metrics.descent - metrics.ascent) * layer.lineSpacing;
        float total = lineHeight * lines.size();
        float widest = 0f;
        for (String line : lines) {
            widest = Math.max(widest, paint.measureText(line));
        }
        float centre = rect.left + layer.cx * rect.width();
        float blockLeft = centre - widest / 2f;
        float top = rect.top + layer.cy * rect.height() - total / 2f;
        for (int i = 0; i < lines.size(); i++) {
            float lineWidth = paint.measureText(lines.get(i));
            float x = blockLeft;
            if (layer.align == 1) {
                x = blockLeft + (widest - lineWidth) / 2f;
            } else if (layer.align == 2) {
                x = blockLeft + widest - lineWidth;
            }
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
        paint.setLetterSpacing(layer.letterSpacing);
        paint.setTextSize(Math.max(dp(6),
                layer.sizeFrac * Math.min(rect.width(), rect.height())));
        List<String> lines = wrap(paint, layer.text, rect.width() * 0.9f);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float lineHeight = (metrics.descent - metrics.ascent) * layer.lineSpacing;
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
                    changed();
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
            if (layer.visible && bounds(layer, rect).contains(x, y)) {
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
            boolean turned = rotation == 90 || rotation == 270;
            int sourceWidth = turned ? image.getHeight() : image.getWidth();
            int sourceHeight = turned ? image.getWidth() : image.getHeight();
            float scale = Math.min(1f, 2048f / Math.max(sourceWidth, sourceHeight));
            width = Math.max(1, Math.round(sourceWidth * scale));
            height = Math.max(1, Math.round(sourceHeight * scale));
        } else {
            width = 1080;
            height = 1350;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        RectF rect = new RectF(0, 0, width, height);
        drawComposition(canvas, rect, filter(), false);
        return bitmap;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * density;
    }
}

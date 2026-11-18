package add;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The Twin's artboard, painted by the platform instead of the WebView's canvas.
 *
 * This is a straight port of the paint path in `twin/src/twin/pngExport.ts`: the same
 * paper, grain, meta rule, layer transforms, type metrics, shape geometry and shader
 * fields, in the same order, at the same design size (765 x 1000). The point is one
 * rasterizer: the PNG comes out of Skia with the app's own fonts, so an export does
 * not wait on a 2D canvas and does not depend on which webfont the WebView loaded.
 *
 * Only the effects the native engine can render are here -- solid, field and the
 * shader fields. A document that uses a pattern, particles or an image texture is
 * refused with {@link Unsupported} so the page keeps its canvas renderer, which is
 * the only one that can draw those faithfully. Refusing beats drawing a subset.
 */
final class TwinRenderer {

    static final float WIDTH = 765f;
    static final float HEIGHT = 1000f;

    private static final String PAPER = "#f2f1ed";
    private static final String SHEET = "#fafaf7";
    private static final String INK = "#111111";
    private static final String[] SHADER_COLORS = {
            "#111111", "#5a5a56", "#8a8983", "#d8d7d1", "#fafaf7",
    };
    private static final String[] SUPPORTED_EFFECTS = { "solid", "field", "shader" };

    private static final float TEXT_WIDTH = 642f;
    private static final float TEXT_LINE_HEIGHT = 0.82f;
    private static final float TEXT_PADDING = 45f;
    private static final float SHAPE_STAR = 208f;
    private static final float SHAPE_ORBIT = 222f;
    private static final float SHAPE_BLOCK = 244f;

    /** The document asks for an effect that only the page's canvas can draw. */
    static final class Unsupported extends Exception {
        Unsupported(String reason) {
            super(reason);
        }
    }

    private final Typeface serif;
    private final Typeface book;
    private final Typeface franklin;
    private final Typeface poppins;

    private TwinRenderer(Context context) {
        this.serif = context.getResources().getFont(R.font.instrument_serif);
        this.book = context.getResources().getFont(R.font.spectral_regular);
        this.franklin = context.getResources().getFont(R.font.libre_franklin);
        this.poppins = poppins(context);
    }

    private static Typeface poppins(Context context) {
        try {
            return Typeface.createFromAsset(context.getAssets(),
                    "www/assets/fonts/poppins-400.ttf");
        } catch (RuntimeException missing) {
            return Typeface.SANS_SERIF;
        }
    }

    /**
     * A project file's artboard as a bitmap. `requested` is pixels per design unit;
     * the page asks for 2, which is the canvas exporter's 1530 x 2000.
     */
    static Bitmap render(Context context, String project, float requested)
            throws JSONException, Unsupported {
        JSONObject root = new JSONObject(project);
        JSONObject doc = root.optJSONObject("document");
        if (doc == null) {
            doc = root;
        }
        ensureNative(doc);
        float scale = requested <= 0f ? 2f : Math.max(0.5f, Math.min(4f, requested));
        return new TwinRenderer(context).paint(doc, scale);
    }

    /** Every effect in the document must be one this painter knows. */
    private static void ensureNative(JSONObject doc) throws Unsupported {
        checkEffect(doc.optJSONObject("background"));
        JSONArray layers = doc.optJSONArray("layers");
        if (layers == null) {
            return;
        }
        for (int index = 0; index < layers.length(); index++) {
            JSONObject layer = layers.optJSONObject(index);
            if (layer != null) {
                checkEffect(layer.optJSONObject("effect"));
            }
        }
    }

    private static void checkEffect(JSONObject effect) throws Unsupported {
        if (effect == null) {
            return;
        }
        String kind = effect.optString("kind", "");
        if (kind.isEmpty()) {
            return;
        }
        for (String supported : SUPPORTED_EFFECTS) {
            if (supported.equals(kind)) {
                return;
            }
        }
        throw new Unsupported(kind);
    }

    private Bitmap paint(JSONObject doc, float scale) {
        int width = Math.max(1, Math.round(WIDTH * scale));
        int height = Math.max(1, Math.round(HEIGHT * scale));
        Bitmap page = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(page);
        canvas.drawColor(colour(SHEET));

        Ink artboard = new Ink(1f, "normal");
        canvas.save();
        canvas.scale(scale, scale);
        paintEffect(canvas, Effect.of(doc.optJSONObject("background")),
                0f, 0f, WIDTH, HEIGHT, artboard);
        paintArtboardGrain(canvas);
        canvas.restore();

        // One layer buffer for the whole document: each layer is painted on its own
        // pixel grid, then composited with its opacity and blend, exactly as the
        // page's exporter does. Reusing the buffer keeps a 12-layer page at one
        // allocation instead of twelve.
        Bitmap buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas layerCanvas = new Canvas(buffer);
        JSONArray layers = doc.optJSONArray("layers");
        if (layers != null) {
            for (int index = 0; index < layers.length(); index++) {
                JSONObject layer = layers.optJSONObject(index);
                if (layer == null || !layer.optBoolean("visible", true)) {
                    continue;
                }
                layerCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                layerCanvas.save();
                layerCanvas.scale(scale, scale);
                JSONObject transform = layer.optJSONObject("transform");
                float x = value(transform, "x", 0f);
                float y = value(transform, "y", 0f);
                float rotation = value(transform, "rotation", 0f);
                float scaleX = value(transform, "scaleX", 1f);
                float scaleY = value(transform, "scaleY", 1f);
                layerCanvas.translate(WIDTH / 2f + x, HEIGHT / 2f + y);
                layerCanvas.rotate(rotation);
                layerCanvas.scale(scaleX, scaleY);
                paintLayer(layerCanvas, layer, artboard);
                layerCanvas.restore();

                Paint compositor = new Paint(Paint.FILTER_BITMAP_FLAG);
                compositor.setAlpha(Math.round(
                        clamp((float) layer.optDouble("opacity", 1), 0f, 1f) * 255f));
                applyBlend(compositor, layer.optString("blend", "normal"));
                canvas.drawBitmap(buffer, 0f, 0f, compositor);
            }
        }
        buffer.recycle();

        canvas.save();
        canvas.scale(scale, scale);
        paintMeta(canvas);
        canvas.restore();
        return page;
    }

    private void paintLayer(Canvas canvas, JSONObject layer, Ink artboard) {
        float[] size = baseLayerSize(layer);
        if (size[0] <= 0f || size[1] <= 0f) {
            return;
        }
        String kind = layer.optString("kind", "");
        if ("text".equals(kind)) {
            paintTextLayer(canvas, layer);
        } else if ("shape".equals(kind)) {
            paintShapeLayer(canvas, layer, size[0]);
        } else {
            paintImageLayer(canvas, layer);
        }
        JSONObject effect = layer.optJSONObject("effect");
        if (effect == null) {
            return;
        }
        canvas.save();
        canvas.clipRect(-size[0] / 2f, -size[1] / 2f, size[0] / 2f, size[1] / 2f);
        // A layer effect is a wash over the layer, not a layer of its own.
        paintEffect(canvas, Effect.of(effect),
                -size[0] / 2f, -size[1] / 2f, size[0], size[1],
                new Ink(0.55f, "multiply"));
        canvas.restore();
    }

    private void paintTextLayer(Canvas canvas, JSONObject layer) {
        JSONObject text = layer.optJSONObject("text");
        if (text == null) {
            return;
        }
        float size = (float) text.optDouble("size", 68);
        float lineHeight = size * TEXT_LINE_HEIGHT;
        String content = text.optString("content", "");
        String[] lines = content.split("\r?\n", -1);
        float total = lineHeight * Math.max(1, lines.length);
        String align = text.optString("align", "left");
        float x = 0f;
        Paint.Align paintAlign = Paint.Align.CENTER;
        if ("left".equals(align)) {
            x = -TEXT_WIDTH / 2f;
            paintAlign = Paint.Align.LEFT;
        } else if ("right".equals(align)) {
            x = TEXT_WIDTH / 2f;
            paintAlign = Paint.Align.RIGHT;
        }

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(typeface(text.optString("font", "instrument-serif")));
        paint.setTextSize(size);
        paint.setTextAlign(paintAlign);
        paint.setLetterSpacing(-0.045f);
        // The page sets textBaseline "middle"; Android wants a baseline, so undo
        // half the font's own ascent and descent around the line centre.
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baselineShift = -(metrics.ascent + metrics.descent) / 2f;

        Paint shadow = new Paint(paint);
        shadow.setColor(rgba("#fafaf7", 0.72f));
        Paint fill = new Paint(paint);
        fill.setColor(colour(layer.optString("fill", INK)));

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].isEmpty() ? "\u00a0" : lines[index];
            float centre = -total / 2f + lineHeight / 2f + index * lineHeight;
            canvas.drawText(line, x, centre + baselineShift + 1f, shadow);
            canvas.drawText(line, x, centre + baselineShift, fill);
        }
    }

    private void paintShapeLayer(Canvas canvas, JSONObject layer, float size) {
        JSONObject shape = layer.optJSONObject("shape");
        String variant = shape == null ? "" : shape.optString("variant", "");
        if (variant.isEmpty()) {
            String id = layer.optString("id", "");
            variant = id.contains("orbit") ? "orbit" : id.contains("star") ? "star" : "block";
        }
        String fillHex = layer.optString("fill", INK);
        if ("star".equals(variant)) {
            float unit = size / 92f;
            Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
            shade.setColor(rgba(INK, 0.16f));
            canvas.save();
            canvas.translate(2f * unit, 3f * unit);
            canvas.drawPath(starPath(size, size), shade);
            canvas.restore();
            Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
            ink.setColor(colour(fillHex));
            canvas.drawPath(starPath(size, size), ink);
            return;
        }
        if ("orbit".equals(variant)) {
            float unit = size / 98f;
            Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
            shade.setColor(rgba(INK, 0.08f));
            canvas.save();
            canvas.translate(4f * unit, 4f * unit);
            canvas.drawCircle(0f, 0f, 49f * unit, shade);
            canvas.restore();
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(18f * unit);
            ring.setColor(colour(fillHex));
            canvas.drawCircle(0f, 0f, 40f * unit, ring);
            Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
            core.setColor(colour(PAPER));
            canvas.drawCircle(0f, 0f, 9f * unit, core);
            return;
        }
        float half = size / 2f;
        float offset = (4f / 108f) * size;
        Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        shade.setColor(rgba(INK, 0.1f));
        canvas.drawRect(-half + offset, -half + offset, half + offset, half + offset, shade);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(colour(fillHex));
        canvas.drawRect(-half, -half, half, half, fill);
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1f);
        edge.setColor(rgba(INK, 0.24f));
        canvas.drawRect(-half, -half, half, half, edge);
    }

    private void paintImageLayer(Canvas canvas, JSONObject layer) {
        JSONObject image = layer.optJSONObject("image");
        if (image == null) {
            return;
        }
        Bitmap bitmap = decodeImage(image.optString("src", ""));
        if (bitmap == null) {
            return;
        }
        float width = (float) image.optDouble("width", bitmap.getWidth());
        float height = (float) image.optDouble("height", bitmap.getHeight());
        canvas.drawBitmap(bitmap, null,
                new RectF(-width / 2f, -height / 2f, width / 2f, height / 2f),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        bitmap.recycle();
    }

    private void paintEffect(Canvas canvas, Effect effect, float x, float y,
                             float width, float height, Ink ink) {
        if (effect == null || effect.kind == null || width <= 0f || height <= 0f) {
            return;
        }
        canvas.save();
        canvas.clipRect(x, y, x + width, y + height);
        if ("solid".equals(effect.kind)) {
            Paint fill = brush(ink);
            fill.setColor(colour(effect.hex));
            canvas.drawRect(x, y, x + width, y + height, fill);
        } else if ("field".equals(effect.kind)) {
            paintField(canvas, effect, x, y, width, height, ink);
        } else if ("shader".equals(effect.kind)) {
            paintShader(canvas, effect, x, y, width, height, ink);
        }
        canvas.restore();
    }

    private void paintField(Canvas canvas, Effect effect, float x, float y,
                            float width, float height, Ink ink) {
        String[] colours = effect.colors.length > 0
                ? effect.colors
                : new String[] { SHEET, "#e7e6e1", "#8a8983", INK };
        Paint base = brush(ink);
        base.setColor(colour(colours[0]));
        canvas.drawRect(x, y, x + width, y + height, base);

        float[][] spots = { { 0.5f, 0.26f }, { 0.77f, 0.5f }, { 0.5f, 0.74f }, { 0.23f, 0.5f } };
        int count = colours.length;
        String[] spotColours = {
                colours[0], colours[1 % count], colours[2 % count], colours[3 % count],
        };
        float radius = Math.max(width, height) * 0.34f;
        for (int index = 0; index < spots.length; index++) {
            String hex = spotColours[index];
            Paint glow = brush(ink);
            glow.setShader(new RadialGradient(
                    x + width * spots[index][0], y + height * spots[index][1], radius,
                    new int[] { colour(hex), rgba(hex, 0.6f), rgba(hex, 0f) },
                    new float[] { 0f, 0.55f, 1f },
                    Shader.TileMode.CLAMP));
            canvas.drawRect(x, y, x + width, y + height, glow);
        }
    }

    private void paintShader(Canvas canvas, Effect effect, float x, float y,
                             float width, float height, Ink ink) {
        String shader = effect.shader == null ? "" : effect.shader;
        String paper = controlColour(effect, "paper", SHEET);
        String[] colours = shaderPalette(controlColour(effect, "color", ""));
        float grain = clamp(controlNumber(effect, "grain", 0.2f), 0f, 1f);
        float roughness = clamp(controlNumber(effect, "roughness", 0.45f), 0f, 1f);
        Seeded random = new Seeded(shader.length() * 7919
                + (effect.preset == null ? 0 : effect.preset.length()));

        if ("PaperTexture".equals(shader) || "Fur".equals(shader)) {
            paintPaperShader(canvas, effect, x, y, width, height, ink);
            return;
        }

        Paint base = brush(ink);
        base.setColor(colour(paper));
        canvas.drawRect(x, y, x + width, y + height, base);
        canvas.save();
        canvas.clipRect(x, y, x + width, y + height);

        if ("MeshGradient".equals(shader) || "GemSmoke".equals(shader)) {
            boolean smoke = "GemSmoke".equals(shader);
            for (int index = 0; index < 7; index++) {
                String hex = colours[index % colours.length];
                float cx = x + width * (0.12f + random.next() * 0.76f);
                float cy = y + height * (0.12f + random.next() * 0.76f);
                float radius = Math.max(width, height) * (0.18f + random.next() * 0.3f);
                Paint blob = brush(ink);
                // The page replaces alpha here rather than multiplying it.
                blob.setAlpha(Math.round(255f * (smoke ? 0.82f : 0.88f)));
                blob.setShader(new RadialGradient(cx, cy, radius,
                        new int[] { rgba(hex, smoke ? 0.92f : 0.95f), rgba(hex, 0f) },
                        new float[] { 0f, 1f }, Shader.TileMode.CLAMP));
                canvas.drawRect(x, y, x + width, y + height, blob);
            }
        } else if ("GrainGradient".equals(shader)) {
            Paint wash = brush(ink);
            wash.setShader(linear(colours, x, y, x + width, y + height));
            canvas.drawRect(x, y, x + width, y + height, wash);
            Paint dots = brush(ink);
            dots.setColor(rgba(INK, 0.04f + grain * 0.12f));
            for (int index = 0; index < 1400; index++) {
                float px = x + random.next() * width;
                float py = y + random.next() * height;
                canvas.drawRect(px, py, px + 0.8f, py + 0.8f, dots);
            }
        } else if ("Warp".equals(shader)) {
            for (int index = 0; index < 28; index++) {
                float lineY = y + ((float) index / 27f) * height;
                Paint stroke = brush(ink);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(2.2f);
                stroke.setColor(colour(colours[index % colours.length]));
                Path path = new Path();
                for (int step = 0; step <= 18; step++) {
                    float px = x + ((float) step / 18f) * width;
                    float wave = (float) Math.sin(step * 0.75 + index * 0.9)
                            * height * 0.035f;
                    if (step == 0) {
                        path.moveTo(px, lineY + wave);
                    } else {
                        path.lineTo(px, lineY + wave);
                    }
                }
                canvas.drawPath(path, stroke);
            }
        } else if ("DotOrbit".equals(shader)) {
            float step = Math.max(7f, width / 38f);
            float centreX = x + width / 2f;
            float centreY = y + height / 2f;
            float span = Math.max(width, height);
            for (float py = y; py < y + height; py += step) {
                for (float px = x; px < x + width; px += step) {
                    float dx = px - centreX;
                    float dy = py - centreY;
                    float distance = (float) Math.hypot(dx, dy) / span;
                    if (distance > 0.5f) {
                        continue;
                    }
                    int slot = (int) Math.floor(
                            (Math.atan2(dy, dx) + Math.PI) / (Math.PI * 2) * colours.length)
                            % colours.length;
                    Paint dot = brush(ink);
                    dot.setColor(colour(colours[slot]));
                    canvas.drawCircle(px, py, step * (0.18f + (1f - distance) * 0.18f), dot);
                }
            }
        } else if ("ColorPanels".equals(shader)) {
            Paint first = brush(ink);
            first.setColor(colour(colours[0]));
            canvas.drawRect(x, y, x + width, y + height, first);
            for (int index = 0; index < 12; index++) {
                canvas.save();
                canvas.translate(x + width / 2f, y + height / 2f);
                canvas.rotate((float) Math.toDegrees(-0.6 + index * 0.13));
                Paint bar = brush(ink);
                bar.setColor(rgba(colours[(index + 1) % colours.length], 0.8f));
                float top = -height * 0.07f + index * height * 0.055f;
                canvas.drawRect(-width * 0.7f, top,
                        width * 0.7f, top + height * 0.032f, bar);
                canvas.restore();
            }
        } else if ("LiquidMetal".equals(shader)) {
            float[] stops = { 0f, 0.16f, 0.3f, 0.48f, 0.62f, 0.8f, 1f };
            int[] swatches = new int[stops.length];
            for (int index = 0; index < stops.length; index++) {
                swatches[index] = colour(colours[index % colours.length]);
            }
            Paint metal = brush(ink);
            metal.setShader(new LinearGradient(x, y + height, x + width, y,
                    swatches, stops, Shader.TileMode.CLAMP));
            canvas.drawRect(x, y, x + width, y + height, metal);
            for (int index = 0; index < 18; index++) {
                float radiusX = width * (0.12f + index * 0.025f);
                float radiusY = height * (0.08f + index * 0.018f);
                canvas.save();
                canvas.rotate((float) Math.toDegrees(0.4),
                        x + width / 2f, y + height / 2f);
                Paint ring = brush(ink);
                ring.setStyle(Paint.Style.STROKE);
                ring.setStrokeWidth(1.4f);
                ring.setColor(rgba(SHEET, 0.45f));
                canvas.drawOval(new RectF(
                        x + width / 2f - radiusX, y + height / 2f - radiusY,
                        x + width / 2f + radiusX, y + height / 2f + radiusY), ring);
                canvas.restore();
            }
        } else if ("FlutedGlass".equals(shader)) {
            float step = Math.max(5f, width / 34f);
            for (int index = 0; index * step < width; index++) {
                Paint band = brush(ink);
                boolean light = index % 2 == 0;
                band.setColor(rgba(light ? SHEET : INK, light ? 0.3f : 0.1f));
                canvas.drawRect(x + index * step, y, x + index * step + step * 0.52f,
                        y + height, band);
            }
            Paint scratch = brush(ink);
            scratch.setStyle(Paint.Style.STROKE);
            scratch.setStrokeWidth(0.8f);
            scratch.setColor(rgba(INK, 0.12f + roughness * 0.12f));
            for (int index = 0; index < 60; index++) {
                float px = x + random.next() * width;
                canvas.drawLine(px, y, px, y + height, scratch);
            }
        } else if ("ImageDithering".equals(shader) || "HalftoneDots".equals(shader)) {
            float step = Math.max(4f,
                    width / ("HalftoneDots".equals(shader) ? 58f : 78f));
            Paint dot = brush(ink);
            dot.setColor(colour(colours[0]));
            for (float py = y + step / 2f; py < y + height; py += step) {
                for (float px = x + step / 2f; px < x + width; px += step) {
                    float nx = (px - x) / width - 0.5f;
                    float ny = (py - y) / height - 0.5f;
                    float light = 1f - clamp((float) Math.hypot(nx, ny) * 1.5f, 0f, 1f);
                    canvas.drawCircle(px, py, step * (0.12f + light * 0.35f), dot);
                }
            }
        } else {
            paintPaperShader(canvas, effect, x, y, width, height, ink);
        }
        canvas.restore();
    }

    private void paintPaperShader(Canvas canvas, Effect effect, float x, float y,
                                  float width, float height, Ink ink) {
        float grain = clamp(controlNumber(effect, "grain", 0.2f), 0f, 1f);
        float roughness = clamp(controlNumber(effect, "roughness", grain), 0f, 1f);
        String paper = controlColour(effect, "paper", PAPER);

        Paint base = brush(ink);
        base.setColor(colour(paper));
        canvas.drawRect(x, y, x + width, y + height, base);

        float centreX = x + width * 0.42f;
        float centreY = y + height * 0.3f;
        Paint light = brush(ink);
        light.setShader(new RadialGradient(centreX, centreY,
                Math.max(width, height) * 0.75f,
                new int[] { rgba("#ffffff", 0.58f), rgba("#ffffff", 0.1f), rgba(INK, 0.12f) },
                new float[] { 0f, 0.55f, 1f }, Shader.TileMode.CLAMP));
        canvas.drawRect(x, y, x + width, y + height, light);

        canvas.save();
        canvas.clipRect(x, y, x + width, y + height);
        Seeded random = new Seeded(0x51a7e);
        Paint hair = brush(ink);
        hair.setStyle(Paint.Style.STROKE);
        hair.setStrokeWidth(0.55f);
        hair.setColor(rgba(INK, 0.08f));
        for (int index = 0; index < 90; index++) {
            float px = x + random.next() * width;
            float py = y + random.next() * height;
            float length = 8f + random.next() * 38f;
            canvas.drawLine(px, py, px + length,
                    py + (random.next() - 0.5f) * 2.5f, hair);
        }
        Paint dust = brush(ink);
        dust.setColor(rgba(INK, 0.035f + roughness * 0.06f));
        for (int index = 0; index < 1100; index++) {
            float size = 0.22f + random.next() * 0.7f;
            float px = x + random.next() * width;
            float py = y + random.next() * height;
            canvas.drawRect(px, py, px + size, py + size, dust);
        }
        canvas.restore();
    }

    private void paintArtboardGrain(Canvas canvas) {
        // One pass of paper flecks plus a corner-to-corner wash, both multiplied in
        // at the page's own 0.55 glass.
        Seeded random = new Seeded(0xa27b0a4d);
        Paint fleck = new Paint();
        fleck.setColor(rgba(INK, 0.045f * 0.55f));
        applyBlend(fleck, "multiply");
        for (int index = 0; index < 1200; index++) {
            float px = random.next() * WIDTH;
            float py = random.next() * HEIGHT;
            canvas.drawRect(px, py, px + 0.4f, py + 0.4f, fleck);
        }
        Paint wash = new Paint();
        wash.setShader(new LinearGradient(0f, 0f, WIDTH, HEIGHT,
                new int[] {
                        rgba("#ffffff", 0.08f * 0.55f),
                        rgba("#ffffff", 0f),
                        rgba(INK, 0.03f * 0.55f),
                },
                new float[] { 0f, 0.42f, 1f }, Shader.TileMode.CLAMP));
        applyBlend(wash, "multiply");
        canvas.drawRect(0f, 0f, WIDTH, HEIGHT, wash);
    }

    private void paintMeta(Canvas canvas) {
        float metaScale = WIDTH / 338f;
        Paint meta = new Paint(Paint.ANTI_ALIAS_FLAG);
        meta.setTypeface(franklin);
        meta.setColor(rgba(INK, 0.72f));
        meta.setTextSize(6f * metaScale);
        meta.setLetterSpacing(0.12f);
        canvas.drawText("VOL. 01", 12f * metaScale, HEIGHT - 9f * metaScale, meta);
        meta.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("PRINT / 2026", WIDTH - 12f * metaScale,
                HEIGHT - 9f * metaScale, meta);
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1f);
        edge.setColor(rgba(INK, 0.48f));
        canvas.drawRect(0.5f, 0.5f, WIDTH - 1f, HEIGHT - 1f, edge);
    }

    private Path starPath(float width, float height) {
        float[][] points = {
                { 50, 4 }, { 61, 34 }, { 93, 35 }, { 68, 55 }, { 77, 86 },
                { 50, 68 }, { 23, 86 }, { 32, 55 }, { 7, 35 }, { 39, 34 },
        };
        Path path = new Path();
        for (int index = 0; index < points.length; index++) {
            float px = -width / 2f + (points[index][0] / 100f) * width;
            float py = -height / 2f + (points[index][1] / 100f) * height;
            if (index == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        path.close();
        return path;
    }

    private static float[] baseLayerSize(JSONObject layer) {
        String kind = layer.optString("kind", "");
        if ("text".equals(kind)) {
            JSONObject text = layer.optJSONObject("text");
            if (text == null) {
                return new float[] { 0f, 0f };
            }
            float size = (float) text.optDouble("size", 68);
            String content = text.optString("content", "");
            int lines = Math.max(1, content.split("\r?\n", -1).length);
            return new float[] {
                    TEXT_WIDTH,
                    Math.max(size * TEXT_LINE_HEIGHT,
                            lines * size * TEXT_LINE_HEIGHT + TEXT_PADDING),
            };
        }
        if ("shape".equals(kind)) {
            JSONObject shape = layer.optJSONObject("shape");
            String variant = shape == null ? "" : shape.optString("variant", "block");
            float size = "star".equals(variant) ? SHAPE_STAR
                    : "orbit".equals(variant) ? SHAPE_ORBIT : SHAPE_BLOCK;
            return new float[] { size, size };
        }
        if ("image".equals(kind)) {
            JSONObject image = layer.optJSONObject("image");
            if (image == null) {
                return new float[] { 0f, 0f };
            }
            return new float[] {
                    (float) image.optDouble("width", 0), (float) image.optDouble("height", 0),
            };
        }
        return new float[] { 0f, 0f };
    }

    private Typeface typeface(String font) {
        if ("spectral".equals(font)) {
            return book;
        }
        if ("poppins".equals(font)) {
            return poppins;
        }
        if ("libre-franklin".equals(font)) {
            return franklin;
        }
        return serif;
    }

    private static Shader linear(String[] colours, float x0, float y0, float x1, float y1) {
        int[] swatches = new int[colours.length];
        float[] stops = new float[colours.length];
        for (int index = 0; index < colours.length; index++) {
            swatches[index] = colour(colours[index]);
            stops[index] = (float) index / Math.max(1, colours.length - 1);
        }
        return new LinearGradient(x0, y0, x1, y1, swatches, stops, Shader.TileMode.CLAMP);
    }

    private static String[] shaderPalette(String colour) {
        String selected = SHADER_COLORS[0];
        if (colour != null && !colour.isEmpty()) {
            for (String option : SHADER_COLORS) {
                if (option.equals(colour)) {
                    selected = colour;
                    break;
                }
            }
        }
        String[] palette = new String[SHADER_COLORS.length];
        palette[0] = selected;
        int slot = 1;
        for (String option : SHADER_COLORS) {
            if (!option.equals(selected)) {
                palette[slot++] = option;
            }
        }
        return palette;
    }

    private static float controlNumber(Effect effect, String key, float fallback) {
        if (effect.controls == null || !effect.controls.has(key)) {
            return fallback;
        }
        Object value = effect.controls.opt(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value instanceof String) {
            try {
                return Float.parseFloat((String) value);
            } catch (NumberFormatException bad) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String controlColour(Effect effect, String key, String fallback) {
        if (effect.controls == null) {
            return fallback;
        }
        String value = effect.controls.optString(key, "");
        return value.isEmpty() ? fallback : value;
    }

    private static Bitmap decodeImage(String source) {
        if (source == null) {
            return null;
        }
        int comma = source.indexOf(',');
        if (!source.startsWith("data:") || comma < 0) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(source.substring(comma + 1), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }

    /** Every paint in a pass carries the same glass: alpha and blend. */
    private static final class Ink {
        final float alpha;
        final String blend;

        Ink(float alpha, String blend) {
            this.alpha = alpha;
            this.blend = blend;
        }
    }

    private static Paint brush(Ink ink) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setAlpha(Math.round(clamp(ink.alpha, 0f, 1f) * 255f));
        applyBlend(paint, ink.blend);
        return paint;
    }

    /**
     * The canvas blend modes, by their own names. These must be Skia's blend modes
     * (API 29's Paint.setBlendMode), not PorterDuff: Android's PorterDuff.MULTIPLY
     * is the Porter-Duff coefficient operator, whose alpha is source x destination,
     * so a transparent source pixel erases the page instead of leaving it alone. A
     * 4% white wash turned the whole artboard pure white through that difference.
     *
     * Android 8 and 8.1 have no blend modes at all, so a layer there stays
     * unblended: it is not the canvas image, but it is not a wrong one either.
     */
    private static void applyBlend(Paint paint, String mode) {
        if (mode == null || mode.isEmpty() || "normal".equals(mode)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            Blend29.apply(paint, mode);
        }
    }

    /** API 29's blend modes; a separate class so older devices never load it. */
    private static final class Blend29 {
        static void apply(Paint paint, String mode) {
            android.graphics.BlendMode blend = null;
            if ("multiply".equals(mode)) {
                blend = android.graphics.BlendMode.MULTIPLY;
            } else if ("screen".equals(mode)) {
                blend = android.graphics.BlendMode.SCREEN;
            } else if ("overlay".equals(mode)) {
                blend = android.graphics.BlendMode.OVERLAY;
            } else if ("darken".equals(mode)) {
                blend = android.graphics.BlendMode.DARKEN;
            } else if ("lighten".equals(mode)) {
                blend = android.graphics.BlendMode.LIGHTEN;
            } else if ("color-dodge".equals(mode)) {
                blend = android.graphics.BlendMode.COLOR_DODGE;
            } else if ("color-burn".equals(mode)) {
                blend = android.graphics.BlendMode.COLOR_BURN;
            } else if ("hard-light".equals(mode)) {
                blend = android.graphics.BlendMode.HARD_LIGHT;
            } else if ("soft-light".equals(mode)) {
                blend = android.graphics.BlendMode.SOFT_LIGHT;
            } else if ("difference".equals(mode)) {
                blend = android.graphics.BlendMode.DIFFERENCE;
            } else if ("exclusion".equals(mode)) {
                blend = android.graphics.BlendMode.EXCLUSION;
            } else if ("hue".equals(mode)) {
                blend = android.graphics.BlendMode.HUE;
            } else if ("saturation".equals(mode)) {
                blend = android.graphics.BlendMode.SATURATION;
            } else if ("color".equals(mode)) {
                blend = android.graphics.BlendMode.COLOR;
            } else if ("luminosity".equals(mode)) {
                blend = android.graphics.BlendMode.LUMINOSITY;
            }
            if (blend != null) {
                paint.setBlendMode(blend);
            }
        }
    }

    /** Parsed effect: the three shapes the native painter handles. */
    private static final class Effect {
        final String kind;
        final String hex;
        final String shader;
        final String preset;
        final String[] colors;
        final JSONObject controls;

        private Effect(String kind, String hex, String shader, String preset,
                       String[] colors, JSONObject controls) {
            this.kind = kind;
            this.hex = hex;
            this.shader = shader;
            this.preset = preset;
            this.colors = colors;
            this.controls = controls;
        }

        static Effect of(JSONObject object) {
            if (object == null) {
                return null;
            }
            String kind = object.optString("kind", "");
            if (kind.isEmpty()) {
                return null;
            }
            JSONArray array = object.optJSONArray("colors");
            String[] colors = new String[array == null ? 0 : array.length()];
            for (int index = 0; index < colors.length; index++) {
                colors[index] = array.optString(index, "");
            }
            return new Effect(kind,
                    object.optString("hex", INK),
                    object.optString("shader", ""),
                    object.optString("preset", ""),
                    colors,
                    object.optJSONObject("controls"));
        }
    }

    /** The page's own linear congruential generator, so the fields land identically. */
    private static final class Seeded {
        private int state;

        Seeded(int seed) {
            this.state = seed;
        }

        float next() {
            state = state * 1664525 + 1013904223;
            return (state & 0xFFFFFFFFL) / 4294967296f;
        }
    }

    private static float value(JSONObject object, String key, float fallback) {
        return object == null ? fallback : (float) object.optDouble(key, fallback);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int colour(String hex) {
        if (hex == null || hex.isEmpty()) {
            return Color.BLACK;
        }
        try {
            return Color.parseColor(hex);
        } catch (IllegalArgumentException bad) {
            return Color.BLACK;
        }
    }

    private static int rgba(String hex, float alpha) {
        return (Math.round(clamp(alpha, 0f, 1f) * 255f) << 24) | (colour(hex) & 0xFFFFFF);
    }

}

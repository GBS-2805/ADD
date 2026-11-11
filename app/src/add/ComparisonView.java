package add;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Before/after comparison surface: the original image is drawn on the left of a
 * draggable divider and the compressed one on the right, both fitted into the
 * same rectangle so the difference is easy to judge at a glance.
 */
public class ComparisonView extends View {

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final RectF labelRect = new RectF();
    private final Path clipPath = new Path();
    private final Path arrowPath = new Path();

    private final float density;
    private Bitmap before;
    private Bitmap after;
    private float fraction = 0.5f;

    public ComparisonView(Context context) {
        this(context, null);
    }

    public ComparisonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setFocusable(true);
        density = getResources().getDisplayMetrics().density;

        dividerPaint.setColor(0xFFFFFFFF);
        dividerPaint.setStyle(Paint.Style.STROKE);
        dividerPaint.setStrokeWidth(2f * density);

        // Highlight handle, readable on a photo in either theme.
        handlePaint.setColor(0xFFFDE047);
        handlePaint.setStyle(Paint.Style.FILL);

        arrowPaint.setColor(0xFF000000);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeWidth(2.1f * density);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);

        labelPaint.setColor(0xFFFFFFFF);
        labelPaint.setTextSize(9.5f * getResources().getDisplayMetrics().scaledDensity);
        labelPaint.setTypeface(getResources().getFont(R.font.libre_franklin));
        labelPaint.setLetterSpacing(0.08f);

        labelBackground.setColor(0x99000000);
        labelBackground.setStyle(Paint.Style.FILL);
    }

    public void setImages(Bitmap before, Bitmap after) {
        this.before = before;
        this.after = after;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int bitmapWidth = before != null && before.getWidth() > 0 ? before.getWidth() : 4;
        int bitmapHeight = before != null && before.getHeight() > 0 ? before.getHeight() : 5;
        int desired = Math.round(width * (bitmapHeight / (float) bitmapWidth));
        setMeasuredDimension(width,
                resolveSize(Math.max(1, desired), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Bitmap left = before;
        Bitmap right = after != null ? after : before;
        if (left == null || left.getWidth() <= 0 || left.getHeight() <= 0) {
            return;
        }

        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float scale = Math.min(viewWidth / left.getWidth(), viewHeight / left.getHeight());
        float width = left.getWidth() * scale;
        float height = left.getHeight() * scale;
        dst.set((viewWidth - width) / 2f, (viewHeight - height) / 2f,
                (viewWidth + width) / 2f, (viewHeight + height) / 2f);

        float radius = 0f;
        clipPath.reset();
        clipPath.addRoundRect(dst, radius, radius, Path.Direction.CW);

        float split = dst.left + dst.width() * fraction;

        canvas.save();
        canvas.clipPath(clipPath);
        canvas.save();
        canvas.clipRect(dst.left, dst.top, split, dst.bottom);
        canvas.drawBitmap(left, null, dst, bitmapPaint);
        canvas.restore();
        canvas.save();
        canvas.clipRect(split, dst.top, dst.right, dst.bottom);
        canvas.drawBitmap(right, null, dst, bitmapPaint);
        canvas.restore();
        canvas.restore();

        if (split > dst.left + density && split < dst.right - density) {
            canvas.drawLine(split, dst.top + 2 * density,
                    split, dst.bottom - 2 * density, dividerPaint);
        }

        float centerY = dst.centerY();
        canvas.drawCircle(split, centerY, 16f * density, handlePaint);

        float arm = 4f * density;
        float tip = 8f * density;
        arrowPath.reset();
        arrowPath.moveTo(split - arm, centerY - arm);
        arrowPath.lineTo(split - tip, centerY);
        arrowPath.lineTo(split - arm, centerY + arm);
        arrowPath.moveTo(split + arm, centerY - arm);
        arrowPath.lineTo(split + tip, centerY);
        arrowPath.lineTo(split + arm, centerY + arm);
        canvas.drawPath(arrowPath, arrowPaint);

        drawLabel(canvas, "BEFORE", dst.left + 10 * density, dst.top + 10 * density, true);
        drawLabel(canvas, "AFTER", dst.right - 10 * density, dst.top + 10 * density, false);
    }

    private void drawLabel(Canvas canvas, String text, float x, float y, boolean alignStart) {
        float paddingH = 8f * density;
        float paddingV = 5f * density;
        float textWidth = labelPaint.measureText(text);
        float textHeight = labelPaint.descent() - labelPaint.ascent();
        float left = alignStart ? x : x - textWidth - paddingH * 2f;
        labelRect.set(left, y, left + textWidth + paddingH * 2f, y + textHeight + paddingV * 2f);
        canvas.drawRoundRect(labelRect, 6f * density, 6f * density, labelBackground);
        canvas.drawText(text, left + paddingH, y + paddingV - labelPaint.ascent(), labelPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (before == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                if (dst.width() > 0) {
                    float value = (event.getX() - dst.left) / dst.width();
                    fraction = Math.max(0f, Math.min(1f, value));
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        performClick();
                    }
                    invalidate();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}

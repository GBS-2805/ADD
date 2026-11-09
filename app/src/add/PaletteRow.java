package add;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * The three palette chips, and the light/dark switch as the last of them.
 *
 * This lives outside the activity so the code that draws the row on a phone can also
 * be run off device: the layoutlib harness inflates the panel and calls this, so the
 * capture is of the control rather than a reconstruction of it. It knows nothing about
 * storage -- the caller supplies the current pair and what picking one means, which
 * keeps the row drawable without a preferences backend.
 */
public final class PaletteRow {

    /** What the caller does with a choice: store it and repaint. */
    public interface OnPick {
        void pick(String id);
    }

    /**
     * Fill a row with the chips and the switch.
     *
     * The switch used to be a labelled "Theme" row of its own. It closes this row now,
     * as on the site: a sun or a moon on a 26dp disc, naming the mode it will put the
     * app in rather than the one it is in, and keeping the reach of a chip so losing
     * its word does not make it harder to hit.
     */
    public static void fill(LinearLayout row, String current, boolean dark,
                            OnPick onPick, Runnable onToggle) {
        Context context = row.getContext();
        row.removeAllViews();
        for (int i = 0; i < Palette.IDS.length; i++) {
            final String id = Palette.IDS[i];
            View chip = new View(context);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(dp(context, 26), dp(context, 26));
            params.setMarginEnd(dp(context, 8));
            chip.setLayoutParams(params);
            chip.setContentDescription(Palette.LABELS[i] + " palette");

            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(Palette.BANDS[i]);
            disc.setStroke(dp(context, current.equals(id) ? 2 : 1), Palette.WORDS[i]);
            chip.setBackground(disc);

            chip.setOnClickListener(v -> onPick.pick(id));
            row.addView(chip);
        }

        ImageView toggle = new ImageView(context);
        toggle.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 26), dp(context, 26)));
        toggle.setScaleType(ImageView.ScaleType.FIT_CENTER);
        toggle.setImageResource(dark ? R.drawable.ic_moon : R.drawable.ic_sun);
        toggle.setColorFilter(colour(context, R.attr.on));
        toggle.setContentDescription(dark ? "Switch to the light page" : "Switch to the dark page");
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(dp(context, 1), colour(context, R.attr.rule));
        toggle.setBackground(ring);
        toggle.setPadding(dp(context, 5), dp(context, 5), dp(context, 5), dp(context, 5));
        toggle.setOnClickListener(v -> onToggle.run());
        row.addView(toggle);
    }

    /** A theme attribute as a packed colour. */
    private static int colour(Context context, int attribute) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, value, true)) {
            return 0xffffffff;
        }
        return value.data;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private PaletteRow() {
    }
}

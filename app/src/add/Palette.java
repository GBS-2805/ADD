package add;

/**
 * The site's four palettes, as the app wears them.
 *
 * The values here are the ones the chips are drawn with — a palette's band for
 * the disc and its band ink for the ring — so the control reads as the thing it
 * picks. Every colour the screens use comes from the theme instead; adding a
 * palette means a block of colours, a theme and one more entry in each list.
 */
final class Palette {

    static final String GREEN = "green";
    static final String MOSS = "moss";
    static final String NAVY = "navy";
    static final String ASH = "ash";

    static final String[] IDS = {GREEN, MOSS, NAVY, ASH};
    static final String[] LABELS = {"Green", "Moss", "Navy", "Ash"};

    /** Each palette's band, for the chip disc. */
    static final int[] BANDS = {0xff0fbf92, 0xff005043, 0xff242d55, 0xffdd4124};

    /** Each palette's band ink, for the ring that marks the current choice. */
    static final int[] WORDS = {0xff0f120f, 0xffe7ddce, 0xffa1b0c3, 0xff0f0b08};

    private Palette() {
    }
}

package app.add.engine;

/**
 * The spike refers to its bundled Poppins faces as {@code R.font}. In the review
 * build those resources come from ADD's generated R class, so this tiny bridge
 * keeps the spike source unchanged while both apps can still be built.
 */
public final class R {
    public static final class font {
        public static final int poppins_regular =
                app.offline.compress.R.font.poppins_regular;
        public static final int poppins_medium =
                app.offline.compress.R.font.poppins_medium;
        public static final int poppins_semibold =
                app.offline.compress.R.font.poppins_semibold;
    }

    private R() {
    }
}

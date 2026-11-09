package add;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * The reader's own looks, kept between visits.
 *
 * A look is the five adjustment values that make it, plus one type treatment per line
 * of the poster. Each is stored one per line as
 * {@code name<TAB>v,v,v,v,v<TAB>colour,align,tracking,leading,opacity,shadow;...} in
 * the same preferences file as the palette and the queue -- no file, no permission, and
 * it survives a restart. Saving under a name that already exists replaces that look,
 * so a name stays one slot rather than collecting duplicates.
 */
final class StyleStore {

    private static final String PREFS = "compress_settings";
    private static final String KEY = "editor_looks";
    private static final String SEPARATOR = "\n";
    private static final String FIELD = "\t";
    private static final int NAME_LIMIT = 28;

    /** Enough looks to keep a habit, few enough to stay one sheet. */
    static final int LIMIT = 24;

    static final class Look {
        final String name;
        final int[] adjust;
        /** One treatment per line, in drawing order; empty for a grade-only look. */
        final List<EditorView.TypeStyle> types;

        Look(String name, int[] adjust, List<EditorView.TypeStyle> types) {
            this.name = name;
            this.adjust = adjust;
            this.types = types;
        }
    }

    private StyleStore() {
    }

    static List<Look> load(Context context) {
        String stored = prefs(context).getString(KEY, "");
        ArrayList<Look> looks = new ArrayList<>();
        if (stored == null || stored.isEmpty()) {
            return looks;
        }
        for (String line : stored.split(SEPARATOR)) {
            int tab = line.indexOf(FIELD);
            if (tab <= 0) {
                continue;
            }
            // The type field arrived after the grade, so a two-field line is still
            // a valid look: it simply carries no type treatment.
            String[] chunks = line.substring(tab + 1).split(FIELD, 3);
            int[] values = parseAdjust(chunks[0]);
            if (values == null) {
                continue;
            }
            List<EditorView.TypeStyle> types = chunks.length > 1
                    ? parseTypes(chunks[1]) : new ArrayList<>();
            looks.add(new Look(line.substring(0, tab), values, types));
        }
        return looks;
    }

    /** A free name for a new look: "Look 1", then the first number not taken. */
    static String suggest(Context context) {
        List<Look> looks = load(context);
        for (int n = 1; ; n++) {
            String candidate = "Look " + n;
            if (find(looks, candidate) < 0) {
                return candidate;
            }
        }
    }

    /**
     * Saves the look, replacing one of the same name. Returns the name used, or null
     * when the list is full and nothing was written.
     */
    static String save(Context context, String requested, int[] adjust,
                       List<EditorView.TypeStyle> types) {
        if (adjust == null || adjust.length != EditorView.ADJUSTMENTS.length) {
            return null;
        }
        List<EditorView.TypeStyle> copy = types == null
                ? new ArrayList<>() : new ArrayList<>(types);
        List<Look> looks = load(context);
        String name = clean(requested);
        if (name.isEmpty()) {
            name = suggest(context);
        }
        int existing = find(looks, name);
        if (existing >= 0) {
            looks.set(existing, new Look(name, adjust.clone(), copy));
        } else {
            if (looks.size() >= LIMIT) {
                return null;
            }
            looks.add(0, new Look(name, adjust.clone(), copy));
        }
        write(context, looks);
        return name;
    }

    static void remove(Context context, String name) {
        List<Look> looks = load(context);
        int existing = find(looks, name);
        if (existing < 0) {
            return;
        }
        looks.remove(existing);
        write(context, looks);
    }

    private static int find(List<Look> looks, String name) {
        for (int i = 0; i < looks.size(); i++) {
            if (looks.get(i).name.equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /** One line, no tabs, and short enough for a preset cell. */
    private static String clean(String name) {
        if (name == null) {
            return "";
        }
        String flat = name.replace('\n', ' ').replace('\t', ' ').trim();
        return flat.length() > NAME_LIMIT ? flat.substring(0, NAME_LIMIT).trim() : flat;
    }

    private static int[] parseAdjust(String raw) {
        String[] parts = raw.split(",");
        if (parts.length != EditorView.ADJUSTMENTS.length) {
            return null;
        }
        int[] values = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                values[i] = Integer.parseInt(parts[i].trim());
            }
        } catch (NumberFormatException bad) {
            return null;
        }
        return values;
    }

    /** Spacing travels as hundredths, the step the TYPE sliders work in. */
    private static EditorView.TypeStyle parseType(String raw) {
        String[] parts = raw.split(",");
        if (parts.length != 6) {
            return null;
        }
        try {
            return new EditorView.TypeStyle(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()) / 100f,
                    Integer.parseInt(parts[3].trim()) / 100f,
                    Integer.parseInt(parts[4].trim()),
                    "1".equals(parts[5].trim()));
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    /**
     * One treatment per line, joined by ';'. A single treatment is what an older build
     * wrote, and still reads: it simply reaches the first line.
     */
    private static List<EditorView.TypeStyle> parseTypes(String raw) {
        ArrayList<EditorView.TypeStyle> types = new ArrayList<>();
        if (raw.isEmpty()) {
            return types;
        }
        for (String part : raw.split(";")) {
            EditorView.TypeStyle style = parseType(part);
            if (style == null) {
                // A broken entry would shift every line after it, so drop the lot and
                // keep the grade rather than restyle the poster out of order.
                return new ArrayList<>();
            }
            types.add(style);
        }
        return types;
    }

    private static void write(Context context, List<Look> looks) {
        StringBuilder builder = new StringBuilder();
        for (Look look : looks) {
            if (builder.length() > 0) {
                builder.append(SEPARATOR);
            }
            builder.append(look.name).append(FIELD).append(csv(look.adjust));
            if (!look.types.isEmpty()) {
                builder.append(FIELD).append(csv(look.types));
            }
        }
        prefs(context).edit().putString(KEY, builder.toString()).apply();
    }

    private static String csv(int[] values) {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(values[i]);
        }
        return csv.toString();
    }

    private static String csv(EditorView.TypeStyle type) {
        return type.colour + "," + type.align + "," + Math.round(type.letterSpacing * 100)
                + "," + Math.round(type.lineSpacing * 100) + "," + type.opacity
                + "," + (type.shadow ? 1 : 0);
    }

    private static String csv(List<EditorView.TypeStyle> types) {
        StringBuilder csv = new StringBuilder();
        for (EditorView.TypeStyle type : types) {
            if (csv.length() > 0) {
                csv.append(';');
            }
            csv.append(csv(type));
        }
        return csv.toString();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

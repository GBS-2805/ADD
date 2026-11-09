package add;

import android.content.Context;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The faces the editor can set: the three Poppins faces the app bundles, then every
 * face from every installed pack. The bundled three are resources; an imported face is
 * a TTF inside the pack's own folder, read with Typeface.createFromFile.
 *
 * A missing or unreadable style is skipped rather than failing the list, so one bad
 * file cannot hide the rest of the shelf.
 */
final class FontStore {

    static final class Face {
        final String label;
        final String family;
        final Typeface typeface;
        final String pack;

        Face(String label, String family, Typeface typeface, String pack) {
            this.label = label;
            this.family = family;
            this.typeface = typeface;
            this.pack = pack;
        }

        boolean imported() {
            return pack != null;
        }
    }

    /** Parsing a TTF is the expensive part; the same file is the same face. */
    private static final Map<String, Typeface> CACHE = new HashMap<>();

    private FontStore() {
    }

    /** Bundled first, in the order the app uses them, then packs by family. */
    static List<Face> faces(Context context) {
        List<Face> faces = new ArrayList<>();
        faces.add(new Face("Poppins \u00b7 Regular", "Poppins",
                context.getResources().getFont(R.font.poppins_regular), null));
        faces.add(new Face("Poppins \u00b7 Medium", "Poppins",
                context.getResources().getFont(R.font.poppins_medium), null));
        faces.add(new Face("Poppins \u00b7 SemiBold", "Poppins",
                context.getResources().getFont(R.font.poppins_semibold), null));

        for (PackStore.Installed pack : PackStore.installed(context)) {
            JSONArray styles = pack.styles;
            for (int i = 0; i < styles.length(); i++) {
                JSONObject style = styles.optJSONObject(i);
                if (style == null) {
                    continue;
                }
                String file = style.optString("file");
                String name = style.optString("name", file);
                File ttf = new File(pack.dir, file);
                if (!ttf.isFile()) {
                    continue;
                }
                try {
                    Typeface typeface = CACHE.get(ttf.getAbsolutePath());
                    if (typeface == null) {
                        typeface = Typeface.createFromFile(ttf);
                        CACHE.put(ttf.getAbsolutePath(), typeface);
                    }
                    faces.add(new Face(pack.family + " \u00b7 " + name, pack.family, typeface, pack.slug));
                } catch (RuntimeException bad) {
                    // A corrupt file: leave it out, keep the rest.
                }
            }
        }
        return faces;
    }

    /** The default face for a fresh line: the app's own Medium. */
    static Face defaultFace(Context context) {
        return new Face("Poppins \u00b7 Medium", "Poppins",
                context.getResources().getFont(R.font.poppins_medium), null);
    }

    static Face find(List<Face> faces, String label) {
        for (Face face : faces) {
            if (face.label.equals(label)) {
                return face;
            }
        }
        return null;
    }
}

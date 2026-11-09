package add;

import android.util.Base64;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/**
 * The public half of the pack-signing key, compiled into the app.
 *
 * A pack's manifest is signed at build time with ECDSA P-256; the signature travels
 * inside the archive as manifest.sig. This is the only key the app trusts, so a
 * pack served by a compromised host, or handed over by another app, still has to
 * carry a signature made with the matching private key. The hash list inside the
 * manifest then covers every file.
 *
 * Rotation is by id: a new key gets a new id, the app carries both for a cycle, and
 * an unknown id is refused with a message rather than a crash.
 */
final class PackKey {

    static final String ID = "add-2026-1";

    /** X.509 SubjectPublicKeyInfo, the form javax's KeyFactory expects. */
    private static final String X509_BASE64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEb83GmRnj0O+OZAWiWPpwNirMtXod"
            + "bfgtUUF41mBkTLGNaeGdJGaQxJ1TQfdPzMK3ISDDy98SqptzZIYzX+nhNw==";

    private PackKey() {
    }

    /** True when {@code signature} is this key's signature over {@code manifest}. */
    static boolean verify(byte[] manifest, byte[] signature) {
        if (manifest == null || signature == null || signature.length == 0) {
            return false;
        }
        try {
            byte[] der = Base64.decode(X509_BASE64, Base64.NO_WRAP);
            PublicKey key = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(der));
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update(manifest);
            return verifier.verify(signature);
        } catch (GeneralSecurityException | IllegalArgumentException bad) {
            return false;
        }
    }
}

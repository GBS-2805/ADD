package app.add.engine

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportFormatTest {

    @Test
    fun pngAndJpegKeepTheArtboardDimensionsAndDeclareTheirMimeType() {
        val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888)

        val png = Exporter.encode(bitmap, ExportFormat.Png)
        val jpeg = Exporter.encode(bitmap, ExportFormat.Jpeg)

        assertEquals(32, BitmapFactory.decodeByteArray(png, 0, png.size).width)
        assertEquals(48, BitmapFactory.decodeByteArray(png, 0, png.size).height)
        assertEquals(32, BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size).width)
        assertEquals(48, BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size).height)
        assertEquals("image/png", ExportFormat.Png.mime)
        assertEquals("image/jpeg", ExportFormat.Jpeg.mime)
    }

    @Test
    fun shareIntentCarriesTheExportMimeAndReadGrant() {
        val uri = Uri.parse("content://media/external/images/media/42")
        val result = ExportResult("SAVED", 1, 1, 1L, 0, 0, 0, 0, uri, ExportFormat.Jpeg.mime)

        val intent = Exporter.shareIntent(result)!!

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/jpeg", intent.type)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertNull(Exporter.shareIntent(result.copy(uri = null)))
    }

    @Test
    fun jpegFlattensTransparencyToOpaqueWhite() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val jpeg = Exporter.encode(bitmap, ExportFormat.Jpeg)
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)

        assertEquals(255, decoded.getPixel(4, 4) ushr 24)
        assertTrue((decoded.getPixel(4, 4) and 0x00FFFFFF) > 0xF0F0F0)
    }
}

package app.add.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentV2Test {
    @Test
    fun aPhotoAndALockSurviveTheRoundTrip() {
        val doc = EngineDocument()
        doc.addImage("photo-abcd1234.jpg")
        val photo = doc.selected!!
        doc.toggleLock(photo.id)
        val text = DocumentCodec.encode(doc.layers, doc.selectedId, doc.zoom, doc.lockedIds)
        val back = DocumentCodec.decode(text)!!
        val image = back.layers.filterIsInstance<ImageLayer>().single()
        assertEquals("photo-abcd1234.jpg", image.path)
        assertTrue(image.fill)
        assertEquals(setOf(photo.id), back.locked)
    }

    @Test
    fun aVersionOneDocumentStillOpens() {
        val old = """{"schema":1,"zoom":1.0,"selected":"vector-star","layers":[
            {"kind":"vector","id":"vector-star","name":"STAR","visible":true,
             "x":0.1,"y":0.1,"w":0.3,"h":0.24,"opacity":1.0,"rotation":0.0,
             "fill":-16711681,"stroke":-15590044,"strokeWidth":4.0}]}"""
        val back = DocumentCodec.decode(old)!!
        assertEquals(1, back.layers.size)
        assertTrue(back.locked.isEmpty())
    }
}

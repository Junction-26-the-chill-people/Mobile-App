package com.example.junction.sola

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.junction.sola.data.SolaMedia
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SolaMediaTest {
    @Test
    fun aPickedPhotoKeepsItsOrientationButNotItsLocationMetadata() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val picked = File(context.cacheDir, "selected.jpg")
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        picked.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(picked).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_ROTATE_90.toString(),
            )
            setLatLong(60.1, 24.9)
            saveAttributes()
        }
        val relative = SolaMedia.importPhoto(context, Uri.fromFile(picked))
        val saved = File(context.filesDir, relative)
        try {
            val decoded = BitmapFactory.decodeFile(saved.absolutePath)
            assertEquals(40, decoded.width)
            assertEquals(80, decoded.height)
            assertNull(ExifInterface(saved).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertTrue(relative.startsWith("sola-photos/"))
            decoded.recycle()
        } finally {
            bitmap.recycle()
            picked.delete()
            saved.delete()
        }
    }

    @Test
    fun anUnreadableSelectionDoesNotCreateAPhantomPhoto() {
        val context = RuntimeEnvironment.getApplication()
        assertThrows(Exception::class.java) {
            runBlocking {
                SolaMedia.importPhoto(context, Uri.fromFile(File(context.cacheDir, "missing.jpg")))
            }
        }
    }
}

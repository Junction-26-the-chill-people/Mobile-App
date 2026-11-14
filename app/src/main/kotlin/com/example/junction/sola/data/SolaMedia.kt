package com.example.junction.sola.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SolaMedia {
    /**
     * Copy only the selected image, normalize orientation/size and discard EXIF/location metadata.
     */
    suspend fun importPhoto(context: Context, uri: Uri): String =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                "This image could not be opened."
            }
            var sample = 1
            while (bounds.outWidth / sample > 2000 || bounds.outHeight / sample > 2000) sample *= 2
            val bitmap =
                resolver.openInputStream(uri).use {
                    BitmapFactory.decodeStream(
                        it,
                        null,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                } ?: error("This image could not be decoded.")
            val orientation = runCatching {
                resolver.openInputStream(uri).use { stream ->
                    if (stream != null)
                        ExifInterface(stream)
                            .getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL,
                            )
                    else ExifInterface.ORIENTATION_NORMAL
                }
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val transform =
                Matrix().apply {
                    when (orientation) {
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                        ExifInterface.ORIENTATION_TRANSPOSE -> {
                            setRotate(90f)
                            postScale(-1f, 1f)
                        }
                        ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                        ExifInterface.ORIENTATION_TRANSVERSE -> {
                            setRotate(270f)
                            postScale(-1f, 1f)
                        }
                        ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                    }
                }
            val normalized =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
            val name = "sola-photos/${UUID.randomUUID()}.jpg"
            val output = File(context.filesDir, name)
            output.parentFile?.mkdirs()
            try {
                output.outputStream().use {
                    check(normalized.compress(Bitmap.CompressFormat.JPEG, 88, it)) {
                        "Could not save the photo."
                    }
                }
            } catch (e: Exception) {
                output.delete()
                throw e
            } finally {
                if (normalized !== bitmap) normalized.recycle()
                bitmap.recycle()
            }
            name
        }
}

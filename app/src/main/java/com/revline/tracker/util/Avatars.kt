package com.revline.tracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import coil.load
import com.revline.tracker.BuildConfig
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Profile pictures. Rendering falls back to the letter chip when there's no photo;
 * [encodeForUpload] does the crop/scale/re-encode client-side (the server just
 * stores the bytes), which also drops any EXIF/GPS the camera wrote.
 */
object Avatars {

    private const val TARGET_PX = 256
    private const val JPEG_QUALITY = 82

    /**
     * Shows [url] in [image] (square, centre-cropped) with a crossfade, or hides
     * [image] and leaves the letter [letter] (first char of [name]) showing.
     */
    fun bind(letter: TextView, image: ImageView, url: String?, name: String?) {
        letter.text = initial(name)
        val resolved = resolve(url)
        if (resolved == null) {
            image.setImageDrawable(null)
            image.visibility = View.GONE
            return
        }
        image.visibility = View.VISIBLE
        image.load(resolved) {
            crossfade(true)
            listener(
                onError = { _, _ ->
                    image.setImageDrawable(null)
                    image.visibility = View.GONE
                }
            )
        }
    }

    fun initial(name: String?): String =
        name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    /** Server may return a relative path ("/uploads/…") or a full URL. */
    fun resolve(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) return url
        return BuildConfig.API_BASE_URL.trimEnd('/') + "/" + url.trimStart('/')
    }

    /**
     * Reads the picked image, rotates per EXIF, centre-crops to a square, scales to
     * [TARGET_PX], and returns a base64 JPEG ready for `POST /api/users/me/avatar`.
     * Returns null if the image can't be decoded.
     */
    fun encodeForUpload(context: Context, uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, TARGET_PX * 2)
        }
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        bmp = applyExifRotation(bmp, bytes)

        val side = min(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(
            bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side
        )
        val scaled = Bitmap.createScaledBitmap(square, TARGET_PX, TARGET_PX, true)

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun sampleSize(w: Int, h: Int, target: Int): Int {
        var s = 1
        while (w / (s * 2) >= target && h / (s * 2) >= target) s *= 2
        return s
    }

    private fun applyExifRotation(bmp: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
}

package com.zenfold.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// Draws an adaptive icon's own background/foreground layers without the
// OS's mask, so each launcher style can clip icons to its own shape
// (circle, squircle, rounded square, ...) instead of the system's.
fun Drawable.toUnmaskedBitmap(sizePx: Int): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this is AdaptiveIconDrawable) {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        listOfNotNull(background, foreground).forEach { layer ->
            layer.setBounds(0, 0, sizePx, sizePx)
            layer.draw(canvas)
        }
        return bitmap
    }
    return toBitmap(width = sizePx, height = sizePx)
}

/** The single-color glyph layer apps ship for Android 13+ themed icons, if this app has one. */
fun Drawable.monochromeLayer(): Drawable? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && this is AdaptiveIconDrawable) monochrome else null

fun Drawable.toLayerBitmap(sizePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, sizePx, sizePx)
    draw(Canvas(bitmap))
    return bitmap
}

/**
 * Average hue (0–360) of the icon's colorful pixels, so a crystal tile can keep the
 * app's own color family (WhatsApp stays green, etc.). Null for grey/white icons.
 */
fun Bitmap.dominantHue(): Float? {
    val software = if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this
    val small = Bitmap.createScaledBitmap(software, 24, 24, true)
    val hsv = FloatArray(3)
    var x = 0.0
    var y = 0.0
    var count = 0
    for (px in 0 until small.width) {
        for (py in 0 until small.height) {
            val c = small.getPixel(px, py)
            if (Color.alpha(c) < 128) continue
            Color.colorToHSV(c, hsv)
            if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue
            // Hue is circular (350° and 10° are both red), so average it as a vector.
            val radians = Math.toRadians(hsv[0].toDouble())
            x += cos(radians)
            y += sin(radians)
            count++
        }
    }
    if (count < 20) return null
    return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
}

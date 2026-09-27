package com.zenfold.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap

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

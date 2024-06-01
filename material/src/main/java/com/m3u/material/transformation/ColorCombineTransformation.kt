package com.m3u.material.transformation

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import coil.size.Size
import coil.size.pxOrElse
import coil.transform.Transformation

class ColorCombineTransformation(
    private val color: Color
) : Transformation {
    override val cacheKey: String = "${ColorCombineTransformation::class.java.name}_$color"

    @Suppress("USELESS_ELVIS")
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val bitmapPainter = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val colorPainter = Paint().apply {
            setColor(this@ColorCombineTransformation.color.toArgb())
            setColor(android.graphics.Color.RED)
        }
        val width = input.width
        val height = input.height
        return createBitmap(
            width = width,
            height = height,
            config = input.config ?: Bitmap.Config.ARGB_8888
        ).applyCanvas {
            drawBitmap(
                input,
                0f,
                0f,
                bitmapPainter
            )
            drawRect(
                0f,
                size.height.pxOrElse { height } * 0.85f,
                size.width.pxOrElse { width }.toFloat(),
                height.toFloat(),
                colorPainter
            )
        }
    }
}
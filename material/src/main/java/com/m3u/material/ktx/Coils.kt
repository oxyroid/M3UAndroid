package com.m3u.material.ktx

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import coil.size.Size
import coil.transform.Transformation

class ScaleIfHasAlphaTransformation(
    private val scaleX: Float,
    private val scaleY: Float = scaleX
) : Transformation {
    override val cacheKey: String = "${javaClass.name}_${scaleX}_${scaleY}"

    @Suppress("USELESS_ELVIS")
    override suspend fun transform(
        input: Bitmap,
        size: Size
    ): Bitmap {
        if (!input.hasAlpha()) return input
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val width = input.width
        val height = input.height
        return createBitmap(
            width = width,
            height = height,
            config = input.config ?: Bitmap.Config.ARGB_8888
        ).applyCanvas {
            withScale(scaleX, scaleY) {
                withTranslation(width * (1 - scaleX) / 2, height * (1 - scaleY) / 2) {
                    drawBitmap(input, 0f, 0f, paint)
                }
            }
        }
    }
}
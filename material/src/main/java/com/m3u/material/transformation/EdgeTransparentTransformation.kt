package com.m3u.material.transformation

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.TileMode
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import coil.size.Size
import coil.transform.Transformation
import com.m3u.material.ktx.Edge

class EdgeTransparentTransformation(
    private val edge: Edge
) : Transformation {
    override val cacheKey: String = "${EdgeTransparentTransformation::class.java.name}_${edge}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            shader = LinearGradientShader(
                from = Offset(x = 0f, y = 0f),
                to = Offset(x = 0f, y = height.toFloat()),
                colors = listOf(Color.Transparent, Color.Black),
                tileMode = TileMode.Clamp
            )
        }
        return createBitmap(width, height).applyCanvas {
            drawBitmap(input, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            drawRect(0f, 0f, width.toFloat(), height.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    shader = LinearGradientShader(
                        from = Offset(x = 0f, y = 0f),
                        to = Offset(x = 0f, y = height.toFloat()),
                        colors = listOf(Color.Transparent, Color.Red),
                        tileMode = TileMode.Clamp
                    )
                })
        }
    }
}
package com.azamovme.sozotvlogin.ui.qr

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class QrOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB0000000.toInt()
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val cutoutRect = RectF()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        val size = min(width, height) * 0.62f
        val left = (width - size) / 2f
        val top = (height - size) / 2.2f
        cutoutRect.set(left, top, left + size, top + size)

        val radius = 22f
        canvas.drawRoundRect(cutoutRect, radius, radius, clearPaint)

        val c = 40f
        canvas.drawLine(cutoutRect.left, cutoutRect.top + c, cutoutRect.left, cutoutRect.top, cornerPaint)
        canvas.drawLine(cutoutRect.left, cutoutRect.top, cutoutRect.left + c, cutoutRect.top, cornerPaint)
        canvas.drawLine(cutoutRect.right - c, cutoutRect.top, cutoutRect.right, cutoutRect.top, cornerPaint)
        canvas.drawLine(cutoutRect.right, cutoutRect.top, cutoutRect.right, cutoutRect.top + c, cornerPaint)
        canvas.drawLine(cutoutRect.left, cutoutRect.bottom - c, cutoutRect.left, cutoutRect.bottom, cornerPaint)
        canvas.drawLine(cutoutRect.left, cutoutRect.bottom, cutoutRect.left + c, cutoutRect.bottom, cornerPaint)
        canvas.drawLine(cutoutRect.right - c, cutoutRect.bottom, cutoutRect.right, cutoutRect.bottom, cornerPaint)
        canvas.drawLine(cutoutRect.right, cutoutRect.bottom - c, cutoutRect.right, cutoutRect.bottom, cornerPaint)

        canvas.restoreToCount(save)
    }
}

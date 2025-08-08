package com.himaaya.platescan

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var detections: List<Detection> = emptyList()
    private val paintGreen = Paint().apply {
        color = Color.argb(140, 0, 255, 0)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }
    private val paintRed = Paint().apply {
        color = Color.argb(180, 255, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }
    private val textBgPaint = Paint().apply {
        color = Color.argb(150, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        isAntiAlias = true
    }
    private val textPadding = 12f

    fun setDetections(detections: List<Detection>) {
        this.detections = detections
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (det in detections) {
            val paint = if (det.isOffender) paintRed else paintGreen
            canvas.drawRect(det.xmin, det.ymin, det.xmax, det.ymax, paint)

            val label = if (det.isOffender && !det.reason.isNullOrBlank()) {
                "${det.plate} • ${det.reason}"
            } else {
                det.plate
            }

            // ✅ SOLUCIÓN DESBORDE: Lógica para evitar que el texto se salga de la pantalla
            val textWidth = textPaint.measureText(label)
            var textX = det.xmin
            var textY = (det.ymin - textPadding).coerceAtLeast(textPaint.textSize + textPadding)

            // Ajusta X si se sale por la derecha
            if (textX + textWidth + (textPadding * 2) > width) {
                textX = width - textWidth - (textPadding * 2)
            }
            // Ajusta X si se sale por la izquierda
            if (textX < 0) {
                textX = 0f
            }

            val bgRect = RectF(
                textX - textPadding,
                textY - textPaint.textSize,
                textX + textWidth + textPadding,
                textY + textPadding
            )

            canvas.drawRoundRect(bgRect, 12f, 12f, textBgPaint)
            canvas.drawText(label, textX, textY, textPaint)
        }
    }
}
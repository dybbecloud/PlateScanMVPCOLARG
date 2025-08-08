package com.himaaya.platescan

import android.graphics.PointF
import android.graphics.RectF

data class Detection(
    var xmin: Float,
    var ymin: Float,
    var xmax: Float,
    var ymax: Float,
    val isOffender: Boolean,
    val plate: String,
    val reason: String?
)

/**
 * Convierte un objeto Detection a un RectF para cálculos de posición.
 */
fun Detection.toRectF(): RectF {
    return RectF(this.xmin, this.ymin, this.xmax, this.ymax)
}

/**
 * Calcula el punto central de la caja de detección.
 */
fun Detection.getCenter(): PointF {
    return PointF((this.xmin + this.xmax) / 2, (this.ymin + this.ymax) / 2)
}
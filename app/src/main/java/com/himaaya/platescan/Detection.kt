package com.himaaya.platescan

import android.graphics.RectF // ✅ Importante añadir esta línea

data class Detection(
    // Usamos var y Float para poder recalcular las coordenadas con decimales
    var xmin: Float,
    var ymin: Float,
    var xmax: Float,
    var ymax: Float,
    val isOffender: Boolean,
    val plate: String,
    val reason: String?
)

// ✅ CÓDIGO AÑADIDO: Función de extensión para facilitar los cálculos de superposición
fun Detection.toRectF(): RectF {
    return RectF(this.xmin, this.ymin, this.xmax, this.ymax)
}
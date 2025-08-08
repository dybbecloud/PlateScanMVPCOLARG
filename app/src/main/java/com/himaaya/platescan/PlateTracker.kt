package com.himaaya.platescan

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

class PlateTracker {

    // ✅ NUEVA ESTRUCTURA: El objeto rastreado ahora tiene un ID único y un contador de "vidas".
    private data class TrackedObject(
        val id: Int,
        var detection: Detection,
        var framesWithoutMatch: Int = 0
    )

    private val trackedObjects = mutableListOf<TrackedObject>()
    private var nextId = 0
    private val MAX_FRAMES_WITHOUT_MATCH = 4 // Tolerancia: el HUD sobrevive 4 frames sin ser detectado.

    // ✅ LÓGICA DE TRACKING COMPLETAMENTE REESCRITA
    fun update(freshDetections: List<Detection>): List<Detection> {
        val IOU_THRESHOLD = 0.5f

        // 1. Marcar todos los objetos existentes para ver si encontramos una coincidencia en este frame
        trackedObjects.forEach { it.framesWithoutMatch++ }

        val matchedFreshIndices = mutableSetOf<Int>()

        // 2. Intentar hacer coincidir las detecciones nuevas con los objetos que ya seguimos
        for (trackedObj in trackedObjects) {
            var bestMatchIndex = -1
            var maxIou = 0f

            for (i in freshDetections.indices) {
                if (i in matchedFreshIndices) continue

                val freshDet = freshDetections[i]
                val iou = calculateIoU(trackedObj.detection.toRectF(), freshDet.toRectF())

                if (iou > IOU_THRESHOLD && iou > maxIou) {
                    maxIou = iou
                    bestMatchIndex = i
                }
            }

            if (bestMatchIndex != -1) {
                // Coincidencia encontrada: actualizamos la posición y reseteamos el contador de "vidas"
                trackedObj.detection = freshDetections[bestMatchIndex]
                trackedObj.framesWithoutMatch = 0
                matchedFreshIndices.add(bestMatchIndex)
            }
        }

        // 3. Añadir las detecciones nuevas que no coincidieron como nuevos objetos a rastrear
        for (i in freshDetections.indices) {
            if (i !in matchedFreshIndices) {
                trackedObjects.add(TrackedObject(nextId++, freshDetections[i]))
            }
        }

        // 4. Eliminar objetos que han superado la tolerancia de "vidas" (no se han visto en N frames)
        trackedObjects.removeAll { it.framesWithoutMatch > MAX_FRAMES_WITHOUT_MATCH }

        // 5. Devolver la lista actual de detecciones activas para ser dibujadas
        return trackedObjects.map { it.detection }
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val xA = max(box1.left, box2.left)
        val yA = max(box1.top, box2.top)
        val xB = min(box1.right, box2.right)
        val yB = min(box1.bottom, box2.bottom)
        val intersectionArea = max(0f, xB - xA) * max(0f, yB - yA)
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        val unionArea = box1Area + box2Area - intersectionArea
        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }
}
package com.himaaya.platescan

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.pow
import kotlin.math.sqrt

class PlateTracker {

    private data class TrackedObject(
        val id: Int,
        var detection: Detection,
        var framesWithoutMatch: Int = 0
    )

    private val trackedObjects = mutableListOf<TrackedObject>()
    private var nextId = 0
    private val MAX_FRAMES_WITHOUT_MATCH = 5 // Tolerancia antes de eliminar un HUD
    private val MAX_DISTANCE_THRESHOLD = 150f // Distancia máxima (en píxeles) para considerar una coincidencia

    fun update(freshDetections: List<Detection>): List<Detection> {
        // 1. Crear una lista de no coincidentes para las detecciones frescas
        val unmatchedFreshDetections = freshDetections.toMutableList()
        val matchedTrackedIndices = mutableSetOf<Int>()

        // 2. Intentar hacer coincidir cada objeto que ya seguimos con la detección fresca más cercana
        trackedObjects.forEachIndexed { i, trackedObj ->
            var bestMatchIndex = -1
            var minDistance = Float.MAX_VALUE

            unmatchedFreshDetections.forEachIndexed { j, freshDet ->
                val distance = calculateCenterDistance(trackedObj.detection.getCenter(), freshDet.getCenter())
                if (distance < MAX_DISTANCE_THRESHOLD && distance < minDistance) {
                    minDistance = distance
                    bestMatchIndex = j
                }
            }

            if (bestMatchIndex != -1) {
                // Coincidencia encontrada: actualizamos el objeto y lo marcamos como coincidente
                val matchedDet = unmatchedFreshDetections.removeAt(bestMatchIndex)
                trackedObj.detection = matchedDet
                trackedObj.framesWithoutMatch = 0
                matchedTrackedIndices.add(i)
            } else {
                // No se encontró coincidencia para este objeto
                trackedObj.framesWithoutMatch++
            }
        }

        // 3. Añadir las detecciones frescas que no coincidieron como nuevos objetos a rastrear
        unmatchedFreshDetections.forEach { newDet ->
            trackedObjects.add(TrackedObject(nextId++, newDet))
        }

        // 4. Eliminar objetos que se han perdido por demasiados frames
        trackedObjects.removeAll { it.framesWithoutMatch > MAX_FRAMES_WITHOUT_MATCH }

        // 5. Devolver la lista actual de detecciones activas
        return trackedObjects.map { it.detection }
    }

    // Calcula la distancia euclidiana entre los centros de dos puntos
    private fun calculateCenterDistance(center1: PointF, center2: PointF): Float {
        return sqrt((center1.x - center2.x).pow(2) + (center1.y - center2.y).pow(2))
    }
}


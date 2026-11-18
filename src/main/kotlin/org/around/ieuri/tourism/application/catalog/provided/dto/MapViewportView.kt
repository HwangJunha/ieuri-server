package org.around.ieuri.tourism.application.catalog.provided.dto

import org.around.ieuri.tourism.application.congestion.provided.dto.CulturalEventView

/** mapViewport 응답. */
data class MapViewportView(
    val spots: List<MapSpotView>,
    val events: List<CulturalEventView>,
    val truncated: Boolean,
)

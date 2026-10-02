package com.otli.app.core.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.otli.app.R
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * The map styles of every Otli map (ADR-13): the OpenFreeMap vector style, which is free and keyless,
 * and an OpenStreetMap raster style the maps fall back to when the vector style cannot be loaded.
 * Both require the attribution that [MapAttribution] shows under each map.
 */
object MapStyle {
    const val VECTOR_URL = "https://tiles.openfreemap.org/styles/liberty"

    /** A minimal MapLibre style: one raster layer fed by the OpenStreetMap standard tiles. */
    val RASTER_FALLBACK_JSON = """
        {
          "version": 8,
          "sources": {
            "osm": {
              "type": "raster",
              "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "maxzoom": 19,
              "attribution": "© OpenStreetMap contributors"
            }
          },
          "layers": [{ "id": "osm", "type": "raster", "source": "osm" }]
        }
    """.trimIndent()
}

/** The OpenFreeMap / OpenStreetMap credit the tile licences require; place it under every map. */
@Composable
fun MapAttribution(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.map_attribution),
        modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Loads the vector style into [map] and calls [onLoaded] once a style is ready. If the vector style
 * fails to load, the OpenStreetMap raster style replaces it; once the vector style is up, a later
 * failure (a tile, say) never swaps it out. Native glue, verified on a device.
 */
fun MapView.loadOtliStyle(map: MapLibreMap, onLoaded: () -> Unit) {
    var vectorLoaded = false
    var fellBack = false
    addOnDidFailLoadingMapListener {
        if (!vectorLoaded && !fellBack) {
            fellBack = true
            map.setStyle(Style.Builder().fromJson(MapStyle.RASTER_FALLBACK_JSON)) { onLoaded() }
        }
    }
    map.setStyle(Style.Builder().fromUri(MapStyle.VECTOR_URL)) {
        vectorLoaded = true
        onLoaded()
    }
}

package com.houseofmmminq.macaco.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.ui.theme.MacacoFontFamily
import com.houseofmmminq.macaco.ui.theme.MapTheme
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel
import kotlin.math.ln
import kotlin.math.tan
import kotlinx.coroutines.delay

private fun createTealMarkerBitmap(context: Context): Bitmap {
    val dp = context.resources.displayMetrics.density
    val size = (36 * dp).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = size / 2f

    paint.style = Paint.Style.FILL
    paint.color = android.graphics.Color.parseColor("#1B96B3")
    canvas.drawCircle(radius, radius, radius - 2 * dp, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3 * dp
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(radius, radius, radius - 3.5f * dp, paint)

    return bitmap
}

@Composable
fun MapScreen(
    viewModel: JournalViewModel,
    onEntryClick: (String) -> Unit
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    val geocodedLocations by viewModel.geocodedLocations.collectAsState()
    val geocodingComplete by viewModel.geocodingComplete.collectAsState()
    val mapTheme by viewModel.mapTheme.collectAsState()

    // BitmapDescriptorFactory requires the Maps SDK to be initialized, which happens when
    // GoogleMap first renders. Defer creation to onMapLoaded so we never call it too early.
    var tealMarker by remember { mutableStateOf<BitmapDescriptor?>(null) }
    // Apply the user-selected map style; Standard (styleRes == null) uses Google's default map.
    val mapProperties = remember(mapTheme) {
        MapProperties(
            mapStyleOptions = mapTheme.styleRes?.let {
                MapStyleOptions.loadRawResourceStyle(context, it)
            }
        )
    }

    val locations = remember(entries) {
        entries.mapNotNull { it.location.trim().ifBlank { null } }.distinct()
    }

    LaunchedEffect(locations) {
        viewModel.geocodeLocations(context, locations)
    }

    val topEntryByLocation = remember(entries) {
        entries
            .groupBy { it.location.trim() }
            .filterKeys { it.isNotBlank() }
            .mapValues { (_, list) -> list.maxBy { it.createdAt } }
    }
    val countByLocation = remember(entries) {
        entries.groupingBy { it.location.trim() }.eachCount().filterKeys { it.isNotBlank() }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 2f)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    // True once the camera has actually been moved onto the user's places. The scrim stays up until
    // then, so the arbitrary default position is never shown — including for returning users whose
    // geocode cache is already populated when the screen opens (the camera is positioned instantly
    // under the scrim rather than animated from the default, which is what made it fly across the
    // Atlantic on every open).
    var cameraPositioned by remember { mutableStateOf(false) }
    // Safety net: if geocoding yields nothing (offline, or every lookup fails) the camera never
    // moves — drop the scrim after a few seconds so the spinner can't trap the user forever.
    var revealTimedOut by remember { mutableStateOf(false) }
    // Actual measured pixel size of the map area (captured via onSizeChanged below). The v10 Mercator
    // zoom math needs the REAL map dimensions — the map is shorter than the screen (header +
    // bottom nav), so displayMetrics would over-tighten the fit and clip edge pins.
    var mapSizePx by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(mapLoaded, geocodingComplete, mapSizePx) {
        if (mapLoaded && !cameraPositioned && geocodingComplete && geocodedLocations.isNotEmpty() &&
            mapSizePx.width > 0 && mapSizePx.height > 0) {
            // Fit ALL geocoded locations in view ("show me my whole travel map"). Exclude Null
            // Island (0.0, 0.0) — geocoding failures land there.
            val latlngs = locations
                .mapNotNull { geocodedLocations[it] }
                .map { LatLng(it.first, it.second) }
                .filter { !(it.latitude == 0.0 && it.longitude == 0.0) }
            if (latlngs.isEmpty()) return@LaunchedEffect

            val update = if (latlngs.size == 1) {
                // A single point has no bounds to fit; pick a sensible country-level zoom.
                CameraUpdateFactory.newLatLngZoom(latlngs[0], 6f)
            } else {
                // Frame every pin, antimeridian-correct AND exact-zoom (the v1–v9 saga, see
                // docs/DONE/code-brief-map-camera-v10.md). The center is found the same way v8/v9 did
                // (correct): latitude is a simple min/max; for longitude we find the largest EMPTY gap
                // between sorted pins — the complement is the tightest arc containing them all, so its
                // midpoint is the antimeridian-correct longitude center.
                //
                // v9 then handed that arc to newLatLngBounds for the zoom, but on device (A53, vc43)
                // it still under-zoomed for our >180° span (4 pins span 209°). v10 computes the zoom
                // ourselves with direct Mercator math — the same calculation the SDK does internally,
                // but explicit so no SDK edge case can interfere — and logs every result so any future
                // failure is visible in Logcat (`adb logcat -s MapCamera`).
                val lats = latlngs.map { it.latitude }
                val latMin = lats.min()
                val latMax = lats.max()

                val lngs = latlngs.map { it.longitude }.sorted()
                var largestGap = Double.NEGATIVE_INFINITY
                var arcStart = lngs.first() // western edge of the populated arc (pin just east of gap)
                for (i in lngs.indices) {
                    val next = if (i + 1 < lngs.size) lngs[i + 1] else lngs.first() + 360.0
                    val gap = next - lngs[i]
                    if (gap > largestGap) {
                        largestGap = gap
                        arcStart = if (i + 1 < lngs.size) lngs[i + 1] else lngs.first()
                    }
                }
                val lngSpan = 360.0 - largestGap
                // Antimeridian-correct longitude center (v8 logic, unchanged).
                var lngCenter = arcStart + lngSpan / 2.0
                if (lngCenter > 180.0) lngCenter -= 360.0
                if (lngCenter < -180.0) lngCenter += 360.0
                val latCenter = (latMin + latMax) / 2.0

                // Mercator zoom math — bypasses newLatLngBounds which under-zooms for spans > 180°.
                // At zoom z: 1 longitude degree = 256·2^z / 360 px (linear).
                //            1 Mercator radian  = 256·2^z / (2π) px (latitude is log-compressed).
                // Solve for z in each dimension; take the smaller (most zoomed-out) value.
                val paddingPx = (context.resources.displayMetrics.density * 32).toInt() // 32dp each side
                val usableW = (mapSizePx.width - 2 * paddingPx).coerceAtLeast(1)
                val usableH = (mapSizePx.height - 2 * paddingPx).coerceAtLeast(1)
                val lngZoom = ln(usableW * 360.0 / (256.0 * lngSpan)) / ln(2.0)
                val mercY = { deg: Double -> ln(tan(Math.PI / 4.0 + deg * Math.PI / 360.0)) }
                val mercSpan = (mercY(latMax) - mercY(latMin)).coerceAtLeast(0.001)
                val latZoom = ln(usableH * 2.0 * Math.PI / (256.0 * mercSpan)) / ln(2.0)
                val zoom = minOf(lngZoom, latZoom).toFloat().coerceIn(1f, 18f)

                Log.d("MapCamera", "v10: lngSpan=${"%.1f".format(lngSpan)}° " +
                    "lngZ=${"%.2f".format(lngZoom)} latZ=${"%.2f".format(latZoom)} " +
                    "→zoom=$zoom map=${mapSizePx.width}×${mapSizePx.height}px " +
                    "center=(${"%+.1f".format(latCenter)},${"%+.1f".format(lngCenter)})")

                CameraUpdateFactory.newLatLngZoom(LatLng(latCenter, lngCenter), zoom)
            }

            // Keep the gate so a hypothetical future failure doesn't clear the scrim with the
            // camera stuck at the default world view. If not moved, the 8-second revealTimedOut in
            // the sibling LaunchedEffect eventually drops the scrim.
            val moveResult = runCatching { cameraPositionState.move(update) }
            if (moveResult.isFailure) {
                Log.e("MapCamera", "v10: move() threw — camera not positioned", moveResult.exceptionOrNull())
            }
            val moved = moveResult.isSuccess
            if (moved) cameraPositioned = true
        }
    }

    LaunchedEffect(locations) {
        if (locations.isNotEmpty()) {
            delay(8000)
            revealTimedOut = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // In landscape on phones (short screen) the tall centered brand block eats ~120dp of map;
        // collapse it to a single slim row. Tablets stay tall (~750dp+) and keep the full header.
        val isLandscape = LocalConfiguration.current.screenHeightDp < 480
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(macacoBrandBackground())
                .statusBarsPadding()
        ) {
          if (isLandscape) {
            // ── Compact landscape header: single slim row ──────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "macaco",
                    color = SplashGoldBright,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 3.sp
                )
                Text(
                    text = " · Adventures",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
                if (locations.isNotEmpty()) {
                    val mappedCount = locations.count { it in geocodedLocations }
                    Text(
                        text = " · $mappedCount/${locations.size} mapped",
                        color = SplashGold.copy(alpha = 0.70f),
                        fontSize = 11.sp,
                        fontFamily = MacacoFontFamily
                    )
                }
            }
          } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).offset(y = 4.dp)
                )
                Text(
                    text = "macaco",
                    color = SplashGoldBright,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 5.sp
                )
                Text(
                    "Adventures",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
                if (locations.isNotEmpty()) {
                    // geocodedLocations is an append-only cache (never pruned when entries are
                    // edited/deleted), so its raw size can exceed the current location count —
                    // count only the overlap with today's unique locations.
                    val mappedCount = locations.count { it in geocodedLocations }
                    Text(
                        "$mappedCount of ${locations.size} locations mapped",
                        color = SplashGold.copy(alpha = 0.70f),
                        fontSize = 11.sp,
                        fontFamily = MacacoFontFamily
                    )
                }
            }
          }
        }

        Box(modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { mapSizePx = it }) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                onMapLoaded = {
                    mapLoaded = true
                    tealMarker = BitmapDescriptorFactory.fromBitmap(createTealMarkerBitmap(context))
                },
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false
                )
            ) {
                val marker = tealMarker
                if (marker != null) {
                    geocodedLocations.forEach { (location, coords) ->
                        val topEntry = topEntryByLocation[location] ?: return@forEach
                        val count = countByLocation[location] ?: 1
                        Marker(
                            state = MarkerState(position = LatLng(coords.first, coords.second)),
                            title = location,
                            snippet = if (count == 1) "1 memory · tap to open" else "$count memories · tap to open",
                            icon = marker,
                            onInfoWindowClick = { onEntryClick(topEntry.id) }
                        )
                    }
                }
            }

            if (locations.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.Explore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No locations yet",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Add a location to your journal entries\nto see them on the map.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }

            // Loading scrim — opaque, so the arbitrary default camera position is never seen.
            // Drops away once the camera has been positioned over the user's places (or the timeout
            // fires). (When there are no locations, the empty-state above covers the map instead.)
            if (locations.isNotEmpty() && !cameraPositioned && !revealTimedOut) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

package com.houseofmmminq.macaco.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
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
import com.houseofmmminq.macaco.ui.components.MacacoBrandBlock
import com.houseofmmminq.macaco.ui.theme.MacacoFontFamily
import com.houseofmmminq.macaco.ui.theme.MapTheme
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Maps [pinLat] to a vertical fraction (0.0 = top, 1.0 = bottom) in the current viewport.
 * Uses Mercator projection so high-latitude positions are positioned correctly.
 * Returns a value clamped to [minFraction, maxFraction] to stay within safe tap area.
 */
private fun arrowVerticalFraction(
    pinLat: Double,
    camLat: Double,
    camZoom: Float,
    mapHeightPx: Int,
    density: Float,
    minFraction: Float = 0.1f,
    maxFraction: Float = 0.9f
): Float {
    if (mapHeightPx <= 0) return 0.5f
    val tile = 256.0 * density
    val mercPerPx = 2 * PI / (tile * 2.0.pow(camZoom.toDouble()))
    val halfMercSpan = mercPerPx * mapHeightPx / 2.0
    val merc = { lat: Double -> ln(tan(PI / 4 + lat * PI / 360)) }
    val fraction = 0.5 + (merc(camLat) - merc(pinLat)) / (2 * halfMercSpan)
    return fraction.toFloat().coerceIn(minFraction, maxFraction)
}

private fun createThemedMarkerBitmap(context: Context, colorInt: Int): Bitmap {
    val dp = context.resources.displayMetrics.density
    val size = (36 * dp).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = size / 2f

    paint.style = Paint.Style.FILL
    paint.color = colorInt
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
    val density = context.resources.displayMetrics.density
    val mapScope = rememberCoroutineScope()
    val entries by viewModel.entries.collectAsState()
    val geocodedLocations by viewModel.geocodedLocations.collectAsState()
    val geocodingComplete by viewModel.geocodingComplete.collectAsState()
    val mapTheme by viewModel.mapTheme.collectAsState()

    // BitmapDescriptorFactory requires the Maps SDK to be initialized, which happens when
    // GoogleMap first renders. Defer creation to onMapLoaded so we never call it too early.
    var themedMarker by remember { mutableStateOf<BitmapDescriptor?>(null) }
    // Pin colour follows the user's selected theme (was hardcoded teal #1B96B3).
    val primaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()
    // Apply the user-selected map style; Standard (styleRes == null) uses Google's default map.
    val mapProperties = remember(mapTheme) {
        MapProperties(
            // The SDK's default minimum zoom (~3.0 on this device) only reveals ~72° of longitude,
            // so a globe-spanning entry set (e.g. 212°) can never be framed — move() silently clamps
            // any lower request up to 3.0 (confirmed via Logcat). Lower the floor so the fit-all
            // camera can actually zoom out far enough.
            minZoomPreference = 1f,
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
    // True when the SDK clamped the fit-all camera to its minimum zoom (portrait Mercator pole floor,
    // ~zoom 2.0) because the user's pins span wider than any allowed zoom can frame. Drives the
    // "Swipe to see all pins" header hint. See the v11/v12 notes in the move() block below.
    var globeSpanning by remember { mutableStateOf(false) }
    // v13: longitude of the off-screen pin furthest west/east (null = none off that edge), plus the
    // lat center + applied zoom after move() — used by the edge chevrons to pan-to the off-screen pin.
    var offScreenWestPins by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var offScreenEastPins by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var mapLatCenter by remember { mutableStateOf(0.0) }
    var mapAppliedZoom by remember { mutableStateOf(0f) }
    // Safety net: if geocoding yields nothing (offline, or every lookup fails) the camera never
    // moves — drop the scrim after a few seconds so the spinner can't trap the user forever.
    var revealTimedOut by remember { mutableStateOf(false) }
    // Actual measured pixel size of the map area (captured via onSizeChanged below). The v10 Mercator
    // zoom math needs the REAL map dimensions — the map is shorter than the screen (header +
    // bottom nav), so displayMetrics would over-tighten the fit and clip edge pins.
    var mapSizePx by remember { mutableStateOf(IntSize.Zero) }

    // Refresh the pin bitmap if the user switches theme while the map is open (or once the map
    // finishes loading). The initial creation still happens in onMapLoaded below.
    LaunchedEffect(primaryColorArgb, mapLoaded) {
        if (mapLoaded) {
            themedMarker = BitmapDescriptorFactory.fromBitmap(
                createThemedMarkerBitmap(context, primaryColorArgb)
            )
        }
    }

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

            // The zoom we ASK the SDK for — captured so the post-move() block can compare it against
            // what the SDK actually applied and detect the portrait Mercator clamp (v12).
            var requestedZoom = 6f
            // Hoisted out of the multi-pin branch so the post-move() block (v13 off-screen-pin math)
            // can reach them — they'd otherwise be scoped inside the `else` and not compile there.
            // (`density` is now a composable-scope val — shared with the reactive off-screen tracker.)
            val tile = 256.0 * density
            var fitLatCenter = latlngs[0].latitude
            var fitLngCenter = latlngs[0].longitude
            val update = if (latlngs.size == 1) {
                // A single point has no bounds to fit; pick a sensible country-level zoom.
                CameraUpdateFactory.newLatLngZoom(latlngs[0], 6f)
            } else {
                // Frame every pin, antimeridian-correct AND best-effort zoom (the v1–v10 saga). The
                // center is found the same way v8/v9 did (correct): latitude is a simple min/max; for
                // longitude we find the largest EMPTY gap between sorted pins — the complement is the
                // tightest arc containing them all, so its midpoint is the antimeridian-correct
                // longitude center.
                //
                // ZOOM (v11): v9's newLatLngBounds under-zoomed; v10's explicit Mercator math was
                // correct but (a) omitted the screen-density factor and (b) hit a hard SDK minimum-zoom
                // floor — move() SILENTLY CLAMPS any lower request (confirmed on A53/vc43 via the
                // `adb logcat -s MapCamera` diagnostics: requested 1.2 → applied 2.0). v11 fixes the
                // density factor AND lowers MapProperties.minZoomPreference (see above) so the camera
                // can actually zoom out. NOTE: a full-height portrait map still can't go below ~zoom 2.0
                // (the SDK won't show past the poles), which caps visible longitude at ~144°. So a
                // genuinely globe-spanning set (>~144° span) frames the MOST pins possible, centered;
                // the 1–2 extreme pins may sit just off-screen. This is an SDK/portrait limit, not a
                // math bug — do not "fix" it with more zoom math.
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
                // At zoom z, world width = 256·density·2^z PHYSICAL px, so:
                //   1 longitude degree = 256·density·2^z / 360 px (linear).
                //   1 Mercator radian  = 256·density·2^z / (2π) px (latitude is log-compressed).
                // Solve for z in each dimension; take the smaller (most zoomed-out) value. The `tile`
                // term MUST include screen density — omitting it (v10) made the computed zoom
                // ~log2(density) too high (≈1.4 on this xxhdpi device).
                val paddingPx = (density * 32).toInt() // 32dp each side
                val usableW = (mapSizePx.width - 2 * paddingPx).coerceAtLeast(1)
                val usableH = (mapSizePx.height - 2 * paddingPx).coerceAtLeast(1)
                val lngZoom = ln(usableW * 360.0 / (tile * lngSpan)) / ln(2.0)
                val mercY = { deg: Double -> ln(tan(Math.PI / 4.0 + deg * Math.PI / 360.0)) }
                val mercSpan = (mercY(latMax) - mercY(latMin)).coerceAtLeast(0.001)
                val latZoom = ln(usableH * 2.0 * Math.PI / (tile * mercSpan)) / ln(2.0)
                val zoom = minOf(lngZoom, latZoom).toFloat().coerceIn(1f, 18f)
                requestedZoom = zoom
                fitLatCenter = latCenter
                fitLngCenter = lngCenter

                CameraUpdateFactory.newLatLngZoom(LatLng(latCenter, lngCenter), zoom)
            }

            // Keep the gate so a hypothetical future failure doesn't clear the scrim with the
            // camera stuck at the default world view. If not moved, the 8-second revealTimedOut in
            // the sibling LaunchedEffect eventually drops the scrim.
            val moveResult = runCatching { cameraPositionState.move(update) }
            if (moveResult.isFailure) {
                Log.e("MapCamera", "v12: move() threw — camera not positioned", moveResult.exceptionOrNull())
            }
            val moved = moveResult.isSuccess
            if (moved) {
                cameraPositioned = true
                // Read back what the SDK actually applied — may differ from requestedZoom if the SDK
                // clamped it (portrait Mercator floor: ~zoom 2.0 on a full-height phone).
                val appliedZoom = cameraPositionState.position.zoom
                globeSpanning = appliedZoom > requestedZoom + 0.2f

                // v13: when clamped, some pins fall off the left/right edge; reframe the latitude on
                // the visible pins only (off-screen pins' latitudes otherwise pull the frame south
                // into empty ocean). v14: the off-screen pin SET is no longer computed here — the
                // reactive LaunchedEffect below owns it so the chevrons stay correct after the user
                // navigates to a new region (fixes the stale-coordinate wraparound bug).
                mapAppliedZoom = appliedZoom
                mapLatCenter = fitLatCenter
                if (globeSpanning) {
                    val halfSpanDeg =
                        (mapSizePx.width / (tile * 2.0.pow(appliedZoom.toDouble()))) * 360.0 / 2.0
                    val westEdge = fitLngCenter - halfSpanDeg
                    val eastEdge = fitLngCenter + halfSpanDeg

                    val visibleLatlngs = latlngs.filter { it.longitude in westEdge..eastEdge }
                    val latCenterVisible = if (visibleLatlngs.isNotEmpty()) {
                        (visibleLatlngs.minOf { it.latitude } + visibleLatlngs.maxOf { it.latitude }) / 2.0
                    } else fitLatCenter

                    if (abs(latCenterVisible - fitLatCenter) > 0.5) {
                        runCatching {
                            cameraPositionState.move(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(latCenterVisible, fitLngCenter), appliedZoom
                                )
                            )
                        }
                        mapLatCenter = latCenterVisible
                    }
                }
            }
        }
    }

    // v14: reactive off-screen pin tracker. Runs whenever the camera settles at a new position,
    // mapSizePx changes, or geocodedLocations gains new entries. Replaces the one-time computation
    // that used to live in the initial LaunchedEffect — this version always reflects the CURRENT
    // viewport, so chevrons work correctly after the user navigates to a new region.
    //
    // Navigation goes to the NEAREST off-screen pin (closest to the viewport edge), not the
    // furthest. From Japan: nearest western pin = Europe, not USA.
    //
    // Antimeridian note: uses simple -180..+180 longitude comparison. Edge case where the viewport
    // straddles the antimeridian is rare and handled by the globeSpanning logic above.
    LaunchedEffect(cameraPositionState.position, geocodedLocations, mapSizePx, cameraPositioned) {
        // Wait for the real "fit all pins" camera position — otherwise this runs once against the
        // arbitrary default framing (LatLng(20,0), zoom 2) before the fit-all move happens, and the
        // chevrons pop into view during the loading scrim instead of only once there's an actual
        // map to navigate on.
        if (!cameraPositioned || mapSizePx.width <= 0 || geocodedLocations.isEmpty()) return@LaunchedEffect

        val pos = cameraPositionState.position
        val camLng = pos.target.longitude
        val camZoom = pos.zoom
        val tile = 256.0 * density
        val halfSpanDeg =
            (mapSizePx.width / (tile * 2.0.pow(camZoom.toDouble()))) * 360.0 / 2.0
        val westEdge = camLng - halfSpanDeg
        val eastEdge = camLng + halfSpanDeg

        val latlngs = locations
            .mapNotNull { geocodedLocations[it] }
            .map { LatLng(it.first, it.second) }
            .filter { !(it.latitude == 0.0 && it.longitude == 0.0) }

        // All pins off each edge — each gets its own directional arrow.
        offScreenWestPins = latlngs.filter { it.longitude < westEdge }
        offScreenEastPins = latlngs.filter { it.longitude > eastEdge }

        // Keep mapLatCenter + mapAppliedZoom in sync for chevron navigation.
        mapLatCenter = pos.target.latitude
        mapAppliedZoom = camZoom
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

        // The initial "fit all pins" camera move (and, for globe-spanning sets, the follow-up
        // re-center move) also toggles cameraPositionState.isMoving — without this guard the header
        // below latches collapsed the instant that auto-fit happens, before the user ever touches the
        // map. Arm user-gesture detection only after the auto-fit has had time to fully settle.
        var readyToDetectUserPan by remember { mutableStateOf(false) }
        LaunchedEffect(cameraPositioned) {
            if (cameraPositioned) {
                delay(400) // lets any auto-fit isMoving pulse (incl. the globe-spanning re-center) finish
                readyToDetectUserPan = true
            }
        }

        // Once the user starts panning/zooming, collapse the header down to the icon and keep it
        // that way for the rest of this visit to the screen — maximizes map space during active
        // exploration, in either orientation. Latched (not live-bound to isMoving) so the header
        // doesn't pop back open every time a drag settles between pans.
        var hasMovedMap by remember { mutableStateOf(false) }
        LaunchedEffect(cameraPositionState.isMoving) {
            if (cameraPositionState.isMoving && readyToDetectUserPan) hasMovedMap = true
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(macacoBrandBackground())
                .statusBarsPadding()
                // Only the EXPANDED header's trailing wordmark/label content extends sideways far
                // enough to need nav-bar clearance (see docs/DONE/code-brief-map-nav-bar.md). Once
                // collapsed, the header is just a centred icon with nothing to clip — and the map
                // below is edge-to-edge with no matching inset — so skip the inset there and centre
                // on the TRUE screen width instead. Keeping the inset in both states was centring the
                // collapsed icon on a narrower axis than the full-bleed map behind it, making it look
                // shifted left of centre.
                .then(
                    if (hasMovedMap) Modifier
                    else Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                )
                .animateContentSize()
        ) {
            MacacoBrandBlock(
                isLandscape = isLandscape,
                collapsed = hasMovedMap,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(
                        top = if (isLandscape) 4.dp else 2.dp,
                        bottom = if (hasMovedMap) 8.dp else if (isLandscape) 4.dp else 10.dp
                    ),
                landscapeTrailing = {
                    Text(
                        text = " · " + stringResource(R.string.map_adventures_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    if (locations.isNotEmpty()) {
                        val mappedCount = locations.count { it in geocodedLocations }
                        Text(
                            text = " · " + stringResource(R.string.map_locations_mapped, mappedCount, locations.size),
                            color = SplashGold.copy(alpha = 0.70f),
                            fontSize = 12.sp,
                            fontFamily = MacacoFontFamily
                        )
                    }
                    // Globe-spanning hint — compact, dot-separated (reuses the localized
                    // portrait hint string).
                    if (globeSpanning) {
                        Text(
                            text = " · " + stringResource(R.string.map_globe_spanning_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = SplashGold.copy(alpha = 0.75f),
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                portraitTrailing = {
                    Text(
                        stringResource(R.string.map_adventures_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    if (locations.isNotEmpty()) {
                        // geocodedLocations is an append-only cache (never pruned when entries are
                        // edited/deleted), so its raw size can exceed the current location count —
                        // count only the overlap with today's unique locations.
                        val mappedCount = locations.count { it in geocodedLocations }
                        Text(
                            stringResource(R.string.map_locations_mapped, mappedCount, locations.size),
                            color = SplashGold.copy(alpha = 0.70f),
                            fontSize = 12.sp,
                            fontFamily = MacacoFontFamily
                        )
                    }
                    if (globeSpanning) {
                        Text(
                            stringResource(R.string.map_globe_spanning_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = SplashGold.copy(alpha = 0.75f),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            )
        }

        // weight(1f) (not fillMaxSize) so mapSizePx measures the REMAINING height after the header,
        // not the full Column height — otherwise the camera centre is placed too low and northern
        // pins render behind the tall (tablet/portrait) header. (v7)
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .onSizeChanged { mapSizePx = it }) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                onMapLoaded = {
                    mapLoaded = true
                    themedMarker = BitmapDescriptorFactory.fromBitmap(
                        createThemedMarkerBitmap(context, primaryColorArgb)
                    )
                },
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false
                )
            ) {
                val marker = themedMarker
                if (marker != null) {
                    geocodedLocations.forEach { (location, coords) ->
                        val topEntry = topEntryByLocation[location] ?: return@forEach
                        val count = countByLocation[location] ?: 1
                        Marker(
                            state = MarkerState(position = LatLng(coords.first, coords.second)),
                            title = location,
                            snippet = if (count == 1) context.getString(R.string.map_marker_snippet_one)
                                      else context.getString(R.string.map_marker_snippet_many, count),
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
                        stringResource(R.string.map_no_locations_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.map_no_locations_subtitle),
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
            // Uses the same brand teal as the header (not colorScheme.background) so this reads as
            // a continuation of the branded header rather than a hard cut to blank white — that cut
            // was what made the ~1s load feel like a "flash" on navigation (diagnosed from a screen
            // recording on the A53; see brief intro).
            if (locations.isNotEmpty() && !cameraPositioned && !revealTimedOut) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(macacoBrandBackground()),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SplashGoldBright)
                }
            }

            // ── Off-screen pin arrows (v15) ──────────────────────────────────────────────
            // One arrow per off-screen pin. Each arrow is an ArrowUpward icon rotated to the
            // visual direction from the map centre to that arrow's edge position — so Iceland
            // tilts NW, Europe points W, Argentina tilts SW, etc.
            //
            // Bearing formula: from screen-centre (0.5, 0.5) to the arrow's edge position
            // (left edge x=0, right edge x=1, vertical = arrowVerticalFraction).
            // atan2(eastComponent, northComponent) → degrees clockwise from north for rotationZ.

            offScreenWestPins.forEach { pin ->
                val fraction = arrowVerticalFraction(
                    pinLat = pin.latitude,
                    camLat = cameraPositionState.position.target.latitude,
                    camZoom = cameraPositionState.position.zoom,
                    mapHeightPx = mapSizePx.height,
                    density = density
                )
                val yOffsetPx = ((fraction - 0.5f) * mapSizePx.height).roundToInt()
                // Arrow at left edge: east component = -0.5, north component = 0.5 - fraction
                val bearing = Math.toDegrees(
                    atan2(-0.5, (0.5f - fraction).toDouble())
                ).toFloat()
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(0, yOffsetPx) }
                        .padding(start = 8.dp)
                        .size(36.dp)
                        // Shadow before clip so it renders outside the circle; primary (opaque,
                        // saturated) pops off any ocean tile where pale primaryContainer vanished.
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            mapScope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(pin.latitude, pin.longitude), mapAppliedZoom
                                    ),
                                    durationMs = 600
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.map_globe_spanning_hint),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = bearing }
                    )
                }
            }

            offScreenEastPins.forEach { pin ->
                val fraction = arrowVerticalFraction(
                    pinLat = pin.latitude,
                    camLat = cameraPositionState.position.target.latitude,
                    camZoom = cameraPositionState.position.zoom,
                    mapHeightPx = mapSizePx.height,
                    density = density
                )
                val yOffsetPx = ((fraction - 0.5f) * mapSizePx.height).roundToInt()
                // Arrow at right edge: east component = +0.5, north component = 0.5 - fraction
                val bearing = Math.toDegrees(
                    atan2(0.5, (0.5f - fraction).toDouble())
                ).toFloat()
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset { IntOffset(0, yOffsetPx) }
                        // In landscape the nav bar sits on the right edge and would swallow taps on
                        // this chevron; inset only the End side so the other overlays stay put.
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))
                        .padding(end = 8.dp)
                        .size(36.dp)
                        // Shadow before clip so it renders outside the circle; primary (opaque,
                        // saturated) pops off any ocean tile where pale primaryContainer vanished.
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            mapScope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(pin.latitude, pin.longitude), mapAppliedZoom
                                    ),
                                    durationMs = 600
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.map_globe_spanning_hint),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = bearing }
                    )
                }
            }
        }
    }
}

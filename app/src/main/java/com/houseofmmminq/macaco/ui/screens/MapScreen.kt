package com.houseofmmminq.macaco.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.ui.theme.MacacoFontFamily
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel

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

    // BitmapDescriptorFactory requires the Maps SDK to be initialized, which happens when
    // GoogleMap first renders. Defer creation to onMapLoaded so we never call it too early.
    var tealMarker by remember { mutableStateOf<BitmapDescriptor?>(null) }
    val mapProperties = remember {
        MapProperties(
            mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style)
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
    var hasAnimated by remember { mutableStateOf(false) }

    LaunchedEffect(mapLoaded, geocodedLocations) {
        if (mapLoaded && !hasAnimated && geocodedLocations.isNotEmpty()) {
            val latlngs = geocodedLocations.values.map { LatLng(it.first, it.second) }
            val update = if (latlngs.size == 1) {
                CameraUpdateFactory.newLatLngZoom(latlngs[0], 8f)
            } else {
                val bounds = LatLngBounds.builder().apply { latlngs.forEach { include(it) } }.build()
                CameraUpdateFactory.newLatLngBounds(bounds, 80)
            }
            cameraPositionState.animate(update, 1200)
            hasAnimated = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(macacoBrandBackground())
                .statusBarsPadding()
        ) {
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
                    Text(
                        "${geocodedLocations.size} of ${locations.size} locations mapped",
                        color = SplashGold.copy(alpha = 0.70f),
                        fontSize = 11.sp,
                        fontFamily = MacacoFontFamily
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
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
        }
    }
}

package com.houseofmmminq.macaco.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.houseofmmminq.macaco.ui.theme.MacacoFontFamily
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel

// Hue 192° ≈ the Macaco teal primary.
private const val TealMarkerHue = 192f

@Composable
fun MapScreen(
    viewModel: JournalViewModel,
    onEntryClick: (String) -> Unit
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    val geocodedLocations by viewModel.geocodedLocations.collectAsState()

    val locations = remember(entries) {
        entries.mapNotNull { it.location.trim().ifBlank { null } }.distinct()
    }

    LaunchedEffect(locations) {
        viewModel.geocodeLocations(context, locations)
    }

    // Most recent entry per unique location string — tap target for each pin.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(macacoBrandBackground())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Text(
                    "Adventures",
                    color = SplashGoldBright,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = MacacoFontFamily,
                    letterSpacing = 1.sp
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
                onMapLoaded = { mapLoaded = true },
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false
                )
            ) {
                geocodedLocations.forEach { (location, coords) ->
                    val topEntry = topEntryByLocation[location] ?: return@forEach
                    val count = countByLocation[location] ?: 1
                    Marker(
                        state = MarkerState(position = LatLng(coords.first, coords.second)),
                        title = location,
                        snippet = if (count == 1) "1 memory · tap to open" else "$count memories · tap to open",
                        icon = BitmapDescriptorFactory.defaultMarker(TealMarkerHue),
                        onInfoWindowClick = { onEntryClick(topEntry.id) }
                    )
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

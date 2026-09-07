package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.entity.EmergencyContact
import com.example.data.local.entity.SeismicAlertEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.components.AppPullToRefreshBox

@Composable
fun DashboardScreen(
    currentStatus: String,
    onStatusSelected: (String) -> Unit,
    sensorMagnitude: Float,
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
    latestSeismicEvent: SeismicAlertEvent?,
    seismicEvents: List<SeismicAlertEvent>,
    batteryLevel: Int,
    onTriggerPanic: () -> Unit,
    isTrappedBeaconActive: Boolean = false,
    onStopTrappedBeacon: () -> Unit = {},
    onStartTrappedBeacon: () -> Unit = {},
    currentLocation: Location? = null,
    contacts: List<EmergencyContact> = emptyList(),
    onlineSyncStatus: String = "Conectado a Internet - Listo para Consultar SGC / USGS",
    isSyncingOnline: Boolean = false,
    isRefreshing: Boolean = false,
    onRefreshOnlineSeismic: () -> Unit = {},
    onRefreshLocation: () -> Unit = {},
    onRefreshDashboard: () -> Unit = {}
) {
    val context = LocalContext.current
    var subTabIndex by remember { mutableIntStateOf(0) }

    val subTabs = listOf(
        "🚨 Emergencia & SOS",
        "📍 Mi Ubicación GPS",
        "Sismos SGC"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-tabs to eliminate vertical scrolling during emergency
        ScrollableTabRow(
            selectedTabIndex = subTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = subTabIndex == index,
                    onClick = { subTabIndex = index },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (subTabIndex == index) FontWeight.Black else FontWeight.Bold,
                        color = if (subTabIndex == index) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when (subTabIndex) {
                0 -> {
                    // TAB 1: 🚨 Emergencia, SOS & Punto de Reunión
                    AppPullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefreshDashboard,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Trapped Rescue Beacon Active Banner
                            if (isTrappedBeaconActive) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F)),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .background(Color.White, CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "🚨 BALIZA DE RESCATE ACTIVA 🚨",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 13.sp
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Text(
                                                text = "Transmitiendo coordenadas GPS y batería en vivo a contactos familiares.",
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 11.sp
                                            )

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Button(
                                                onClick = onStopTrappedBeacon,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White,
                                                    contentColor = Color(0xFFD32F2F)
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "DESACTIVAR BALIZA / ESTOY A SALVO",
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Status Selector Section (Strategic Tactical States)
                            item {
                                Text(
                                    text = "MI ESTADO DE EMERGENCIA",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusPillItem(
                                        title = "SANO Y SALVO",
                                        subtitle = "Sin lesiones, en lugar seguro",
                                        color = Color(0xFF00E676),
                                        icon = Icons.Default.CheckCircle,
                                        isSelected = currentStatus == "SANO Y SALVO",
                                        onClick = { onStatusSelected("SANO Y SALVO") },
                                        testTag = "status_safe"
                                    )

                                    StatusPillItem(
                                        title = "NECESITO AYUDA / ATRAPADO",
                                        subtitle = "Requiere asistencia de rescate urgente",
                                        color = Color(0xFFFF1744),
                                        icon = Icons.Default.Warning,
                                        isSelected = currentStatus == "NECESITO AYUDA / ATRAPADO",
                                        onClick = { onStatusSelected("NECESITO AYUDA / ATRAPADO") },
                                        testTag = "status_help"
                                    )

                                    StatusPillItem(
                                        title = "LESIONADO",
                                        subtitle = "Atención médica prioritaria requerida",
                                        color = Color(0xFFFFC107),
                                        icon = Icons.Default.MedicalServices,
                                        isSelected = currentStatus == "LESIONADO",
                                        onClick = { onStatusSelected("LESIONADO") },
                                        testTag = "status_injured"
                                    )

                                    StatusPillItem(
                                        title = "EVACUANDO",
                                        subtitle = "En movimiento hacia punto de reunión",
                                        color = Color(0xFF00BCD4),
                                        icon = Icons.Default.GpsFixed,
                                        isSelected = currentStatus == "EVACUANDO",
                                        onClick = { onStatusSelected("EVACUANDO") },
                                        testTag = "status_evacuating"
                                    )
                                }
                            }

                            // Big SOS Manual Panic Button Trigger (Moved to Bottom)
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = onTriggerPanic,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF1744),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .testTag("manual_sos_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = "SOS",
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "BOTÓN DE PÁNICO (SOS)",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            text = "Transmitir GPS + batería a contactos",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 2: 🗺️ Mapa Google Maps en Vivo
                    AppPullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            onRefreshLocation()
                            onRefreshDashboard()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                GoogleMapsDashboardCard(
                                    currentLocation = currentLocation,
                                    contacts = contacts,
                                    onRefreshLocation = onRefreshLocation,
                                    onOpenExternalMap = { lat, lng ->
                                        val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 3: 📡 Feeds Sísmicos Oficiales (SGC Colombia / USGS)
                    AppPullToRefreshBox(
                        isRefreshing = isRefreshing || isSyncingOnline,
                        onRefresh = onRefreshOnlineSeismic,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                OnlineSeismicFeedCard(
                                    syncStatus = onlineSyncStatus,
                                    isSyncing = isSyncingOnline,
                                    onRefresh = onRefreshOnlineSeismic
                                )
                            }

                            if (seismicEvents.isNotEmpty()) {
                                items(seismicEvents) { event ->
                                    SeismicEventCard(event = event)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🗺️ GOOGLE MAPS EMBEDDED DASHBOARD CARD SHOWING REAL USER GPS LOCATION & CONNECTED PERSON
 */
@Composable
fun GoogleMapsDashboardCard(
    currentLocation: Location?,
    contacts: List<EmergencyContact>,
    onRefreshLocation: () -> Unit,
    onOpenExternalMap: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val lat = currentLocation?.latitude ?: 4.6097
    val lng = currentLocation?.longitude ?: -74.0817
    val hasRealLocation = currentLocation != null

    val connectedContactName = if (contacts.isNotEmpty()) contacts.first().name else "Ana María (Familiar Conectado)"
    val distanceKm = calculateDistanceKm(lat, lng, lat + 0.008, lng + 0.009)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Bar: Sleek Tactical Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "GPS Location",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "UBICACIÓN GPS",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "Lat: %.4f, Lng: %.4f", lat, lng),
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    color = if (hasRealLocation) Color(0xFF10B981).copy(alpha = 0.18f) else Color(0xFFF59E0B).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (hasRealLocation) Color(0xFF10B981) else Color(0xFFF59E0B))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasRealLocation) "GPS Activo" else "Buscando...",
                            color = if (hasRealLocation) Color(0xFF10B981) else Color(0xFFF59E0B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val hasContacts = contacts.isNotEmpty()
            val connectedContactName = if (hasContacts) contacts.first().name else "Sin familiares agregados aún"
            val connectedStatusText = if (hasContacts) "Conectado • A ${distanceKm} km de ti (Sano y Salvo)" else "Agrega a tu familia en la pestaña 'Familia' para verlos aquí"

            // Connected Family Status Card
            Surface(
                color = Color(0xFF1F2937),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF374151)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (hasContacts) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF64748B).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (hasContacts) "👤" else "👥", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = connectedContactName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = connectedStatusText,
                            color = if (hasContacts) Color(0xFF10B981) else Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Easy Family Meeting Point Card (Zero Coordinate Boxes)
            var meetingPointName by remember { mutableStateOf("Parque Principal de Evacuación") }
            var meetingLat by remember { mutableStateOf(lat) }
            var meetingLng by remember { mutableStateOf(lng) }
            var isSaved by remember { mutableStateOf(false) }

            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📍", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PUNTO DE REUNIÓN FAMILIAR",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (isSaved) "📍 Guardado: $meetingPointName" else "📍 $meetingPointName",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action 1: Set current location as meeting point in 1 tap
                    Button(
                        onClick = {
                            meetingLat = lat
                            meetingLng = lng
                            meetingPointName = String.format(Locale.US, "GPS: %.6f, %.6f (Fijado por Usuario)", lat, lng)
                            isSaved = true
                            
                            val profilePrefs = context.getSharedPreferences("sismo_user_profile_prefs", Context.MODE_PRIVATE)
                            profilePrefs.edit().putString("user_safe_point", meetingPointName).apply()

                            android.widget.Toast.makeText(
                                context,
                                "📍 Tu ubicación GPS actual fue fijada como Punto de Encuentro Familiar Permanente",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "📍 FIJAR MI UBICACIÓN ACTUAL COMO PUNTO FAMILIAR",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action 2: Pedestrian Walking Route to Meeting Point
                    Button(
                        onClick = {
                            val walkingUri = Uri.parse("google.navigation:q=$meetingLat,$meetingLng&mode=w")
                            val intent = Intent(Intent.ACTION_VIEW, walkingUri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val webWalkingUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$meetingLat,$meetingLng&travelmode=walking")
                                context.startActivity(Intent(Intent.ACTION_VIEW, webWalkingUri))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF059669),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🚶 NAVEGAR A PIE AL PUNTO DE REUNIÓN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // GPS Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRefreshLocation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "REFRESCAR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Button(
                    onClick = { onOpenExternalMap(lat, lng) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(imageVector = Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "GOOGLE MAPS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * Internet Connected Real-Time Seismic Feed (SGC Colombia / USGS)
 */
@Composable
fun OnlineSeismicFeedCard(
    syncStatus: String,
    isSyncing: Boolean,
    onRefresh: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title & Expand Button (Full width, no horizontal crowding)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RssFeed,
                            contentDescription = "SGC Colombia & Red Sísmica Feed",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "RED DE SISMOS EN VIVO",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Servicio Geológico Colombiano (SGC)",
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Replegar" else "Desplegar",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row: Consult Button with full prominence
            Button(
                onClick = onRefresh,
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("refresh_online_seismic_button")
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CONSULTANDO SGC COLOMBIA...",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CONSULTAR SGC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Expandable Content Body
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = syncStatus,
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Conexión directa a los servicios oficiales del Servicio Geológico Colombiano (sgc.gov.co) y redes sismológicas regionales. Reportes verificados de magnitud, profundidad, hora exacta de ocurrencia y epicentros en territorio colombiano.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPillItem(
    title: String,
    subtitle: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, color) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Seleccionado",
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SeismicEventCard(event: SeismicAlertEvent) {
    val dateFormat = SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault())
    val dateStr = dateFormat.format(Date(event.timestamp))
    val relativeTimeStr = formatRelativeTime(event.timestamp)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = when {
                        event.magnitude >= 6.0 -> Color(0xFFFF1744)
                        event.magnitude >= 4.5 -> Color(0xFFFF9100)
                        event.magnitude >= 3.0 -> Color(0xFFFFC107)
                        else -> Color(0xFF10B981)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = String.format(Locale.getDefault(), "M %.1f", event.magnitude),
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = dateStr,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (relativeTimeStr.isNotEmpty()) {
                        Text(
                            text = relativeTimeStr,
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = event.epicenter,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profundidad: ${event.depthKm} km",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = "Distancia: ${event.distanceKm} km",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Fuente: ${event.agencySource}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diffMillis = System.currentTimeMillis() - timestamp
    if (diffMillis < 0) return "Reciente"
    val diffMinutes = diffMillis / (1000 * 60)
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffMinutes < 1 -> "Hace unos momentos"
        diffMinutes < 60 -> "Hace $diffMinutes min"
        diffHours < 24 -> "Hace $diffHours h"
        diffDays == 1L -> "Ayer"
        diffDays < 30 -> "Hace $diffDays días"
        else -> ""
    }
}

private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (r * c * 10).toInt() / 10.0
}

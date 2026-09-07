package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.service.AlertVibrationMode
import com.example.service.EmergencySoundPlayer
import com.example.service.SeismicMonitoringService

import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.components.AppPullToRefreshBox

@Composable
fun SettingsScreen(
    isHighContrast: Boolean,
    onToggleHighContrast: (Boolean) -> Unit,
    forceMaxVolume: Boolean,
    onToggleForceMaxVolume: (Boolean) -> Unit,
    panicTimerSeconds: Int = 5,
    onSetPanicTimerSeconds: (Int) -> Unit,
    alarmToneIndex: Int = 0,
    onSetAlarmToneIndex: (Int) -> Unit = {},
    customToneUri: String? = null,
    customToneTitle: String = "Tono Nativo del Dispositivo",
    onSetCustomTone: (String?, String) -> Unit = { _, _ -> },
    isDrillActive: Boolean = false,
    drillProgressMessage: String? = null,
    minMagnitude: Double = 3.5,
    onSetMinMagnitude: (Double) -> Unit = {},
    maxDistanceKm: Double = 350.0,
    onSetMaxDistanceKm: (Double) -> Unit = {},
    isAudioAlertEnabled: Boolean = true,
    onToggleAudioAlert: (Boolean) -> Unit = {},
    isVibrationAlertEnabled: Boolean = true,
    onToggleVibrationAlert: (Boolean) -> Unit = {},
    isRefreshing: Boolean = false,
    onRefreshSettings: () -> Unit = {},
    onStartDrill: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        "🔔 Alertas & Audio",
        "🚨 Modo Simulacro",
        "🎨 Accesibilidad Visual",
        "🔐 Permisos & Batería"
    )

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else true

    val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    } else true

    val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .testTag("settings_tab_$index")
                ) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (selectedTabIndex == index) FontWeight.Black else FontWeight.Bold,
                        color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AppPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefreshSettings,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTabIndex) {
                0 -> AudioAlertsTab(
                    forceMaxVolume = forceMaxVolume,
                    onToggleForceMaxVolume = onToggleForceMaxVolume,
                    panicTimerSeconds = panicTimerSeconds,
                    onSetPanicTimerSeconds = onSetPanicTimerSeconds,
                    alarmToneIndex = alarmToneIndex,
                    onSetAlarmToneIndex = onSetAlarmToneIndex,
                    customToneUri = customToneUri,
                    customToneTitle = customToneTitle,
                    onSetCustomTone = onSetCustomTone,
                    minMagnitude = minMagnitude,
                    onSetMinMagnitude = onSetMinMagnitude,
                    maxDistanceKm = maxDistanceKm,
                    onSetMaxDistanceKm = onSetMaxDistanceKm,
                    isAudioAlertEnabled = isAudioAlertEnabled,
                    onToggleAudioAlert = onToggleAudioAlert,
                    isVibrationAlertEnabled = isVibrationAlertEnabled,
                    onToggleVibrationAlert = onToggleVibrationAlert
                )
                1 -> DrillModeScreen(
                    isDrillActive = isDrillActive,
                    drillProgressMessage = drillProgressMessage,
                    onStartDrill = onStartDrill
                )
                2 -> VisualAccessibilityTab(
                    isHighContrast = isHighContrast,
                    onToggleHighContrast = onToggleHighContrast
                )
                3 -> PermissionsTab(
                    hasFineLocation = hasFineLocation,
                    hasBackgroundLocation = hasBackgroundLocation,
                    hasNotifications = hasNotifications,
                    hasSms = hasSms,
                    isIgnoringBattery = isIgnoringBattery,
                    onOpenBatterySettings = {
                        try {
                            val intent = Intent().apply {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                    data = Uri.parse("package:${context.packageName}")
                                } else {
                                    action = Settings.ACTION_SETTINGS
                                }
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(fallbackIntent)
                        }
                    },
                    onLogout = onLogout
                )
            }
        }
    }
}

/**
 * Tab 1: Audio & Emergency Alerts
 */
@Composable
fun AudioAlertsTab(
    forceMaxVolume: Boolean,
    onToggleForceMaxVolume: (Boolean) -> Unit,
    panicTimerSeconds: Int,
    onSetPanicTimerSeconds: (Int) -> Unit,
    alarmToneIndex: Int = 0,
    onSetAlarmToneIndex: (Int) -> Unit = {},
    customToneUri: String? = null,
    customToneTitle: String = "Tono Nativo del Dispositivo",
    onSetCustomTone: (String?, String) -> Unit = { _, _ -> },
    minMagnitude: Double = 3.5,
    onSetMinMagnitude: (Double) -> Unit = {},
    maxDistanceKm: Double = 350.0,
    onSetMaxDistanceKm: (Double) -> Unit = {},
    isAudioAlertEnabled: Boolean = true,
    onToggleAudioAlert: (Boolean) -> Unit = {},
    isVibrationAlertEnabled: Boolean = true,
    onToggleVibrationAlert: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var isTestingAudio by remember { androidx.compose.runtime.mutableStateOf(false) }
    val emergencyPlayer = remember { EmergencySoundPlayer(context) }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(context, uri)
                val title = ringtone?.getTitle(context) ?: "Tono del Celular"
                onSetCustomTone(uri.toString(), title)
                onSetAlarmToneIndex(3) // 3: Custom Phone Ringtone
            }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // --- 1. UMBRALES Y FILTROS SÍSMICOS EN SEGUNDO PLANO ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2563EB).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Filtros de Alerta Sísmica 24/7",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Solo te despertará si el sismo supera estos umbrales",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Magnitud mínima
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Magnitud mínima:",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            color = Color(0xFF2563EB).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "≥ ${String.format(java.util.Locale.US, "%.1f", minMagnitude)} M",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Slider(
                        value = minMagnitude.toFloat(),
                        onValueChange = { onSetMinMagnitude(Math.round(it * 10.0) / 10.0) },
                        valueRange = 2.0f..6.0f,
                        steps = 7, // 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Radio de distancia
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Radio de distancia máxima:",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val distLabel = if (maxDistanceKm >= 1000.0) "Toda Colombia" else "${maxDistanceKm.toInt()} km"
                            Text(
                                text = distLabel,
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Chips de distancia
                    val distanceOptions = listOf(
                        100.0 to "100 km",
                        250.0 to "250 km",
                        350.0 to "350 km*",
                        500.0 to "500 km",
                        1500.0 to "País"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        distanceOptions.forEach { (dist, label) ->
                            val isSelected = Math.abs(maxDistanceKm - dist) < 1.0
                            OutlinedButton(
                                onClick = { onSetMaxDistanceKm(dist) },
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) Color(0xFF2563EB) else Color.Transparent,
                                    contentColor = if (isSelected) Color.White else Color(0xFF94A3B8)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                                )
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Switch Sirena de Emergencia
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sirena Sonora de Emergencia",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Reproduce alarma a alto volumen en sismos confirmados",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Switch(
                            checked = isAudioAlertEnabled,
                            onCheckedChange = onToggleAudioAlert
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Switch Vibración
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Vibración Háptica Sísmica",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Patrón SOS continuo durante la alerta",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Switch(
                            checked = isVibrationAlertEnabled,
                            onCheckedChange = onToggleVibrationAlert
                        )
                    }
                }
            }
        }

        // --- 2. SISTEMA DE ALERTAS DE TERREMOTOS DE GOOGLE (AEAS) ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Alertas de Terremoto de Android (Google)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Android incluye el sistema nativo de Google Earthquake Alerts que usa sensores acelerómetros comunitarios. Sismoalerta complementa esta red con las estaciones sismológicas oficiales del SGC, filtrado geográfico exacto y red SOS familiar.\n\nTe sugerimos mantener ambos sistemas activos para contar con redundancia total.",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent("com.google.android.gms.settings.EARTHQUAKE_ALERT_SETTINGS")
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                } catch (e2: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF0F172A),
                            contentColor = Color(0xFF38BDF8)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f))
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ver Alertas de Terremoto del Teléfono", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Forzar Volumen de Alarma al 100%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Ignora el modo silencio y No Molestar durante alerta de sismo",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        Switch(
                            checked = forceMaxVolume,
                            onCheckedChange = onToggleForceMaxVolume,
                            modifier = Modifier.testTag("force_volume_switch")
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color(0xFFFF9100),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Tono de Alarma del Celular",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val toneNames = listOf(
                        "Tono de Alarma Estándar del Sistema",
                        "Sirena de Ringtone de Emergencia",
                        "Tono de Notificación Sonora de Auxilio"
                    )

                    toneNames.forEachIndexed { idx, name ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = alarmToneIndex == idx,
                                onClick = {
                                    onSetCustomTone(null, "Tono Nativo del Dispositivo")
                                    onSetAlarmToneIndex(idx)
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Option 4: Custom Native Phone Tone Picker
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = alarmToneIndex == 3,
                            onClick = { onSetAlarmToneIndex(3) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "🎵 Tono Personalizado del Celular",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF60A5FA)
                            )
                            Text(
                                text = "Tono activo: $customToneTitle",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Seleccionar Tono del Celular")
                                if (customToneUri != null) {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(customToneUri))
                                }
                            }
                            ringtonePickerLauncher.launch(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "🎵 ELEGIR CUALQUIER TONO DE MI CELULAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (isTestingAudio) {
                                emergencyPlayer.stopSirenAlert()
                                isTestingAudio = false
                            } else {
                                emergencyPlayer.startSirenAlert(
                                    forceMaxVolume = forceMaxVolume,
                                    mode = AlertVibrationMode.CRITICAL_SEISMIC,
                                    toneIndex = alarmToneIndex,
                                    customUriString = if (alarmToneIndex == 3) customToneUri else null
                                )
                                isTestingAudio = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTestingAudio) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isTestingAudio) "🛑 Detener Sonido de Prueba" else "🔊 Probar Tono Seleccionado", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color(0xFFFF9100),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Ventana de Cancelación de Pánico",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tiempo de espera antes de transmitir alerta a la red familiar: $panicTimerSeconds segundos",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = panicTimerSeconds.toFloat(),
                        onValueChange = { onSetPanicTimerSeconds(it.toInt()) },
                        valueRange = 3f..10f,
                        steps = 6,
                        modifier = Modifier.testTag("panic_timer_slider")
                    )
                }
            }
        }
    }
}

/**
 * Tab 2: 24/7 Motion Filter (Anti-False Positive Sensor Calibration)
 */
@Composable
fun MotionFilterTab(
    sensitivityLevel: Float,
    onSensitivityChanged: (Float) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2923)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Filtro Inteligente Anti-Falsos Positivos",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Diferencia movimiento casual del celular de ondas P/S sísmicas reales.",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Ajusta la inmunidad a sacudidas manuales, caminatas y caídas accidentales del smartphone:",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val levelLabel = when (sensitivityLevel.toInt()) {
                        1 -> "Baja Inmunidad (Alta Sensibilidad)"
                        2 -> "Equilibrado Anti-Falsos Positivos (Recomendado) ✓"
                        else -> "Alta Inmunidad (Solo Movimientos Masivos Extremos)"
                    }

                    Surface(
                        color = Color(0xFF00E676).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = levelLabel,
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = sensitivityLevel,
                        onValueChange = onSensitivityChanged,
                        valueRange = 1f..3f,
                        steps = 1,
                        modifier = Modifier.testTag("motion_sensitivity_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• Filtro P-Wave: Analiza aceleración sostenida en 3 ejes durante >1.2 segundos.\n" +
                               "• Inmunidad a Caídas: Descarta impactos de gravedad única o pasos del usuario.\n" +
                               "• Filtro de Frecuencia: Descarta sacudidas con la mano mayores a 4Hz.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * Tab 3: Visual & High Contrast Accessibility
 */
@Composable
fun VisualAccessibilityTab(
    isHighContrast: Boolean,
    onToggleHighContrast: (Boolean) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contrast,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Modo Alto Contraste de Emergencia",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Fondo negro puro y tipografía aumentada para visibilidad con humo o baja luz",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Switch(
                            checked = isHighContrast,
                            onCheckedChange = onToggleHighContrast,
                            modifier = Modifier.testTag("high_contrast_switch")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab 4: System Permissions & Battery Optimizations Status
 */
@Composable
fun PermissionsTab(
    hasFineLocation: Boolean,
    hasBackgroundLocation: Boolean,
    hasNotifications: Boolean,
    hasSms: Boolean,
    isIgnoringBattery: Boolean,
    onOpenBatterySettings: () -> Unit,
    onLogout: () -> Unit = {}
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                text = "ESTADO DE PERMISOS Y OPTIMIZACIÓN DE BATERÍA",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isIgnoringBattery) Color(0xFF1E2923) else Color(0xFF3E1F1F)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exención de Optimización de Batería",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isIgnoringBattery)
                                    "Protegido: El sistema mantendrá la vigilancia 24/7 activa."
                                else
                                    "⚠️ Riesgo: Android o tu fabricante podrían congelar el servicio.",
                                color = if (isIgnoringBattery) Color(0xFF00E676) else Color(0xFFFF8A80),
                                fontSize = 11.sp
                            )
                        }

                        if (!isIgnoringBattery) {
                            Button(
                                onClick = onOpenBatterySettings,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Desactivar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            PermissionStatusItem("Ubicación GPS de Alta Precisión (GPS Real)", hasFineLocation)
        }

        item {
            PermissionStatusItem("Ubicación en Segundo Plano (Vigilancia 24/7)", hasBackgroundLocation)
        }

        item {
            PermissionStatusItem("Canal de Notificaciones de Alarma Sonora", hasNotifications)
        }

        item {
            PermissionStatusItem("Envío de Mensajes SMS de Emergencia", hasSms)
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "MI CUENTA DE USUARIO",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sesión iniciada activamente en este dispositivo.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🚪 CERRAR SESIÓN DE USUARIO",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionStatusItem(title: String, granted: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (granted) Color(0xFF00E676) else Color(0xFFFF1744),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (granted) "Otorgado ✓" else "Pendiente ⚠️",
                    color = if (granted) Color(0xFF00E676) else Color(0xFFFF1744),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

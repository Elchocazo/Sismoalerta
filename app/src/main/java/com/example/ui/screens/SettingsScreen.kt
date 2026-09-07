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
                    onSetCustomTone = onSetCustomTone
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
    onSetCustomTone: (String?, String) -> Unit = { _, _ -> }
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

package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.PanicCountdownOverlay
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DrillModeScreen
import com.example.ui.screens.FamilyMapScreen
import com.example.ui.screens.ResilienceKitScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SismoAlertaTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        (application as? SismoAlertaApp)?.locationRepository?.refreshActualDeviceLocation()
        startMonitoringServiceIfAllowed()
    }

    private val foregroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        (application as? SismoAlertaApp)?.locationRepository?.refreshActualDeviceLocation()
        startMonitoringServiceIfAllowed()

        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (fineGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestRequiredPermissions()
        startMonitoringServiceIfAllowed()
        // Cancelar cualquier alarma exacta legacy agresiva para permitir el reposo de batería Doze
        com.example.receiver.SeismicAlarmReceiver.cancelPeriodicCheck(this)

        val app = application as SismoAlertaApp

        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(
                    seismicRepo = app.seismicRepository,
                    locationRepo = app.locationRepository,
                    familyRepo = app.familyRepository,
                    settingsRepo = app.settingsRepository,
                    nucleusRepo = app.nucleusRepository,
                    context = applicationContext
                )
            )

            val isHighContrast by viewModel.isHighContrast.collectAsStateWithLifecycle()

            SismoAlertaTheme(isHighContrast = isHighContrast) {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    private fun startMonitoringServiceIfAllowed() {
        try {
            val serviceIntent = android.content.Intent(this, com.example.service.SeismicMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestRequiredPermissions() {
        val foregroundPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.VIBRATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            foregroundPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingForeground = foregroundPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingForeground.isNotEmpty()) {
            foregroundPermissionLauncher.launch(missingForeground.toTypedArray())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        // Solicitar exención de ahorro de batería para evitar que el sistema congele las alertas 24/7
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val batteryIntent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(batteryIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val authPrefs = remember { context.getSharedPreferences("sismo_auth_prefs", android.content.Context.MODE_PRIVATE) }
    val hasFirebaseUser = remember {
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null
        } catch (e: Exception) {
            false
        }
    }

    var isAuthenticated by remember {
        mutableStateOf(hasFirebaseUser || authPrefs.getBoolean("is_authenticated", false))
    }

    val activity = context as? androidx.activity.ComponentActivity
    val initialTab = activity?.intent?.getStringExtra("NAVIGATE_TO") ?: "dashboard"
    var selectedTab by remember { mutableStateOf(initialTab) }

    androidx.compose.runtime.DisposableEffect(activity?.intent) {
        val target = activity?.intent?.getStringExtra("NAVIGATE_TO")
        if (target != null) {
            selectedTab = target
        }
        onDispose {}
    }

    val currentStatus by viewModel.currentStatus.collectAsStateWithLifecycle()
    val isPanicCountdownActive by viewModel.isPanicCountdownActive.collectAsStateWithLifecycle()
    val panicCountdownRemaining by viewModel.panicCountdownRemaining.collectAsStateWithLifecycle()

    val sensorMagnitude by viewModel.liveMagnitude.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val latestSeismicEvent by viewModel.latestSeismicEvent.collectAsStateWithLifecycle()
    val seismicEvents by viewModel.seismicEvents.collectAsStateWithLifecycle()

    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val breadcrumbs by viewModel.breadcrumbs.collectAsStateWithLifecycle()
    val alertLogs by viewModel.alertLogs.collectAsStateWithLifecycle()

    val isHighContrast by viewModel.isHighContrast.collectAsStateWithLifecycle()
    val forceMaxVolume by viewModel.forceMaxVolume.collectAsStateWithLifecycle()
    val isDrillActive by viewModel.isDrillActive.collectAsStateWithLifecycle()
    val drillProgressMessage by viewModel.drillProgressMessage.collectAsStateWithLifecycle()
    val isTrappedBeaconActive by viewModel.isTrappedBeaconActive.collectAsStateWithLifecycle()
    val firestoreSyncStatus by viewModel.firestoreSyncStatus.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val onlineSyncStatus by viewModel.onlineSyncStatus.collectAsStateWithLifecycle()
    val isSyncingOnlineSeismic by viewModel.isSyncingOnlineSeismic.collectAsStateWithLifecycle()

    val userSharingCode by viewModel.userSharingCode.collectAsStateWithLifecycle()
    val joinedNuclei by viewModel.joinedNuclei.collectAsStateWithLifecycle()
    val nucleusMembersMap by viewModel.nucleusMembersMap.collectAsStateWithLifecycle()
    val isLiveLocationSharingActive by viewModel.isLiveLocationSharingActive.collectAsStateWithLifecycle()
    val alarmToneIndex by viewModel.alarmToneIndex.collectAsStateWithLifecycle()
    val customToneUri by viewModel.customToneUri.collectAsStateWithLifecycle()
    val customToneTitle by viewModel.customToneTitle.collectAsStateWithLifecycle()
    val profileSyncVersion by viewModel.profileSyncVersion.collectAsStateWithLifecycle()

    val minAlertMagnitude by viewModel.minAlertMagnitude.collectAsStateWithLifecycle()
    val maxAlertDistanceKm by viewModel.maxAlertDistanceKm.collectAsStateWithLifecycle()
    val isAudioAlertEnabled by viewModel.isAudioAlertEnabled.collectAsStateWithLifecycle()
    val isVibrationAlertEnabled by viewModel.isVibrationAlertEnabled.collectAsStateWithLifecycle()
    val batteryLevel by viewModel.batteryLevel.collectAsStateWithLifecycle()

    val isRefreshingDashboard by viewModel.isRefreshingDashboard.collectAsStateWithLifecycle()
    val isRefreshingFamily by viewModel.isRefreshingFamily.collectAsStateWithLifecycle()
    val isRefreshingProfile by viewModel.isRefreshingProfile.collectAsStateWithLifecycle()
    val isRefreshingKit by viewModel.isRefreshingKit.collectAsStateWithLifecycle()
    val isRefreshingSettings by viewModel.isRefreshingSettings.collectAsStateWithLifecycle()

    if (!isAuthenticated) {
        com.example.ui.screens.AuthScreen(
            onAuthSuccess = { userId, userName, userEmail ->
                val prefs = context.getSharedPreferences("sismo_user_profile_prefs", android.content.Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.putString("user_name", userName)
                if (userEmail.isNotBlank()) {
                    editor.putString("user_email", userEmail)
                }
                editor.apply()

                viewModel.syncUserProfileFromCloud(userId, userEmail)
                authPrefs.edit().putBoolean("is_authenticated", true).apply()
                isAuthenticated = true
            },
            onSkipGuest = {
                authPrefs.edit().putBoolean("is_authenticated", true).apply()
                isAuthenticated = true
            }
        )
    } else {
        Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = if (isHighContrast) Color.Black else Color(0xFF1A1A1A),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedTab == "dashboard",
                    onClick = { selectedTab = "dashboard" },
                    icon = { Icon(imageVector = Icons.Default.Warning, contentDescription = null) },
                    label = { Text("Alerta", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFF1744),
                        selectedTextColor = Color(0xFFFF1744),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    ),
                    modifier = Modifier.testTag("nav_dashboard")
                )

                NavigationBarItem(
                    selected = selectedTab == "family",
                    onClick = { selectedTab = "family" },
                    icon = { Icon(imageVector = Icons.Default.People, contentDescription = null) },
                    label = { Text("Familia", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFF1744),
                        selectedTextColor = Color(0xFFFF1744),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    ),
                    modifier = Modifier.testTag("nav_family")
                )

                NavigationBarItem(
                    selected = selectedTab == "profile",
                    onClick = { selectedTab = "profile" },
                    icon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) },
                    label = { Text("Perfil", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFF1744),
                        selectedTextColor = Color(0xFFFF1744),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    ),
                    modifier = Modifier.testTag("nav_profile")
                )

                NavigationBarItem(
                    selected = selectedTab == "kit",
                    onClick = { selectedTab = "kit" },
                    icon = { Icon(imageVector = Icons.Default.Work, contentDescription = null) },
                    label = { Text("Mochila", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFF1744),
                        selectedTextColor = Color(0xFFFF1744),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    ),
                    modifier = Modifier.testTag("nav_kit")
                )

                NavigationBarItem(
                    selected = selectedTab == "settings",
                    onClick = { selectedTab = "settings" },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Ajustes", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFF1744),
                        selectedTextColor = Color(0xFFFF1744),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    ),
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                "dashboard" -> DashboardScreen(
                    currentStatus = currentStatus,
                    onStatusSelected = { viewModel.updateStatus(it) },
                    sensorMagnitude = sensorMagnitude,
                    isServiceRunning = isServiceRunning,
                    onToggleService = { viewModel.toggleService() },
                    latestSeismicEvent = latestSeismicEvent,
                    seismicEvents = seismicEvents,
                    batteryLevel = batteryLevel,
                    onTriggerPanic = { viewModel.triggerPanicAlert() },
                    isTrappedBeaconActive = isTrappedBeaconActive,
                    onStopTrappedBeacon = { viewModel.stopTrappedBeacon() },
                    onStartTrappedBeacon = { viewModel.startTrappedBeacon() },
                    currentLocation = currentLocation,
                    contacts = contacts,
                    onlineSyncStatus = onlineSyncStatus,
                    isSyncingOnline = isSyncingOnlineSeismic,
                    isRefreshing = isRefreshingDashboard,
                    onRefreshOnlineSeismic = { viewModel.syncOnlineSeismicFeeds() },
                    onRefreshLocation = { viewModel.refreshDeviceLocation() },
                    onRefreshDashboard = { viewModel.refreshDashboard() }
                )

                "family" -> FamilyMapScreen(
                    contacts = contacts,
                    breadcrumbs = breadcrumbs,
                    alertLogs = alertLogs,
                    batteryLevel = batteryLevel,
                    onAddContact = { name, phone, rel -> viewModel.addContact(name, phone, rel) },
                    onDeleteContact = { viewModel.deleteContact(it) },
                    firestoreSyncStatus = firestoreSyncStatus,
                    joinedNuclei = joinedNuclei,
                    nucleusMembersMap = nucleusMembersMap,
                    userSharingCode = userSharingCode,
                    isLiveLocationSharingActive = isLiveLocationSharingActive,
                    isRefreshing = isRefreshingFamily,
                    onRefreshFamily = { viewModel.refreshFamilyData() },
                    onJoinNucleusCode = { viewModel.joinNucleusByCode(it) },
                    onToggleLiveLocationSharing = { viewModel.toggleLiveLocationSharing(it) },
                    onPulseCurrentLocation = { viewModel.publishCurrentLocationPulse(forceImmediate = true) },
                    currentLocation = currentLocation,
                    currentUserId = viewModel.currentUserId
                )

                "profile" -> com.example.ui.screens.UserProfileScreen(
                    contacts = contacts,
                    userSharingCode = userSharingCode,
                    profileSyncVersion = profileSyncVersion,
                    isRefreshing = isRefreshingProfile,
                    onRefreshProfile = { viewModel.refreshUserProfile() },
                    onSaveProfile = { viewModel.saveEmergencyProfile(it) },
                    onSignOut = {
                        authPrefs.edit().clear().apply()
                        try {
                            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                        } catch (e: Exception) {}
                        isAuthenticated = false
                    }
                )

                "kit" -> ResilienceKitScreen(
                    isRefreshing = isRefreshingKit,
                    onRefreshKit = { viewModel.refreshResilienceKit() }
                )

                "settings" -> SettingsScreen(
                    isHighContrast = isHighContrast,
                    onToggleHighContrast = { viewModel.setHighContrast(it) },
                    forceMaxVolume = forceMaxVolume,
                    onToggleForceMaxVolume = { viewModel.setForceMaxVolume(it) },
                    panicTimerSeconds = 5,
                    onSetPanicTimerSeconds = { viewModel.setPanicTimerSeconds(it) },
                    alarmToneIndex = alarmToneIndex,
                    onSetAlarmToneIndex = { viewModel.setAlarmToneIndex(it) },
                    customToneUri = customToneUri,
                    customToneTitle = customToneTitle,
                    onSetCustomTone = { uriStr, title -> viewModel.setCustomTone(uriStr, title) },
                    isDrillActive = isDrillActive,
                    drillProgressMessage = drillProgressMessage,
                    minMagnitude = minAlertMagnitude,
                    onSetMinMagnitude = { viewModel.setMinAlertMagnitude(it) },
                    maxDistanceKm = maxAlertDistanceKm,
                    onSetMaxDistanceKm = { viewModel.setMaxAlertDistanceKm(it) },
                    isAudioAlertEnabled = isAudioAlertEnabled,
                    onToggleAudioAlert = { viewModel.setAudioAlertEnabled(it) },
                    isVibrationAlertEnabled = isVibrationAlertEnabled,
                    onToggleVibrationAlert = { viewModel.setVibrationAlertEnabled(it) },
                    isRefreshing = isRefreshingSettings,
                    onRefreshSettings = { viewModel.refreshSettings() },
                    onStartDrill = { viewModel.startDrillSimulation() },
                    onLogout = {
                        authPrefs.edit().putBoolean("is_authenticated", false).apply()
                        try {
                            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                        } catch (e: Exception) {}
                        isAuthenticated = false
                    }
                )
            }

            if (isPanicCountdownActive) {
                PanicCountdownOverlay(
                    secondsRemaining = panicCountdownRemaining,
                    onCancelPanic = { viewModel.cancelPanicAlert() }
                )
            }
        }
    }
}}

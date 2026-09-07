package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.data.local.entity.BreadcrumbLocation
import com.example.data.local.entity.EmergencyAlertLog
import com.example.data.local.entity.EmergencyContact
import com.example.data.model.NucleusGroup
import com.example.data.model.NucleusMember
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.ui.components.AppPullToRefreshBox

@Composable
fun FamilyMapScreen(
    contacts: List<EmergencyContact>,
    breadcrumbs: List<BreadcrumbLocation>,
    alertLogs: List<EmergencyAlertLog>,
    batteryLevel: Int,
    onAddContact: (String, String, String) -> Unit,
    onDeleteContact: (EmergencyContact) -> Unit,
    firestoreSyncStatus: String = "Conectado al Círculo Familiar",
    joinedNuclei: List<NucleusGroup> = listOf(
        NucleusGroup("FAM-8742", "Familia"),
        NucleusGroup("AMG-9102", "Amigos"),
        NucleusGroup("TRB-5531", "Trabajo"),
        NucleusGroup("VEC-3094", "Vecinos")
    ),
    nucleusMembersMap: Map<String, List<NucleusMember>> = emptyMap(),
    userSharingCode: String = "SISMO-87A4",
    isLiveLocationSharingActive: Boolean = true,
    isRefreshing: Boolean = false,
    onRefreshFamily: () -> Unit = {},
    onJoinNucleusCode: (String) -> Unit = {},
    onToggleLiveLocationSharing: (Boolean) -> Unit = {},
    onPulseCurrentLocation: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        "👥 Círculos",
        "📡 Transmisión SOS",
        "📞 Contactos"
    )

    var selectedNucleusCode by remember(joinedNuclei) {
        mutableStateOf(joinedNuclei.firstOrNull()?.code ?: "FAM-8742")
    }

    val activeNucleus = joinedNuclei.find { it.code == selectedNucleusCode }
        ?: joinedNuclei.firstOrNull()
        ?: NucleusGroup("FAM-8742", "Familia")

    val activeMembers = nucleusMembersMap[activeNucleus.code] ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Sub-Tab Navigation Bar (Limpia y ajustada a la pantalla)
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .testTag("family_tab_$index")
                ) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (selectedTabIndex == index) FontWeight.Black else FontWeight.Bold,
                        color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AppPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                onRefreshFamily()
                onPulseCurrentLocation()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTabIndex) {
                0 -> NucleiAndGroupTab(
                    joinedNuclei = joinedNuclei,
                    selectedNucleusCode = selectedNucleusCode,
                    onSelectNucleus = { selectedNucleusCode = it },
                    activeNucleus = activeNucleus,
                    activeMembers = activeMembers,
                    userSharingCode = userSharingCode,
                    onJoinNucleusCode = onJoinNucleusCode
                )
                1 -> LiveLocationSharingTab(
                    isLiveLocationSharingActive = isLiveLocationSharingActive,
                    onToggleLiveLocationSharing = onToggleLiveLocationSharing,
                    onPulseCurrentLocation = onPulseCurrentLocation
                )
                2 -> ContactsSOSTab(
                    contacts = contacts,
                    batteryLevel = batteryLevel,
                    onAddContact = onAddContact,
                    onDeleteContact = onDeleteContact
                )
            }
        }
    }
}

/**
 * Pestaña 1: Círculos y Núcleos de Integrantes
 */
@Composable
fun NucleiAndGroupTab(
    joinedNuclei: List<NucleusGroup>,
    selectedNucleusCode: String,
    onSelectNucleus: (String) -> Unit,
    activeNucleus: NucleusGroup,
    activeMembers: List<NucleusMember>,
    userSharingCode: String,
    onJoinNucleusCode: (String) -> Unit
) {
    val context = LocalContext.current
    var showJoinDialog by remember { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "REDES DE AUXILIO Y FAMILIARES",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Código ${activeNucleus.name}: ${activeNucleus.code}",
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dynamic Category/Nucleus Selector Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        joinedNuclei.forEach { nucleus ->
                            val isSelected = selectedNucleusCode == nucleus.code
                            Surface(
                                onClick = { onSelectNucleus(nucleus.code) },
                                color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = nucleus.name,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "¡Únete a mi círculo de ${activeNucleus.name} en SismoAlerta! Usa el Código de Grupo: ${activeNucleus.code} o mi código personal $userSharingCode para estar sincronizados."
                                    )
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Compartir Código ${activeNucleus.name}"))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "COMPARTIR CÓDIGO", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }

                        Button(
                            onClick = { showJoinDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF334155),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "UNIRSE A NÚCLEO", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "INTEGRANTES EN NÚCLEO ${activeNucleus.name.uppercase()} (${activeMembers.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }

        if (activeMembers.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Aún no hay otros miembros en este grupo de ${activeNucleus.name}.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Comparte el Código de Grupo ${activeNucleus.code} o tu código personal $userSharingCode para que tus contactos se unan a esta pestaña.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else {
            items(activeMembers) { member ->
                NucleusMemberCard(member = member)
            }
        }
    }

    if (showJoinDialog) {
        var inputCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Unirse a un Núcleo por Código", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Ingresa el código de 6-8 caracteres compartido por tu contacto:", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it.uppercase() },
                        label = { Text("Código de Círculo (ej. FAM-8742)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputCode.isNotBlank()) {
                            onJoinNucleusCode(inputCode.trim())
                            onSelectNucleus(inputCode.trim())
                            showJoinDialog = false
                            android.widget.Toast.makeText(
                                context,
                                "✅ Te has unido al Círculo ${inputCode.trim()}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Text("Conectar y Sincronizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Pestaña 2: Transmisión GPS en Vivo
 */
@Composable
fun LiveLocationSharingTab(
    isLiveLocationSharingActive: Boolean,
    onToggleLiveLocationSharing: (Boolean) -> Unit,
    onPulseCurrentLocation: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (isLiveLocationSharingActive) Color(0xFF10B981) else Color(0xFF475569)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isLiveLocationSharingActive) Color(0xFF10B981).copy(alpha = 0.2f)
                                        else Color(0xFF475569).copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = if (isLiveLocationSharingActive) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "TRANSMISIÓN GPS EN TIEMPO REAL",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isLiveLocationSharingActive) "🟢 Compartiendo posición GPS continua cada 20s" else "⚪ Transmisión de ubicación pausada",
                                    color = if (isLiveLocationSharingActive) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onToggleLiveLocationSharing(!isLiveLocationSharingActive) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLiveLocationSharingActive) Color(0xFFEF4444) else Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isLiveLocationSharingActive) "PAUSAR TRANSMISIÓN" else "COMPARTIR MI UBICACIÓN",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Button(
                            onClick = onPulseCurrentLocation,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "ENVIAR PULSO AHORA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                    Text(
                        text = "VIGILANCIA Y TRANSMISIÓN SEGURA EN LA NUBE",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Tu ubicación se transmite únicamente a las personas que pertenezcan a tus mismos núcleos de auxilio.\n" +
                               "• La información incluye nivel de batería en vivo y estado de salud SOS para facilitar el rescate en emergencias.\n" +
                               "• Si el teléfono se queda sin conexión a internet, guarda las coordenadas en la memoria interna y sincroniza automáticamente al reconectarse.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * Pestaña 3: Mi Ubicación GPS & Navegación Peatonal
 */
@Composable
fun MyLocationTab(
    breadcrumbs: List<BreadcrumbLocation>
) {
    val context = LocalContext.current
    val lastLoc = breadcrumbs.lastOrNull()
    val userLat = lastLoc?.latitude ?: 2.4750833
    val userLng = lastLoc?.longitude ?: -76.5598283

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Tu Ubicación GPS Actual en Vivo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = String.format(Locale.US, "Lat: %.6f, Lng: %.6f", userLat, userLng),
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val latStr = String.format(Locale.US, "%.6f", userLat)
                            val lngStr = String.format(Locale.US, "%.6f", userLng)
                            val mapUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latStr,$lngStr&travelmode=walking")
                            val intent = Intent(Intent.ACTION_VIEW, mapUri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latStr,$lngStr&travelmode=walking")
                                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "🚶 ABRIR MI UBICACIÓN EN GOOGLE MAPS", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "HISTORIAL DE RUTA GPS (BREADCRUMBS)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }

        if (breadcrumbs.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Puntos GPS guardados para localización en caso de evacuación...",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(breadcrumbs.take(5)) { breadcrumb ->
                BreadcrumbItemCard(breadcrumb = breadcrumb)
            }
        }
    }
}

/**
 * Pestaña 4: Contactos Directos SOS
 */
@Composable
fun ContactsSOSTab(
    contacts: List<EmergencyContact>,
    batteryLevel: Int,
    onAddContact: (String, String, String) -> Unit,
    onDeleteContact: (EmergencyContact) -> Unit
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CONTACTOS SOS (${contacts.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Red directa de auxilio por llamada y SMS",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_contact_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "AGREGAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (contacts.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "No has agregado contactos de emergencia aún.",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Toca el botón + AGREGAR arriba para incluir números de familiares a quienes enviar SMS automático durante emergencias.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else {
            items(contacts) { contact ->
                FamilyContactCard(
                    contact = contact,
                    batteryLevel = batteryLevel,
                    onDelete = { onDeleteContact(contact) },
                    onOpenMap = {
                        val url = "https://www.google.com/maps/search/?api=1&query=4.6097,-74.0817"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    onSendSms = {
                        val msg = "SismoAlerta: Me encuentro SANO Y SALVO. Batería: $batteryLevel%."
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.phone}")).apply {
                            putExtra("sms_body", msg)
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, phone, rel ->
                onAddContact(name, phone, rel)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun NucleusMemberCard(member: NucleusMember) {
    val context = LocalContext.current
    val isEmergency = member.status.contains("NECESITO AYUDA") || member.status.contains("ATRAPADO")
    val displayName = com.example.data.repository.NucleusRepository.formatFirstAndLastName(member.name)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isEmergency) Color(0xFFFF1744) else Color(0xFF334155)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEmergency) Color(0xFFFF1744).copy(alpha = 0.2f)
                                else Color(0xFF10B981).copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isEmergency) Color(0xFFFF1744) else Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = "${member.relationship}${if (member.phone.isNotBlank()) " • " + member.phone else ""}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    color = if (isEmergency) Color(0xFFFF1744).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (isEmergency) Color(0xFFFF1744) else Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = member.status,
                            color = if (isEmergency) Color(0xFFFF1744) else Color(0xFF10B981),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Distance & Phone Battery Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📍 GPS: ${String.format(Locale.US, "%.6f, %.6f", member.latitude, member.longitude)} • 🔋 Batería: ${member.batteryLevel}%",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Direct Pedestrian Walking Navigation & Quick Contact Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        val latStr = String.format(Locale.US, "%.6f", member.latitude)
                        val lngStr = String.format(Locale.US, "%.6f", member.longitude)
                        val walkingUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latStr,$lngStr&travelmode=walking")
                        val intent = Intent(Intent.ACTION_VIEW, walkingUri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latStr,$lngStr&travelmode=walking")
                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF059669),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(imageVector = Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "🚶 IR A PIE", fontSize = 10.sp, fontWeight = FontWeight.Black)
                }

                if (member.phone.isNotBlank()) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${member.phone}"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "LLAMAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val msg = "SismoAlerta: Me pongo en contacto sobre el estado del Círculo de Alerta."
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${member.phone}")).apply {
                                putExtra("sms_body", msg)
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF334155),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "SMS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun FamilyContactCard(
    contact: EmergencyContact,
    batteryLevel: Int,
    onDelete: () -> Unit,
    onOpenMap: () -> Unit,
    onSendSms: () -> Unit
) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF3B82F6)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = contact.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = "${contact.relationship} • ${contact.phone}",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar de la red",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phone}"))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "LLAMAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onSendSms,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "SMS SOS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BreadcrumbItemCard(breadcrumb: BreadcrumbLocation) {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeStr = sdf.format(Date(breadcrumb.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (breadcrumb.isEmergencyPoint) Color(0xFFFF1744) else Color(0xFF00E676)
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Lat: ${String.format(Locale.US, "%.6f", breadcrumb.latitude)}, Lng: ${String.format(Locale.US, "%.6f", breadcrumb.longitude)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Precisión: ${breadcrumb.accuracy}m • Batería: ${breadcrumb.batteryLevel}%",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            Text(
                text = timeStr,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Familiar") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Nuevo Contacto de Emergencia", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre Completo") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono (+57 Colombia...)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_phone_input")
                )
                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("Parentesco (ej. Mamá, Hijo, Esposo)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_relationship_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        onConfirm(name, phone, relationship)
                    }
                },
                modifier = Modifier.testTag("confirm_add_contact_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

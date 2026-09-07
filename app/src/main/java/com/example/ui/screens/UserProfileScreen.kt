package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entity.EmergencyContact
import java.io.File
import com.example.ui.components.AppPullToRefreshBox

@Composable
fun UserProfileScreen(
    contacts: List<EmergencyContact>,
    userSharingCode: String = "SISMO-87A4",
    profileSyncVersion: Long = 0L,
    isRefreshing: Boolean = false,
    onRefreshProfile: () -> Unit = {},
    onSaveProfile: (Map<String, String>) -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sismo_user_profile_prefs", Context.MODE_PRIVATE) }

    val defaultGoogleEmail = remember {
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    var fullName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
    var documentId by remember { mutableStateOf(prefs.getString("user_doc", "") ?: "") }
    var userAge by remember { mutableStateOf(prefs.getString("user_age", "") ?: "") }
    var userOccupation by remember { mutableStateOf(prefs.getString("user_occupation", "") ?: "") }
    var userEmail by remember { 
        mutableStateOf(
            prefs.getString("user_email", null)?.takeIf { it.isNotBlank() } ?: defaultGoogleEmail
        ) 
    }
    var userPhone by remember { mutableStateOf(prefs.getString("user_phone", "") ?: "") }

    var bloodType by remember { mutableStateOf(prefs.getString("user_rh", "") ?: "") }
    var medicalConditions by remember { mutableStateOf(prefs.getString("user_conditions", "") ?: "") }
    var userEps by remember { mutableStateOf(prefs.getString("user_eps", "") ?: "") }

    var homeAddress by remember { mutableStateOf(prefs.getString("user_address", "") ?: "") }
    var safeEvacuationPoint by remember { mutableStateOf(prefs.getString("user_safe_point", "") ?: "") }

    var photoPath by remember { mutableStateOf(prefs.getString("user_photo_path", null)) }

    // Sincronizar automáticamente la UI si los datos son restaurados desde la nube
    androidx.compose.runtime.LaunchedEffect(profileSyncVersion) {
        fullName = prefs.getString("user_name", fullName) ?: fullName
        documentId = prefs.getString("user_doc", documentId) ?: documentId
        userAge = prefs.getString("user_age", userAge) ?: userAge
        userOccupation = prefs.getString("user_occupation", userOccupation) ?: userOccupation
        userEmail = prefs.getString("user_email", null)?.takeIf { it.isNotBlank() } ?: defaultGoogleEmail
        userPhone = prefs.getString("user_phone", userPhone) ?: userPhone
        bloodType = prefs.getString("user_rh", bloodType) ?: bloodType
        medicalConditions = prefs.getString("user_conditions", medicalConditions) ?: medicalConditions
        userEps = prefs.getString("user_eps", userEps) ?: userEps
        homeAddress = prefs.getString("user_address", homeAddress) ?: homeAddress
        safeEvacuationPoint = prefs.getString("user_safe_point", safeEvacuationPoint) ?: safeEvacuationPoint
    }

    // Launcher de foto con copia permanente a memoria interna del celular
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val photoFile = File(context.filesDir, "user_profile_photo.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    photoFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                photoPath = photoFile.absolutePath
                prefs.edit().putString("user_photo_path", photoFile.absolutePath).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var showEditDialog by remember { mutableStateOf(false) }

    // Si los datos basicos estan vacios, mostrar banner; si ya se llenaron, el banner desaparece
    val hasEmptyData = fullName.isBlank() || userPhone.isBlank()

    AppPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefreshProfile,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Direct Action Banner (Desaparece cuando la ficha esta configurada)
        if (hasEmptyData) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2563EB)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📝 COMPLETA TU FICHA DE EMERGENCIA",
                                color = Color(0xFFFF9100),
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ingresa tus datos personales, teléfono, EPS, dirección y ficha médica de rescate.",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { showEditDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "LLENAR DATOS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Main Emergency Identity Card with Persistent Photo
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2563EB).copy(alpha = 0.2f))
                                    .clickable { photoLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                val currentFile = photoPath?.let { File(it) }
                                if (currentFile != null && currentFile.exists()) {
                                    AsyncImage(
                                        model = currentFile,
                                        contentDescription = "Foto de Perfil",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(22.dp)
                                        .background(Color(0xFF2563EB), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Cambiar Foto",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = fullName.ifBlank { "Sin configurar" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "${documentId.ifBlank { "Sin documento" }}${if (userAge.isNotBlank()) " • $userAge" else ""}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = userOccupation.ifBlank { "Ficha de Usuario" },
                                    color = Color(0xFF60A5FA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("edit_profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar Ficha",
                                tint = Color(0xFF60A5FA)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Contact Phone & Email Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = userPhone.ifBlank { "Sin Teléfono" }, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = userEmail.ifBlank { "Sin Correo" }, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Ficha Médica de Rescate (Medical & Rescue ID)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFDC2626)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(imageVector = Icons.Default.LocalHospital, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FICHA MÉDICA Y DE EMERGENCIA",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                maxLines = 2
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "ACCESO SOS 24/7",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.18f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = "GRUPO SANGUÍNEO", fontSize = 9.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Black)
                                Text(text = bloodType.ifBlank { "No registrado" }, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            color = Color(0xFF2563EB).copy(alpha = 0.18f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = "SEGURO / EPS", fontSize = 9.sp, color = Color(0xFF60A5FA), fontWeight = FontWeight.Black)
                                Text(text = userEps.ifBlank { "No registrada" }, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(text = "ALERGIAS Y CONDICIONES MÉDICAS CRÍTICAS:", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = medicalConditions.ifBlank { "Sin alergias ni condiciones registradas" }, fontSize = 12.sp, color = Color.White, lineHeight = 16.sp)
                }
            }
        }

        // Evacuation & Safe Meeting Point Card (Fijado Permanente desde Mi Ubicacion GPS)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF10B981)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "DIRECCIÓN Y PUNTO SEGURO DE EVACUACIÓN", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "Dirección de Residencia", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            Text(text = homeAddress.ifBlank { "Sin dirección registrada" }, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "Punto de Encuentro Familiar Asignado (Guardado Permanente)", fontSize = 10.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            Text(
                                text = safeEvacuationPoint.ifBlank { "Toca 'Fijar mi ubicación actual' en la pestaña Alerta para sincronizar." },
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Personal Pairing Code Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2563EB)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "MI CÓDIGO PERSONAL DE EMPAREJAMIENTO", color = Color(0xFF60A5FA), fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = userSharingCode, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.5.sp)
                        Text(text = "Comparte este código para sincronizar círculos de auxilio.", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    }

                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "¡Únete a mi red de auxilio en SismoAlerta con mi Código Personal: $userSharingCode!")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Compartir mi Código de Emparejamiento"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("COMPARTIR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // QR Rescue Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.QrCode, contentDescription = null, tint = Color.Black, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "CARNET QR DE EMERGENCIAS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "Permite a socorristas o paramédicos escanear tus datos de salud al instante.", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }
            }
        }

        // Account & Switch Account Session Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CUENTA Y SESIÓN GOOGLE",
                                color = Color(0xFF60A5FA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = userEmail.ifBlank { "Sesión de Usuario" },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onSignOut,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color(0xFFFF5252)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "CAMBIAR CUENTA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

    if (showEditDialog) {
        var tempName by remember { mutableStateOf(fullName) }
        var tempDoc by remember { mutableStateOf(documentId) }
        var tempAge by remember { mutableStateOf(userAge) }
        var tempOcc by remember { mutableStateOf(userOccupation) }
        var tempPhone by remember { mutableStateOf(userPhone) }
        var tempEmail by remember { mutableStateOf(userEmail) }
        var tempRh by remember { mutableStateOf(bloodType) }
        var tempCond by remember { mutableStateOf(medicalConditions) }
        var tempEps by remember { mutableStateOf(userEps) }
        var tempAddr by remember { mutableStateOf(homeAddress) }
        var tempSafe by remember { mutableStateOf(safeEvacuationPoint) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Ficha Completa de Usuario y Evacuación", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(380.dp)
                ) {
                    item {
                        Text("DATOS PERSONALES:", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF2563EB))
                    }
                    item {
                        OutlinedTextField(value = tempName, onValueChange = { tempName = it }, label = { Text("Nombre Completo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = tempPhone, onValueChange = { tempPhone = it }, label = { Text("Teléfono Celular") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = tempEmail, onValueChange = { tempEmail = it }, label = { Text("Correo Electrónico") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = tempDoc, onValueChange = { tempDoc = it }, label = { Text("Documento de Identidad (ej. CC...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = tempAge, onValueChange = { tempAge = it }, label = { Text("Edad") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = tempOcc, onValueChange = { tempOcc = it }, label = { Text("Ocupación / Profesión") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        Text("FICHA MÉDICA DE RESCATE:", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFEF4444))
                    }
                    item {
                        Column {
                            Text("Grupo Sanguíneo y Factor RH:", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            val rhOptionsRow1 = listOf("O+", "O-", "A+", "A-")
                            val rhOptionsRow2 = listOf("B+", "B-", "AB+", "AB-")

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rhOptionsRow1.forEach { rh ->
                                    val isSelected = tempRh == rh
                                    Surface(
                                        onClick = { tempRh = rh },
                                        color = if (isSelected) Color(0xFFDC2626) else Color(0xFF1E293B),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = rh,
                                            color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rhOptionsRow2.forEach { rh ->
                                    val isSelected = tempRh == rh
                                    Surface(
                                        onClick = { tempRh = rh },
                                        color = if (isSelected) Color(0xFFDC2626) else Color(0xFF1E293B),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = rh,
                                            color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(value = tempEps, onValueChange = { tempEps = it }, label = { Text("Seguro Médico / EPS") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = tempCond, onValueChange = { tempCond = it }, label = { Text("Alergias o Condiciones Médicas") }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        Text("PLAN DE EVACUACIÓN FAMILIAR:", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981))
                    }
                    item {
                        OutlinedTextField(value = tempAddr, onValueChange = { tempAddr = it }, label = { Text("Dirección de Residencia") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(
                            value = tempSafe,
                            onValueChange = { tempSafe = it },
                            label = { Text("Punto de Encuentro Familiar (Guardado Permanente)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        fullName = tempName.trim()
                        documentId = tempDoc.trim()
                        userAge = tempAge.trim()
                        userOccupation = tempOcc.trim()
                        userPhone = tempPhone.trim()
                        userEmail = tempEmail.trim()
                        bloodType = tempRh.trim()
                        medicalConditions = tempCond.trim()
                        userEps = tempEps.trim()
                        homeAddress = tempAddr.trim()
                        safeEvacuationPoint = tempSafe.trim()

                        val profileMap = mapOf(
                            "user_name" to fullName,
                            "user_doc" to documentId,
                            "user_age" to userAge,
                            "user_occupation" to userOccupation,
                            "user_phone" to userPhone,
                            "user_email" to userEmail,
                            "user_rh" to bloodType,
                            "user_conditions" to medicalConditions,
                            "user_eps" to userEps,
                            "user_address" to homeAddress,
                            "user_safe_point" to safeEvacuationPoint
                        )

                        prefs.edit().apply {
                            profileMap.forEach { (k, v) -> putString(k, v) }
                            apply()
                        }

                        onSaveProfile(profileMap)

                        showEditDialog = false
                    }
                ) {
                    Text("Guardar Ficha Permanente")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

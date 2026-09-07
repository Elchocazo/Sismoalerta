package com.example.ui.screens

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthSuccess: (String, String, String) -> Unit,
    onSkipGuest: () -> Unit
) {
    var isRegistering by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val firebaseAuth = remember {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // App Branding Icon Header
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF1744).copy(alpha = 0.2f))
                    .border(2.dp, Color(0xFFFF1744), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color(0xFFFF1744),
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "SISMOALERTA COLOMBIA",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            Text(
                text = if (isRegistering) "Crea tu cuenta familiar de emergencia" else "Ingresa a tu red de alerta en tiempo real",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Full Name (Only for Registration)
                    AnimatedVisibility(visible = isRegistering) {
                        Column {
                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Nombre Completo") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color(0xFF64748B))
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_name_input")
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Email Input
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        label = { Text("Correo Electrónico") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = Color(0xFF64748B))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("Contraseña") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFF64748B))
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B)
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_input")
                    )

                    if (!errorMessage.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Main Auth Action Button
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Por favor ingresa tu correo y contraseña."
                                return@Button
                            }

                            isLoading = true
                            errorMessage = null

                            if (firebaseAuth != null) {
                                if (isRegistering) {
                                    firebaseAuth.createUserWithEmailAndPassword(email.trim(), password)
                                        .addOnSuccessListener { result ->
                                            isLoading = false
                                            val name = if (fullName.isNotBlank()) fullName else email.substringBefore("@")
                                            val userMail = result.user?.email ?: email.trim()
                                            onAuthSuccess(result.user?.uid ?: "", name, userMail)
                                        }
                                        .addOnFailureListener { e ->
                                            isLoading = false
                                            errorMessage = e.localizedMessage ?: "Error al registrar usuario."
                                        }
                                } else {
                                    firebaseAuth.signInWithEmailAndPassword(email.trim(), password)
                                        .addOnSuccessListener { result ->
                                            isLoading = false
                                            val name = result.user?.displayName ?: email.substringBefore("@")
                                            val userMail = result.user?.email ?: email.trim()
                                            onAuthSuccess(result.user?.uid ?: "", name, userMail)
                                        }
                                        .addOnFailureListener { e ->
                                            isLoading = false
                                            errorMessage = e.localizedMessage ?: "Error al iniciar sesión."
                                        }
                                }
                            } else {
                                isLoading = false
                                onAuthSuccess("LOCAL_USER", if (fullName.isNotBlank()) fullName else email.substringBefore("@"), email.trim())
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_submit_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isRegistering) "CREAR CUENTA EN FIREBASE" else "INICIAR SESIÓN",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFF334155)))
                        Text(
                            text = "  O INICIA CON  ",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFF334155)))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Google Sign-In with Account Selection
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

                    val googleAccountPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        isLoading = false
                        if (result.resultCode == Activity.RESULT_OK) {
                            val selectedEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                            if (!selectedEmail.isNullOrBlank()) {
                                val rawName = selectedEmail.substringBefore("@").replace(".", " ").replace("_", " ")
                                val capitalized = rawName.split(" ")
                                    .filter { it.isNotBlank() }
                                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                                val displayName = com.example.data.repository.NucleusRepository.formatFirstAndLastName(capitalized)
                                val userId = "google_" + selectedEmail.lowercase().replace("[^a-zA-Z0-9]".toRegex(), "_")
                                onAuthSuccess(userId, displayName, selectedEmail)
                            }
                        } else {
                            errorMessage = null
                        }
                    }

                    Button(
                        onClick = {
                            isLoading = true
                            errorMessage = null

                            try {
                                val intent = AccountManager.newChooseAccountIntent(
                                    null,
                                    null,
                                    arrayOf("com.google"),
                                    null,
                                    null,
                                    null,
                                    null
                                )
                                googleAccountPickerLauncher.launch(intent)
                            } catch (e: Exception) {
                                coroutineScope.launch {
                                    try {
                                        val credentialManager = androidx.credentials.CredentialManager.create(context)
                                        val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId("734106072169-d31b967992fdb1ae9ddef8.apps.googleusercontent.com")
                                            .setAutoSelectEnabled(false)
                                            .build()

                                        val request = androidx.credentials.GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()

                                        val result = credentialManager.getCredential(context = context, request = request)
                                        val credential = result.credential

                                        if (credential is com.google.android.libraries.identity.googleid.GoogleIdTokenCredential) {
                                            val googleIdToken = credential.idToken
                                            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                                            firebaseAuth?.signInWithCredential(firebaseCredential)
                                                ?.addOnSuccessListener { authResult ->
                                                    isLoading = false
                                                    val user = authResult.user
                                                    val userEmail = user?.email ?: ""
                                                    onAuthSuccess(user?.uid ?: "", user?.displayName ?: userEmail.substringBefore("@"), userEmail)
                                                }
                                                ?.addOnFailureListener { err ->
                                                    isLoading = false
                                                    errorMessage = err.localizedMessage ?: "Error de autenticación con Google."
                                                }
                                        } else {
                                            isLoading = false
                                        }
                                    } catch (ce: Exception) {
                                        isLoading = false
                                        if (ce !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                                            errorMessage = "No se pudo seleccionar cuenta de Google."
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_google_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "G",
                                color = Color(0xFF4285F4),
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Continuar con Google",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Toggle Register / Login mode
                    TextButton(
                        onClick = {
                            isRegistering = !isRegistering
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isRegistering) "¿Ya tienes cuenta? Inicia Sesión" else "¿No tienes cuenta? Registrate gratis",
                            color = Color(0xFF60A5FA),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Guest Mode Action
            TextButton(onClick = onSkipGuest) {
                Text(
                    text = "Continuar sin iniciar sesión (Modo Directo)",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }
    }
}

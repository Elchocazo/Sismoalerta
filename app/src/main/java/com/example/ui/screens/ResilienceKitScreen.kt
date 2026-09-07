package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppPullToRefreshBox

data class BackpackItem(
    val id: String,
    val title: String,
    val category: String,
    val emojiIcon: String,
    val clayBgColor: Color,
    val clayAccentColor: Color
)

@Composable
fun ResilienceKitScreen(
    isRefreshing: Boolean = false,
    onRefreshKit: () -> Unit = {}
) {
    val context = LocalContext.current

    val backpackItems = remember {
        listOf(
            BackpackItem("1", "Agua Embotellada (2L/persona)", "Hidratación", "💧", Color(0xFFE3F2FD), Color(0xFF1E88E5)),
            BackpackItem("2", "Enlatados y No Perecederos", "Alimentos", "🥫", Color(0xFFFFF3E0), Color(0xFFFB8C00)),
            BackpackItem("3", "Botiquín Primeros Auxilios", "Salud", "🩹", Color(0xFFFFEBEE), Color(0xFFE53935)),
            BackpackItem("4", "Linterna LED + Baterías", "Iluminación", "🔦", Color(0xFFFFFDE7), Color(0xFFFDD835)),
            BackpackItem("5", "Radio AM/FM Portátil", "Comunicación", "📻", Color(0xFFF3E5F5), Color(0xFF8E24AA)),
            BackpackItem("6", "Silbato de Rescate Potente", "Señalización", "🔊", Color(0xFFE8F5E9), Color(0xFF43A047)),
            BackpackItem("7", "Copia Documentos + USB", "Identidad", "📄", Color(0xFFE0F7FA), Color(0xFF00ACC1)),
            BackpackItem("8", "Powerbank (Batería 10,000mAh)", "Energía", "🔋", Color(0xFFECEFF1), Color(0xFF546E7A)),
            BackpackItem("9", "Manta Térmica Impermeable", "Protección", "🧥", Color(0xFFFBE9E7), Color(0xFFD84315)),
            BackpackItem("10", "Llaves Repuesto y Efectivo", "Acceso", "🔑", Color(0xFFFFF8E1), Color(0xFFFFB300)),
            BackpackItem("11", "Navaja Multiusos Suiza", "Herramienta", "🔪", Color(0xFFF1F8E9), Color(0xFF7CB342)),
            BackpackItem("12", "Mascarillas N95 y Gel", "Higiene", "😷", Color(0xFFE8EAF6), Color(0xFF3F51B5))
        )
    }

    val checklistState = remember {
        mutableStateMapOf(
            "1" to true, "2" to true, "3" to true, "4" to true,
            "5" to false, "6" to true, "7" to false, "8" to true,
            "9" to false, "10" to true, "11" to true, "12" to false
        )
    }

    val readyCount = checklistState.values.count { it }
    val totalCount = backpackItems.size
    val progressFraction = readyCount.toFloat() / totalCount.toFloat()

    AppPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefreshKit,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
        // 🇨🇴 COLOMBIA DIRECT EMERGENCY NUMBERS (Image 2 fix)
        item {
            Text(
                text = "LÍNEAS DIRECTAS DE EMERGENCIA COLOMBIA 🇨🇴",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColombiaEmergencyCard(
                        categoryTitle = "EMERGENCIAS",
                        number = "123",
                        name = "Policía Nacional",
                        color = Color(0xFFFF1744),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dial_colombia_123"),
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:123")))
                        }
                    )

                    ColombiaEmergencyCard(
                        categoryTitle = "AMBULANCIAS",
                        number = "132",
                        name = "Cruz Roja",
                        color = Color(0xFFFF9100),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dial_colombia_132"),
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:132")))
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColombiaEmergencyCard(
                        categoryTitle = "RESCATE",
                        number = "144",
                        name = "Defensa Civil",
                        color = Color(0xFF00E676),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dial_colombia_144"),
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:144")))
                        }
                    )

                    ColombiaEmergencyCard(
                        categoryTitle = "INCENDIOS",
                        number = "119",
                        name = "Bomberos",
                        color = Color(0xFF29B6F6),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dial_colombia_119"),
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:119")))
                        }
                    )
                }
            }
        }

        // 🎒 CLAYMORPHISM MOCHILA DE EMERGENCIA HEADER & PROGRESS
        item {
            ClayCardContainer(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Work,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mochila de Emergencia (72h)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF102A43)
                                )
                                Text(
                                    text = "Equipamiento de supervivencia vital",
                                    fontSize = 11.sp,
                                    color = Color(0xFF486581)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            color = if (readyCount == totalCount) Color(0xFF00E676) else Color(0xFFFF9100),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "$readyCount/$totalCount Listos",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = Color(0xFF00E676),
                        trackColor = Color(0xFFB0BEC5).copy(alpha = 0.4f),
                    )
                }
            }
        }

        // 🎒 CLAYMORPHISM GRID LISTING OF ITEMS WITH IMAGES & EMOJIS
        item {
            Text(
                text = "LISTADO DE ARTÍCULOS VITALES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                backpackItems.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { item ->
                            val isChecked = checklistState[item.id] == true

                            ClaymorphicItemTile(
                                item = item,
                                isChecked = isChecked,
                                modifier = Modifier.weight(1f),
                                onToggle = {
                                    checklistState[item.id] = !isChecked
                                }
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Protocol Guide Card
        item {
            ClayCardContainer(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MedicalInformation,
                            contentDescription = null,
                            tint = Color(0xFF00C853),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PROTOCOLO DE SEGURIDAD Y EVACUACIÓN",
                            color = Color(0xFF102A43),
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Mantén la mochila en un lugar visible y cerca de la salida principal.\n" +
                               "• Revisa caducidades de alimentos y baterías cada 6 meses.\n" +
                               "• Durante el sismo: Agáchate, Cúbrete y Sujétate hasta que cese el movimiento.",
                        color = Color(0xFF334E68),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}
}

/**
 * Claymorphic Inflated 3D Container Component
 */
@Composable
fun ClayCardContainer(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFF0F4F8),
    content: @Composable () -> Unit
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 8.dp,
        modifier = modifier.border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
    ) {
        content()
    }
}

/**
 * Claymorphic Interactive Item Tile with 3D Tactile Styling
 */
@Composable
fun ClaymorphicItemTile(
    item: BackpackItem,
    isChecked: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        color = if (isChecked) item.clayBgColor else Color(0xFFF7FAFC),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = if (isChecked) 6.dp else 2.dp,
        modifier = modifier
            .testTag("backpack_item_${item.id}")
            .border(
                width = if (isChecked) 2.dp else 1.dp,
                color = if (isChecked) item.clayAccentColor else Color(0xFFCBD5E0),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 3D Emoji Icon Badge
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(item.clayAccentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = item.emojiIcon, fontSize = 22.sp)
                }

                // Check indicator
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isChecked) item.clayAccentColor else Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isChecked) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Listo",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isChecked) Color(0xFF102A43) else Color(0xFF486581),
                lineHeight = 15.sp
            )

            Text(
                text = item.category,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = item.clayAccentColor
            )
        }
    }
}

/**
 * Official Colombia Emergency Contact Cards (Centered 3-tier layout: Category, Number, Name)
 */
@Composable
fun ColombiaEmergencyCard(
    categoryTitle: String,
    number: String,
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = categoryTitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color.copy(alpha = 0.9f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = number,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                color = color,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = name,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

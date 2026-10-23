package com.tiktokstudio.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BDB), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E0FF), onPrimaryContainer = Color(0xFF1B0F6B),
    secondary = Color(0xFFE6357A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCE8), onSecondaryContainer = Color(0xFF5A0A2B),
    tertiary = Color(0xFF00A38C), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC2F5EB), onTertiaryContainer = Color(0xFF00382F),
    background = Color(0xFFF6F5FB), onBackground = Color(0xFF1B1A24),
    surface = Color.White, onSurface = Color(0xFF1B1A24),
    surfaceVariant = Color(0xFFECE9F6), onSurfaceVariant = Color(0xFF5A5870),
    outline = Color(0xFF8F8CA6), outlineVariant = Color(0xFFDAD7E8),
    error = Color(0xFFD32F4A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD9), onErrorContainer = Color(0xFF410008),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF9F8FD),
    surfaceContainer = Color(0xFFF1EFF9), surfaceContainerHigh = Color(0xFFEBE8F5), surfaceContainerHighest = Color(0xFFE5E2F1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7ACFF), onPrimary = Color(0xFF2A1A8C),
    primaryContainer = Color(0xFF332A78), onPrimaryContainer = Color(0xFFE5E0FF),
    secondary = Color(0xFFFF8AB3), onSecondary = Color(0xFF5A0A2B),
    secondaryContainer = Color(0xFF6B1B3F), onSecondaryContainer = Color(0xFFFFDCE8),
    tertiary = Color(0xFF5EEAD4), onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF0F4D43), onTertiaryContainer = Color(0xFFC2F5EB),
    background = Color(0xFF0F0E17), onBackground = Color(0xFFE8E6F5),
    surface = Color(0xFF1A1928), onSurface = Color(0xFFE8E6F5),
    surfaceVariant = Color(0xFF2A2840), onSurfaceVariant = Color(0xFFB9B6D0),
    outline = Color(0xFF7D7A94), outlineVariant = Color(0xFF3A3852),
    error = Color(0xFFFF8A9B), onError = Color(0xFF5C1020),
    errorContainer = Color(0xFF5C1020), onErrorContainer = Color(0xFFFFD9DC),
    surfaceContainerLowest = Color(0xFF0B0A12), surfaceContainerLow = Color(0xFF16152A),
    surfaceContainer = Color(0xFF1E1D30), surfaceContainerHigh = Color(0xFF282740), surfaceContainerHighest = Color(0xFF33314D),
)

private val baseType = Typography()
private val AppTypography = Typography(
    headlineMedium = baseType.headlineMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 38.sp),
    titleLarge = baseType.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = baseType.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 26.sp),
    titleSmall = baseType.titleSmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = baseType.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = baseType.bodyMedium.copy(fontSize = 15.sp, lineHeight = 23.sp),
    bodySmall = baseType.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = baseType.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography, shapes = AppShapes, content = content,
    )
}

/** كارت أبيض بحواف دائرية وظل خفيف (بدل لون الكارت الافتراضي). */
@Composable
fun AppCard(modifier: Modifier = Modifier, border: BorderStroke? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = border, content = content,
    )
}

/** قسم بعنوان وأيقونة داخل كارت. */
@Composable
fun Panel(title: String, icon: ImageVector? = null, content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) Box(
                    Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = content, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChecked)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun ActionCard(icon: ImageVector, title: String, subtitle: String, accent: Color, badge: Int = 0, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(28.dp), tint = accent)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (badge > 0) Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary), contentAlignment = Alignment.Center) {
                Text("$badge", color = MaterialTheme.colorScheme.onSecondary, style = MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.Default.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

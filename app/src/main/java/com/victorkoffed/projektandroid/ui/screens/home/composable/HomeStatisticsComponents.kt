package com.victorkoffed.projektandroid.ui.screens.home.composable

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.victorkoffed.projektandroid.R
import com.victorkoffed.projektandroid.domain.model.BleConnectionState
import com.victorkoffed.projektandroid.ui.viewmodel.home.RecentBrewItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// --- Hjälpfunktioner ---

/**
 * Hjälpfunktion för att formatera tiden som gått sedan senaste bryggningen.
 *
 * @param lastBrewTime Tidpunkten för den senaste bryggningen.
 * @return Formaterad sträng (t.ex. "5 min", "3 h", "2 w").
 */
fun formatTimeSince(lastBrewTime: Date?): String? {
    if (lastBrewTime == null) return null

    val now = System.currentTimeMillis()
    val diffMillis = now - lastBrewTime.time

    val diffSeconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis)
    val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)
    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

    // Returnerar den största relevanta tidsenheten
    return when {
        diffSeconds < 60 -> "< 1 min"
        diffMinutes < 60 -> "$diffMinutes min"
        diffHours < 24 -> "$diffHours h"
        diffDays < 7 -> "$diffDays d"
        else -> {
            val weeks = diffDays / 7
            if (weeks >= 52) {
                val years = weeks / 52
                "$years y"
            } else {
                "$weeks w"
            }
        }
    }
}

// --- Komponenter ---

/**
 * Rutnät för infokorten som visar nyckelstatistik och status.
 *
 * @param totalBrews Totalt antal genomförda bryggningar.
 * @param beansExplored Totalt antal bönor i databasen.
 * @param availableWeight Total kvarvarande vikt av aktiva bönor.
 * @param imageUrl URL till den slumpmässiga kaffebilden.
 * @param imageLoading Anger om bildladdning pågår.
 * @param imageError Felmeddelande vid bildladdning.
 * @param timeSinceLastCoffee Formaterad tid sedan senaste bryggning.
 * @param scaleConnectionState Aktuell anslutningsstatus till vågen.
 * @param rememberedScaleAddress Adressen till den ihågkomna vågen (om någon).
 * @param onReloadImage Callback för att ladda om bilden.
 * @param onRetryScaleConnect Callback för att försöka återansluta till vågen.
 */
@Composable
fun InfoGrid(
    totalBrews: Int,
    beansExplored: Int,
    availableWeight: Double,
    imageUrl: String?,
    imageLoading: Boolean,
    imageError: String?,
    timeSinceLastCoffee: String,
    scaleConnectionState: BleConnectionState,
    rememberedScaleAddress: String?,
    onReloadImage: () -> Unit,
    onRetryScaleConnect: () -> Unit
) {
    val firstRowHeight = 160.dp
    val otherRowHeight = 100.dp

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Första raden: Bild och vågens status
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Kort 1: Slumpmässig bild
            InfoCard(modifier = Modifier.weight(1f).height(firstRowHeight)) {
                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    when {
                        imageLoading -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        imageError != null -> {
                            // Visa felikon och en knapp för att ladda om
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Warning, "Error loading image", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(onClick = onReloadImage, modifier= Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Refresh, "Reload")
                                }
                            }
                        }
                        imageUrl != null -> {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = "Random coffee",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clickable { onReloadImage() }
                            )
                        }
                        else -> Text("Image", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Kort 2: Vågens status
            ScaleStatusCard(
                connectionState = scaleConnectionState,
                rememberedAddress = rememberedScaleAddress,
                onRetryConnect = onRetryScaleConnect,
                modifier = Modifier.weight(1f).height(firstRowHeight)
            )
        }

        // Andra raden: Brews och Tid sedan kaffe
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(title = totalBrews.toString(), subtitle = "Brews", modifier = Modifier.weight(1f).height(otherRowHeight))
            InfoCard(title = timeSinceLastCoffee, subtitle = "Since last coffee", modifier = Modifier.weight(1f).height(otherRowHeight))
        }

        // Tredje raden: Beans explored och Beans available
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(title = beansExplored.toString(), subtitle = "Beans explored", modifier = Modifier.weight(1f).height(otherRowHeight))
            InfoCard(title = "%.0f g".format(availableWeight), subtitle = "Beans available", modifier = Modifier.weight(1f).height(otherRowHeight))
        }
    }
}

/**
 * Visar vågens anslutningsstatus med dynamiska ikoner och färger.
 *
 * @param connectionState Aktuell Bluetooth-anslutningsstatus.
 * @param rememberedAddress Adressen till den ihågkomna vågen.
 * @param onRetryConnect Callback för att försöka återansluta.
 * @param modifier Modifier för layout.
 */
@Composable
fun ScaleStatusCard(
    connectionState: BleConnectionState,
    rememberedAddress: String?,
    onRetryConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon: @Composable () -> Unit
    val title: String
    val subtitle: String
    val iconColor: Color
    val titleColor: Color
    val isClickableForRetry = connectionState is BleConnectionState.Disconnected || connectionState is BleConnectionState.Error

    when (connectionState) {
        is BleConnectionState.Connected -> {
            val batteryLevel = connectionState.batteryPercent

            icon = { Icon(Icons.Default.BluetoothConnected, contentDescription = "Connected") }
            title = connectionState.deviceName.takeIf { it.isNotEmpty() } ?: "Connected"
            subtitle = if (batteryLevel != null) {
                "Battery: $batteryLevel%"
            } else {
                "Scale Connected"
            }
            iconColor = MaterialTheme.colorScheme.primary
            titleColor = MaterialTheme.colorScheme.primary
        }
        is BleConnectionState.Connecting -> {
            icon = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
            title = "Connecting..."
            subtitle = "Please wait"
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
            titleColor = MaterialTheme.colorScheme.onSurface
        }
        is BleConnectionState.Error -> {
            icon = { Icon(Icons.Default.BluetoothDisabled, contentDescription = "Error", tint = MaterialTheme.colorScheme.error) }
            title = "Connection Error"
            subtitle = "Tap to retry"
            iconColor = MaterialTheme.colorScheme.error
            titleColor = MaterialTheme.colorScheme.error
        }
        BleConnectionState.Disconnected -> {
            icon = { Icon(Icons.Default.BluetoothDisabled, contentDescription = "Disconnected") }
            title = "Scale disconnected"
            subtitle = if (rememberedAddress == null) {
                "Use Menu (☰) to connect"
            } else {
                "Tap to retry connect"
            }
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant
            titleColor = MaterialTheme.colorScheme.onSurface
        }
    }

    Card(
        modifier = modifier.clickable(
            enabled = isClickableForRetry,
            onClick = onRetryConnect
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides iconColor) {
                    icon()
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = titleColor)
            // För instruktionstexten, visa även menyikonen
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (connectionState is BleConnectionState.Disconnected && rememberedAddress == null) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Generisk baskomponent för informationskort.
 *
 * @param modifier Modifier för layout.
 * @param title Huvudtiteln/värdet.
 * @param subtitle Undertiteln/etiketten.
 * @param content Valfri Composable för att ersätta standardinnehållet (t.ex. för bilder/laddning).
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (content != null) { content() }
            else if (title != null) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
            }
        }
    }
}

/**
 * Placeholder-text som visas när inga bryggningar finns sparade.
 * Gör hela ytan klickbar för att starta bryggning.
 *
 * @param modifier Modifier för layout.
 * @param onStartBrewClick Callback för att starta bryggningsprocessen.
 */
@Composable
fun NoBrewsTextWithIcon(
    modifier: Modifier = Modifier,
    onStartBrewClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onStartBrewClick)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "No brews saved yet, tap ",
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            )
            Surface(
                modifier = Modifier.size(32.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Text(
            "to create one.",
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Kortkomponent för att visa en nyligen genomförd bryggning.
 *
 * @param brewItem Daten för den senaste bryggningen.
 * @param onClick Callback när kortet klickas (navigera till detaljvy).
 */
@Composable
fun RecentBrewCard(
    brewItem: RecentBrewItem,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${dateFormat.format(brewItem.brew.startedAt)} ${timeFormat.format(brewItem.brew.startedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(brewItem.beanName ?: "Unknown bean", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))

            // Bild eller standardikon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (brewItem.brew.imageUri != null) {
                    AsyncImage(
                        model = brewItem.brew.imageUri,
                        contentDescription = "Brew photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Visar en standardikon om ingen bild finns (R.mipmap.ic_launcher_foreground)
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "Brew Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
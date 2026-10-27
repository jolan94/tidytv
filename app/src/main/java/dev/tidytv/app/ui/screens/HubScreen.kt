package dev.tidytv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import dev.tidytv.app.TvSummary
import dev.tidytv.app.ui.Check
import dev.tidytv.app.ui.HeroMascot
import dev.tidytv.app.ui.Mood
import dev.tidytv.app.ui.ScreenFrame
import dev.tidytv.app.ui.TextLink
import dev.tidytv.app.ui.Tidy
import dev.tidytv.app.ui.TvCard

private class HubItem(val icon: ImageVector, val title: String, val bg: Color, val fg: Color, val badge: String? = null, val onClick: () -> Unit)

/** The My TV hub: which TV and a tile per feature. How tidy it is sits on the Tidy tile. */
@Composable
fun HubScreen(
    tv: TvSummary?,
    appsOff: Int,
    fireTv: Boolean,
    onTidy: () -> Unit,
    onRemote: () -> Unit,
    onFiles: () -> Unit,
    onHomeScreen: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
    onCheck: () -> Unit = {},
    onSpace: () -> Unit = {},
    onAds: () -> Unit = {},
    onApps: () -> Unit = {},
    facts: dev.tidytv.app.HubFacts = dev.tidytv.app.HubFacts(),
) {
    val items = buildList {
        add(HubItem(Icons.Rounded.CleaningServices, "Tidy up", Tidy.Blue, Color.White, if (appsOff > 0) "$appsOff off" else null, onTidy))
        add(HubItem(Icons.Rounded.SettingsRemote, "Remote", Tidy.Purple, Color.White, onClick = onRemote))
        add(HubItem(Icons.Rounded.Upload, "Send files", Tidy.Orange, Color.White, onClick = onFiles))
        if (!fireTv) add(HubItem(Icons.Rounded.Home, "Home screen", Tidy.Green, Color.White, onClick = onHomeScreen))
        add(HubItem(Icons.Rounded.Apps, "Apps", Tidy.Blue, Color.White, onClick = onApps))
        add(HubItem(Icons.Rounded.TaskAlt, "Check my TV", Tidy.Yellow, Tidy.Ink, onClick = onCheck))
        add(HubItem(Icons.Rounded.Storage, "Free up space", Tidy.Green, Color.White, onClick = onSpace))
        add(HubItem(Icons.Rounded.Shield, "Block ads", Tidy.Purple, Color.White, onClick = onAds))
        add(HubItem(Icons.Rounded.Restore, "Restore", Tidy.Grey, Tidy.Ink, onClick = onRestore))
    }
    ScreenFrame(top = { Row(Modifier.fillMaxWidth()) { TextLink("Change TV", onBack, icon = Icons.AutoMirrored.Rounded.ArrowBack) } }) {
        HeroMascot(mood = Mood.HAPPY, height = 84.dp)
        if (tv != null) TvCard(tv.title, tv.model)
        if (facts.any) dev.tidytv.app.ui.ChipRow(center = true) {
            facts.freeStorageMb?.let { dev.tidytv.app.ui.InfoChip(Icons.Rounded.Storage, "${gbOrMb(it)} free", color = Tidy.BlueSoft, textColor = Tidy.Ink) }
            facts.freeRamMb?.let { dev.tidytv.app.ui.InfoChip(Icons.Rounded.Memory, "${gbOrMb(it)} RAM free", color = Tidy.GreenSoft, textColor = Tidy.GreenInk) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (pair.size == 2) {
                        HubTile(pair[0]); HubTile(pair[1])
                    } else {
                        HubTile(pair[0]) // lone last tile spans both columns
                    }
                }
            }
        }
        val actions = dev.tidytv.app.ui.LocalAppActions.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextLink("Rate TidyTV", { actions.rate() }, color = Tidy.Muted, small = true)
        }
    }
}

@Composable
private fun RowScope.HubTile(item: HubItem) {
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .clip(RoundedCornerShape(22.dp)).background(item.bg)
            .semantics { contentDescription = if (item.badge != null) "${item.title}, ${item.badge}" else item.title }
            .clickable(role = Role.Button, onClick = item.onClick)
            .heightIn(min = 112.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(item.icon, null, Modifier.size(36.dp), tint = item.fg)
        Spacer(Modifier.weight(1f, fill = false))
        Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = item.fg)
        if (item.badge != null) Row(
            Modifier.clip(RoundedCornerShape(99.dp)).background(Color.White).padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Check(Tidy.Green, 16.dp)
            Spacer(Modifier.width(2.dp))
            Text(item.badge, style = MaterialTheme.typography.labelMedium, color = Tidy.GreenInk, maxLines = 1)
        }
    }
}

private fun gbOrMb(mb: Int) = if (mb >= 1024) String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0) else "$mb MB"

package ir.rokoi.rmho.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ir.rokoi.rmho.data.Adventurer
import ir.rokoi.rmho.data.DataBundle

private val ClassFilters = listOf(
    "" to "All",
    "ASSAULT" to "Assault",
    "DISRUPTOR" to "Disruptor",
    "SUPPORT" to "Support"
)

@Composable
fun AdventurersScreen(data: DataBundle, onOpen: (String) -> Unit) {
    var filter by remember { mutableStateOf("") }
    val list = data.adventurers.filter { filter.isEmpty() || it.classType == filter }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Adventurers",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            items(ClassFilters) { (value, label) ->
                FilterChip(
                    selected = filter == value,
                    onClick = { filter = value },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            items(list, key = { it.id }) { adv ->
                AdventurerCard(adv) { onOpen(adv.id) }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun AdventurerCard(adv: Adventurer, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GoldPortrait(adv.imageUrl, adv.nameEn, 64.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(adv.nameEn, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${classLabel(adv.classType)} • ${weaponLabel(adv.weapon)} • ${elementLabel(adv.element)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AdventurerDetailScreen(data: DataBundle, id: String, onBack: () -> Unit) {
    val adv = data.adventurers.firstOrNull { it.id == id }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                adv?.nameEn ?: "Adventurer",
                style = MaterialTheme.typography.titleLarge
            )
        }
        if (adv == null) {
            Text(
                "Not found.",
                modifier = Modifier.padding(20.dp)
            )
            return
        }
        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (adv.imageUrl.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        GoldPortrait(adv.imageUrl, adv.nameEn, 184.dp, glow = true)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (adv.rarity.isNotEmpty()) {
                        SuggestionChip(onClick = {}, label = { Text(adv.rarity) })
                    }
                    SuggestionChip(onClick = {}, label = { Text(classLabel(adv.classType)) })
                    SuggestionChip(onClick = {}, label = { Text(weaponLabel(adv.weapon)) })
                    SuggestionChip(onClick = {}, label = { Text(elementLabel(adv.element)) })
                }
            }
            if (adv.attack != null && adv.defense != null) {
                item {
                    Text(
                        "Max stats — Attack ${adv.attack} • Defense ${adv.defense}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            if (adv.description.isNotEmpty()) {
                item { Text(adv.description, style = MaterialTheme.typography.bodyLarge) }
            }
            if (adv.descriptionFa.isNotEmpty()) {
                item { FaSection(adv.descriptionFa) }
            }
            if (adv.skills.isNotEmpty()) {
                item {
                    Text("Skills", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    adv.skills.forEach { s ->
                        Text(
                            if (s.description.isEmpty()) "• ${s.nameEn}"
                            else "• ${s.nameEn} — ${s.description}"
                        )
                    }
                }
            }
            adv.ultimate?.let { u ->
                item {
                    Text("Ultimate: ${u.nameEn}", style = MaterialTheme.typography.titleMedium)
                    if (u.description.isNotEmpty()) Text(u.description)
                }
            }
            if (adv.typeUltimates.isNotEmpty()) {
                item {
                    Text("Ultimate by Type", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your Type (class) decides your Ultimate — your weapon never does.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    adv.typeUltimates.forEach { u ->
                        Text(
                            "${classLabel(u.type)}: ${u.nameEn}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (u.effect.isNotEmpty()) Text(u.effect)
                        val meta = listOfNotNull(
                            u.cooldownSec?.let { cd ->
                                "Cooldown ${if (cd % 1.0 == 0.0) cd.toInt().toString() else cd.toString()}s"
                            },
                            if (u.initialEnergy != null && u.maxEnergy != null) {
                                "Energy ${u.initialEnergy}/${u.maxEnergy}"
                            } else null
                        )
                        if (meta.isNotEmpty()) {
                            Text(
                                meta.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            if (adv.signatureTraits.isNotEmpty()) {
                item {
                    Text("Signature traits", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    adv.signatureTraits.forEach { t -> Text("• $t") }
                }
            }
            if (adv.weaponSystemNote.isNotEmpty() || adv.weaponSystemPoints.isNotEmpty()) {
                item {
                    Text("Weapon system", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    if (adv.weaponSystemNote.isNotEmpty()) Text(adv.weaponSystemNote)
                    adv.weaponSystemPoints.forEach { p -> Text("• $p") }
                }
            }
            if (adv.strengths.isNotEmpty()) {
                item {
                    Text("Strengths", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    adv.strengths.forEach { s -> Text("• $s") }
                }
            }
            if (adv.weaknesses.isNotEmpty()) {
                item {
                    Text("Weaknesses", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    adv.weaknesses.forEach { s -> Text("• $s") }
                }
            }
            if (adv.insightSummary.isNotEmpty()) {
                item {
                    Text("Insight system", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(adv.insightSummary)
                    if (adv.insightHighlights.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Top Insight per weapon", style = MaterialTheme.typography.titleSmall)
                        adv.insightHighlights.forEach { h -> Text("• $h") }
                    }
                }
            }
            if (adv.insightSummaryFa.isNotEmpty()) {
                item { FaSection(adv.insightSummaryFa, title = "سیستم Insight") }
            }
            if (adv.bestClassNote.isNotEmpty()) {
                item {
                    Text("Best class", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(adv.bestClassNote)
                }
            }
            if (adv.bestStartersNote.isNotEmpty()) {
                item {
                    Text("Best starter weapons", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(adv.bestStartersNote)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

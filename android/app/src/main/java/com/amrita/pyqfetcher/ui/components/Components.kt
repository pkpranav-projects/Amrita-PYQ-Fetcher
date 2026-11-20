package com.amrita.pyqfetcher.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amrita.pyqfetcher.data.db.PaperRow
import com.amrita.pyqfetcher.data.db.SubjectSummary
import com.amrita.pyqfetcher.domain.PaperKind
import com.amrita.pyqfetcher.domain.StudyStatus
import com.amrita.pyqfetcher.ui.PaperActivity
import com.amrita.pyqfetcher.ui.theme.Amber
import com.amrita.pyqfetcher.ui.theme.CodeStyle
import com.amrita.pyqfetcher.ui.theme.Emerald

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        action?.invoke()
    }
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A frosted-glass list row. */
@Composable
fun RowCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    com.amrita.pyqfetcher.ui.theme.GlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        selected = selected,
        onClick = onClick,
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(label(item)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
fun StatusMenu(status: StudyStatus, onChange: (StudyStatus) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
            Text(status.label, style = MaterialTheme.typography.labelMedium)
            Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            StudyStatus.entries.forEach {
                DropdownMenuItem(text = { Text(it.label) }, onClick = { open = false; onChange(it) })
            }
        }
    }
}

/** Compact subject row: code, name, and how much of it is saved. */
@Composable
fun SubjectItem(s: SubjectSummary, onClick: () -> Unit) {
    RowCard(onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.code, style = CodeStyle, color = MaterialTheme.colorScheme.primary)
                    if (s.legacyList.isNotEmpty()) {
                        Text(
                            "also ${s.legacyList.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(s.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { if (s.paperCount == 0) 0f else s.savedCount.toFloat() / s.paperCount },
                        modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
                        color = Emerald,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${s.savedCount}/${s.paperCount} saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun kindColor(kind: PaperKind) = when (kind) {
    PaperKind.END_SEM -> Emerald
    PaperKind.MID_TERM -> Amber
    PaperKind.ASSESSMENT -> Color(0xFF7DD3FC)
}

/**
 * One question paper. Tap opens it (downloading first if needed); long-press selects.
 * The trailing slot always shows the paper's download state.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaperItem(
    row: PaperRow,
    demoMode: Boolean,
    activity: PaperActivity?,
    selected: Boolean = false,
    showSubject: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
) {
    val kind = PaperKind.from(row.paper.kind)
    val saved = row.isSaved && (row.dlDemo != true || demoMode)
    val colors = MaterialTheme.colorScheme
    val glass = com.amrita.pyqfetcher.ui.theme.LocalGlass.current
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) glass.fillSelected else glass.fill,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = com.amrita.pyqfetcher.ui.theme.glassBorder(glass),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(kindColor(kind).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (row.paper.year > 0) "'" + (row.paper.year % 100).toString().padStart(2, '0') else "—",
                    style = MaterialTheme.typography.titleMedium, color = kindColor(kind),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${kind.label}${if (row.paper.year > 0) " · ${row.paper.year}" else ""}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (StudyStatus.from(row.paper.status) == StudyStatus.SOLVED) Tag("Solved", Emerald)
                }
                val sub = buildString {
                    if (showSubject) append(row.subjectCode).append(" · ")
                    if (row.paper.sourceCode != row.subjectCode) append("Old code ${row.paper.sourceCode}") else append(row.subjectName)
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (activity is PaperActivity.Failed) {
                    Text(activity.reason, style = MaterialTheme.typography.bodySmall, color = colors.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                when {
                    activity is PaperActivity.Downloading -> {
                        if (activity.fraction < 0f) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        else CircularProgressIndicator(progress = { activity.fraction }, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    }
                    activity is PaperActivity.Queued -> Icon(Icons.Filled.Schedule, "Queued", tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    saved -> Icon(Icons.Filled.CheckCircle, "Saved", tint = Emerald, modifier = Modifier.size(22.dp))
                    activity is PaperActivity.Failed && onDownload != null ->
                        IconButton(onClick = onDownload) { Icon(Icons.Filled.ErrorOutline, "Retry", tint = colors.error) }
                    onDownload != null -> IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, "Download", tint = colors.primary) }
                }
            }
        }
    }
}

package org.ivanivorontsov.mudrost.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.ivanivorontsov.mudrost.engine.Entry
import org.ivanivorontsov.mudrost.engine.Quote
import org.ivanivorontsov.mudrost.engine.Topic

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
fun FavButton(active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(if (active) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = if (active) "Убрать из избранного" else "В избранное",
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnswerCard(entry: Entry, topics: List<Topic>, fav: Boolean, onFav: () -> Unit, onTopic: (Topic) -> Unit, shareText: String) {
    val ctx = LocalContext.current
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                topics.forEach { t -> AssistChip(onClick = { onTopic(t) }, label = { Text("${t.emoji} ${t.title}") }) }
            }
            Spacer(Modifier.size(8.dp))
            Text(entry.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 8.dp))
            Spacer(Modifier.size(12.dp))
            Text(entry.answer, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FavButton(fav, onFav)
                IconButton(onClick = { copy(ctx, shareText) }) { Icon(Icons.Outlined.ContentCopy, "Копировать") }
                IconButton(onClick = { share(ctx, shareText) }) { Icon(Icons.Outlined.Share, "Поделиться") }
            }
        }
    }
}

@Composable
fun OwnBadge() {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
        Text("из произведений автора", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
fun QuoteCard(
    quote: Quote, fav: Boolean, onFav: () -> Unit,
    counter: String? = null, onNext: (() -> Unit)? = null, label: String? = null,
) {
    val ctx = LocalContext.current
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FormatQuote, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp))
                if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 6.dp))
                Spacer(Modifier.weight(1f))
                if (quote.own) OwnBadge()
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.size(6.dp))
            Text(quote.text, style = QuoteStyle, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(end = 8.dp))
            Spacer(Modifier.size(10.dp))
            Text("— " + quote.author, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer)
            if (quote.source.isNotBlank()) Text(quote.source, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onNext != null) {
                    FilledTonalButton(onClick = onNext, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("Другая цитата", maxLines = 1)
                        if (counter != null) Text("  $counter", style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                    FavButton(fav, onFav)
                    IconButton(onClick = { copy(ctx, quoteText(quote)) }) { Icon(Icons.Outlined.ContentCopy, "Копировать") }
                    IconButton(onClick = { share(ctx, quoteText(quote)) }) { Icon(Icons.Outlined.Share, "Поделиться") }
                }
            }
        }
    }
}

@Composable
fun QuestionRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun Clickable(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) { content() }
}

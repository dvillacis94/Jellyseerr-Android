package com.nesoi.seerr.android.ui.media

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.media.MediaItem
import com.nesoi.seerr.android.media.MediaStatus
import com.nesoi.seerr.android.media.TmdbImage

/** Poster artwork at a 2:3 ratio, with a placeholder while it loads or when there is none. */
@Composable
fun PosterImage(url: String?, modifier: Modifier = Modifier, placeholder: ImageVector = Icons.Filled.Movie) {
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // A faint edge keeps dark posters apart from a dark background.
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(placeholder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** A title's poster, kind and library status, with its name and year underneath. */
@Composable
fun MediaCard(item: MediaItem, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            PosterImage(
                url = TmdbImage.poster(item.posterPath),
                modifier = Modifier.fillMaxWidth(),
                placeholder = if (item is MediaItem.Person) Icons.Filled.Person else Icons.Filled.Movie,
            )
            Row(
                // Pad first, then fix the height: padding applied after `height` shrinks the row, and the
        // pill and circle get squeezed into ovals.
                Modifier.fillMaxWidth().padding(7.dp).height(StatusIndicatorSize),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.kind?.let { TypePill(it) }
                Box(Modifier.weight(1f))
                item.mediaInfo?.mediaStatus?.takeIf { it.isNoteworthy }?.let { StatusIndicator(it) }
            }
        }
        // As tall as a two-line title and a year, so cards in a row line up whatever the
        // title's length, with the year right under the title.
        Column(Modifier.heightIn(min = 52.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.year?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

enum class MediaKind(val label: Int, val color: Color) {
    // Tailwind blue-600 and purple-600, Seerr web's pill colours.
    Movie(R.string.type_movie, Color(0xFF2563EB)),
    Series(R.string.type_series, Color(0xFF9333EA)),
    Collection(R.string.type_collection, Color(0xFF2563EB)),
}

/** The pill for a result's card, or null for people. */
val MediaItem.kind: MediaKind?
    get() = when (this) {
        is MediaItem.Movie -> MediaKind.Movie
        is MediaItem.Series -> MediaKind.Series
        is MediaItem.Collection -> MediaKind.Collection
        else -> null
    }

private val StatusIndicatorSize = 23.dp

@Composable
fun TypePill(kind: MediaKind) {
    Box(
        Modifier
            .height(StatusIndicatorSize)
            .clip(CircleShape)
            .background(kind.color.copy(alpha = 0.85f))
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(kind.label).uppercase(),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
        )
    }
}

/** A library status as a small circle with a symbol, readable on any artwork. */
@Composable
fun StatusIndicator(status: MediaStatus, modifier: Modifier = Modifier) {
    val symbol = status.symbol ?: return
    Box(
        modifier
            .size(StatusIndicatorSize)
            .background(Color.White, CircleShape)
            .padding(1.5.dp)
            .background(status.color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(symbol, contentDescription = stringResource(status.label), tint = Color.White, modifier = Modifier.size(12.dp))
    }
}

private val MediaStatus.symbol: ImageVector?
    get() = when (this) {
        MediaStatus.Available -> Icons.Filled.Check
        MediaStatus.PartiallyAvailable -> Icons.Filled.Remove
        MediaStatus.Processing -> Icons.Filled.Schedule
        MediaStatus.Pending -> Icons.Filled.Notifications
        MediaStatus.Blocklisted -> Icons.Filled.VisibilityOff
        MediaStatus.Deleted -> Icons.Filled.Delete
        MediaStatus.Unknown -> null
    }

/** Partially available is green like available, as on Seerr web: the minus tells them apart. */
val MediaStatus.color: Color
    get() = when (this) {
        MediaStatus.Available, MediaStatus.PartiallyAvailable -> Color(0xFF22C55E)
        MediaStatus.Processing -> Color(0xFF6366F1)
        MediaStatus.Pending -> Color(0xFFEAB308)
        MediaStatus.Blocklisted -> Color(0xFFEF4444)
        MediaStatus.Deleted -> Color(0xFF6B7280)
        MediaStatus.Unknown -> Color.Transparent
    }

val MediaStatus.label: Int
    get() = when (this) {
        MediaStatus.Available -> R.string.status_available
        MediaStatus.PartiallyAvailable -> R.string.status_partial
        MediaStatus.Processing -> R.string.status_processing
        MediaStatus.Pending -> R.string.status_pending
        MediaStatus.Blocklisted -> R.string.status_blocklisted
        MediaStatus.Deleted -> R.string.status_deleted
        MediaStatus.Unknown -> R.string.status_pending
    }

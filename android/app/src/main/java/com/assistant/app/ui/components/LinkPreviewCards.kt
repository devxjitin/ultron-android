package com.assistant.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.LinkPreviews
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantSurfaceElevated
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** One or two links get large cards with the full image; more links get compact rows so a long list stays readable. */
private const val LARGE_CARD_LIMIT = 2

/**
 * A card for every link in a finished reply. Each card shows at once (video thumbnail or site name) and fills in the
 * title, description and image when the page has been read.
 */
@Composable
fun LinkPreviewCards(text: String, modifier: Modifier = Modifier) {
    val links = remember(text) { LinkPreviews.extractLinks(text) }
    if (links.isEmpty()) return
    val large = links.size <= LARGE_CARD_LIMIT
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        links.forEach { link -> LinkPreviewCard(link, large) }
    }
}

@Composable
private fun LinkPreviewCard(link: String, large: Boolean) {
    val loaded by produceState<LinkPreviews.Preview?>(null, link) { value = LinkPreviews.preview(link) }
    val data = loaded ?: remember(link) { LinkPreviews.quick(link) } ?: return
    val context = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    val cardModifier = Modifier.fillMaxWidth().clip(shape).background(AssistantSurface, shape)
        .border(BorderStroke(1.dp, AssistantOutline), shape)
        .clickable { open(context, data.url) }
    if (large && data.imageUrl != null) {
        Column(cardModifier) {
            Thumbnail(data, Modifier.fillMaxWidth().aspectRatio(16f / 9f), playSize = 48.dp)
            CardText(data, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), descriptionLines = 2)
        }
    } else {
        Row(cardModifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (data.imageUrl != null) {
                Thumbnail(data, Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)), playSize = 28.dp)
            } else {
                SiteIcon(data.iconUrl)
            }
            CardText(data, Modifier.weight(1f).padding(start = 12.dp, end = 4.dp), descriptionLines = if (data.imageUrl == null) 1 else 0)
        }
    }
}

@Composable
private fun Thumbnail(data: LinkPreviews.Preview, modifier: Modifier, playSize: Dp) {
    val image by produceState<ImageBitmap?>(null, data.imageUrl) {
        value = data.imageUrl?.let { LinkPreviews.image(it) }?.asImageBitmap()
    }
    Box(modifier.background(AssistantSurfaceElevated), contentAlignment = Alignment.Center) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (data.video) {
            Box(Modifier.size(playSize).background(Color.Black.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Video", tint = Color.White, modifier = Modifier.size(playSize * 0.62f))
            }
        }
    }
}

/** The site's own icon, or a globe while it loads or when the site has none. */
@Composable
private fun SiteIcon(iconUrl: String?) {
    val icon by produceState<ImageBitmap?>(null, iconUrl) { value = iconUrl?.let { LinkPreviews.image(it) }?.asImageBitmap() }
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(AssistantFieldBackground),
        contentAlignment = Alignment.Center
    ) {
        val loaded = icon
        if (loaded != null) {
            Image(loaded, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(26.dp))
        } else {
            Icon(Icons.Outlined.Language, contentDescription = null, tint = AssistantAccent, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun CardText(data: LinkPreviews.Preview, modifier: Modifier, descriptionLines: Int) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            data.title, color = AssistantTextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
        )
        if (descriptionLines > 0 && data.description.isNotBlank()) {
            Text(
                data.description, color = AssistantTextSecondary, maxLines = descriptionLines, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp)
            )
        }
        Text(data.site, color = AssistantTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
    }
}

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

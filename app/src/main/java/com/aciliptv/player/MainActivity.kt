
package com.aciliptv.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

data class Channel(
    val name: String,
    val streamUrl: String,
    val group: String = "قنوات"
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AcilIptvApp()
        }
    }

    @Composable
    private fun AcilIptvApp() {
        val context = LocalContext.current

        var playlistUrl by remember { mutableStateOf("") }
        var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
        var selectedChannel by remember { mutableStateOf<Channel?>(null) }
        var status by remember { mutableStateOf("أدخل رابط M3U للبدء") }
        var loading by remember { mutableStateOf(false) }

        val exoPlayer = remember {
            ExoPlayer.Builder(context).build()
        }

        DisposableEffect(exoPlayer) {
            onDispose {
                exoPlayer.release()
            }
        }

        LaunchedEffect(selectedChannel) {
            selectedChannel?.let { channel ->
                exoPlayer.setMediaItem(
                    MediaItem.fromUri(channel.streamUrl)
                )
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        }

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF00D1B2),
                background = Color(0xFF080D16),
                surface = Color(0xFF111B2A)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF080D16))
                    .padding(16.dp)
            ) {
                Text(
                    text = "ACIL IPTV",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF00D1B2),
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "مشغل القنوات والبث المباشر",
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = playlistUrl,
                        onValueChange = { playlistUrl = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("رابط قائمة M3U") }
                    )

                    Spacer(Modifier.width(8.dp))

                    Button(
                        enabled = !loading && playlistUrl.isNotBlank(),
                        onClick = {
                            loading = true
                            status = "جارٍ تحميل القائمة..."

                            lifecycleScope.launch {
                                try {
                                    val loaded = withContext(Dispatchers.IO) {
                                        parseM3u(URL(playlistUrl).readText())
                                    }

                                    channels = loaded
                                    selectedChannel = null
                                    status = "تم تحميل ${loaded.size} قناة"

                                } catch (e: Exception) {
                                    status = "تعذر تحميل القائمة: ${e.localizedMessage}"
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    ) {
                        Text(if (loading) "..." else "تحميل")
                    }
                }

                Text(
                    text = status,
                    color = Color(0xFF9EACBD),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                selectedChannel?.let { channel ->
                    Text(
                        text = "يعرض الآن: ${channel.name}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    )

                    Spacer(Modifier.height(12.dp))
                }

                Text(
                    text = "القنوات (${channels.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(channels) { channel ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selectedChannel == channel)
                                        Color(0xFF183B43)
                                    else
                                        Color(0xFF111B2A),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedChannel = channel
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = channel.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )

                                Text(
                                    text = channel.group,
                                    color = Color(0xFF9EACBD),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Text(
                                "▶",
                                color = Color(0xFF00D1B2)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun parseM3u(content: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var name = "قناة"
        var group = "قنوات"

        content.lineSequence().forEach { raw ->
            val line = raw.trim()

            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    name = line.substringAfter(",", "قناة")
                        .trim()
                        .ifBlank { "قناة" }

                    group = Regex("group-title=\"([^\"]*)\"")
                        .find(line)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.ifBlank { "قنوات" }
                        ?: "قنوات"
                }

                line.startsWith("http://") ||
                line.startsWith("https://") -> {
                    result.add(Channel(name, line, group))
                    name = "قناة"
                    group = "قنوات"
                }
            }
        }

        return result
    }
}

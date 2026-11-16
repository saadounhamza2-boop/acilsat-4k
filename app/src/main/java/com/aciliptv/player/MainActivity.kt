
package com.aciliptv.player

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

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
        var status by remember { mutableStateOf("أدخل رابط M3U أو اختر ملفاً") }
        var loading by remember { mutableStateOf(false) }

        val exoPlayer = remember {
            ExoPlayer.Builder(context).build()
        }

        DisposableEffect(exoPlayer) {
            onDispose {
                exoPlayer.release()
            }
        }

        val filePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                loading = true
                status = "جارٍ قراءة ملف القائمة..."

                lifecycleScope.launch {
                    try {
                        val content = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use {
                                it.bufferedReader(Charsets.UTF_8).readText()
                            } ?: throw Exception("تعذر فتح الملف")
                        }

                        val loaded = parseM3u(content)

                        channels = loaded
                        selectedChannel = null
                        status = if (loaded.isEmpty()) {
                            "لم يتم العثور على قنوات في الملف"
                        } else {
                            "تم تحميل ${loaded.size} قناة بنجاح"
                        }

                    } catch (e: Exception) {
                        status = "خطأ في قراءة الملف: ${e.message}"
                    } finally {
                        loading = false
                    }
                }
            }
        }

        LaunchedEffect(selectedChannel) {
            selectedChannel?.let { channel ->
                exoPlayer.stop()
                exoPlayer.setMediaItem(
                    MediaItem.fromUri(channel.streamUrl)
                )
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        }

        fun loadPlaylist(url: String) {
            if (url.isBlank()) {
                status = "أدخل رابط القائمة أولاً"
                return
            }

            loading = true
            status = "جارٍ تحميل القائمة..."

            lifecycleScope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        downloadPlaylist(url)
                    }

                    val loaded = parseM3u(content)

                    channels = loaded
                    selectedChannel = null

                    status = if (loaded.isEmpty()) {
                        "القائمة فارغة أو صيغة M3U غير معروفة"
                    } else {
                        "تم تحميل ${loaded.size} قناة بنجاح"
                    }

                } catch (e: Exception) {
                    status = "فشل التحميل: ${e.localizedMessage ?: "خطأ غير معروف"}"
                } finally {
                    loading = false
                }
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

                OutlinedTextField(
                    value = playlistUrl,
                    onValueChange = { playlistUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("رابط M3U أو get.php") },
                    placeholder = {
                        Text("ضع رابط الاشتراك هنا")
                    }
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !loading && playlistUrl.isNotBlank(),
                        onClick = {
                            loadPlaylist(playlistUrl.trim())
                        }
                    ) {
                        Text("تحميل الرابط")
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !loading,
                        onClick = {
                            filePicker.launch("*/*")
                        }
                    ) {
                        Text("اختيار ملف M3U")
                    }
                }

                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        color = Color(0xFF00D1B2)
                    )
                }

                Text(
                    text = status,
                    color = Color(0xFF9EACBD),
                    modifier = Modifier.padding(vertical = 12.dp)
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

                Spacer(Modifier.height(8.dp))

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

    private fun downloadPlaylist(address: String): String {
        val url = URL(address)

        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true

            connection.setRequestProperty(
                "User-Agent",
                "VLC/3.0.20 LibVLC/3.0.20"
            )

            connection.setRequestProperty(
                "Accept",
                "application/vnd.apple.mpegurl, audio/x-mpegurl, */*"
            )

            connection.connect()

            val responseCode = connection.responseCode

            if (responseCode !in 200..299) {
                throw Exception("HTTP $responseCode")
            }

            val contentType = connection.contentType ?: ""

            val charsetName = Regex(
                "charset=([^;]+)",
                RegexOption.IGNORE_CASE
            ).find(contentType)?.groupValues?.get(1)
                ?.trim()
                ?.trim('"')

            val charset = try {
                if (charsetName != null) {
                    Charset.forName(charsetName)
                } else {
                    Charsets.UTF_8
                }
            } catch (_: Exception) {
                Charsets.UTF_8
            }

            return connection.inputStream.use { input ->
                BufferedReader(
                    InputStreamReader(input, charset)
                ).readText().removePrefix("\uFEFF")
            }

        } finally {
            connection.disconnect()
        }
    }

    private fun parseM3u(content: String): List<Channel> {
        val result = mutableListOf<Channel>()

        var name = "قناة"
        var group = "قنوات"

        content.lineSequence().forEach { raw ->

            val line = raw.trim()

            if (line.isBlank()) return@forEach

            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {

                    val comma = findDisplayNamePosition(line)

                    name = if (comma >= 0) {
                        line.substring(comma + 1).trim()
                    } else {
                        "قناة"
                    }

                    val nameAttribute = findAttribute(
                        line,
                        "tvg-name"
                    )

                    if (name.isBlank() && !nameAttribute.isNullOrBlank()) {
                        name = nameAttribute
                    }

                    if (name.isBlank()) {
                        name = nameAttribute ?: "قناة"
                    }

                    group = findAttribute(
                        line,
                        "group-title"
                    ) ?: "قنوات"

                    if (group.isBlank()) {
                        group = "قنوات"
                    }
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    group = line.substringAfter(":")
                        .trim()
                        .ifBlank { "قنوات" }
                }

                line.startsWith("http://", ignoreCase = true) ||
                line.startsWith("https://", ignoreCase = true) -> {

                    result.add(
                        Channel(
                            name = name,
                            streamUrl = line,
                            group = group
                        )
                    )

                    name = "قناة"
                    group = "قنوات"
                }
            }
        }

        return result
    }

    private fun findAttribute(
        line: String,
        attribute: String
    ): String? {
        val pattern = Regex(
            """(?:^|\s)${Regex.escape(attribute)}\s*=\s*["']([^"']*)["']""",
            RegexOption.IGNORE_CASE
        )

        return pattern.find(line)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun findDisplayNamePosition(line: String): Int {
        var insideQuotes = false
        var quote = ' '

        for (i in line.indices) {
            val char = line[i]

            if ((char == '"' || char == '\'') &&
                (!insideQuotes || char == quote)
            ) {
                if (insideQuotes) {
                    insideQuotes = false
                } else {
                    insideQuotes = true
                    quote = char
                }
            } else if (char == ',' && !insideQuotes) {
                return i
            }
        }

        return -1
    }
}

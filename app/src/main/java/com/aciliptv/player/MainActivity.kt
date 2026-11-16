package com.aciliptv.player

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class Channel(val name: String, val group: String, val url: String)

class MainActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var status: TextView
    private lateinit var list: ListView
    private lateinit var search: EditText
    private lateinit var urlInput: EditText
    private val channels = mutableListOf&lt;Channel&gt;()
    private var visibleChannels = listOf&lt;Channel&gt;()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 13, 27)
        window.navigationBarColor = Color.BLACK
        player = ExoPlayer.Builder(this).build()
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 8)
            setBackgroundColor(Color.rgb(9, 13, 27))
        }
        val title = TextView(this).apply {
            text = "ACIL IPTV"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 8, 4, 12)
        }
        root.addView(title)
        urlInput = EditText(this).apply {
            hint = "Paste your M3U playlist URL"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(28, 35, 55))
        }
        root.addView(urlInput, LinearLayout.LayoutParams(-1, 52))
        val load = Button(this).apply {
            text = "LOAD PLAYLIST"
            setOnClickListener { loadPlaylist(urlInput.text.toString().trim()) }
        }
        root.addView(load, LinearLayout.LayoutParams(-1, 48))
        playerView = PlayerView(this).apply {
            player = this@MainActivity.player
            useController = true
        }
        root.addView(playerView, LinearLayout.LayoutParams(-1, 190))
        status = TextView(this).apply {
            text = "Enter an M3U URL to load channels"
            setTextColor(Color.LTGRAY)
            setPadding(4, 8, 4, 8)
        }
        root.addView(status)
        search = EditText(this).apply {
            hint = "Search channels"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(28, 35, 55))
            addTextChangedListener(SimpleTextWatcher { filterChannels(it) })
        }
        root.addView(search, LinearLayout.LayoutParams(-1, 50))
        list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(Color.DKGRAY)
            dividerHeight = 1
            setBackgroundColor(Color.rgb(15, 21, 38))
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        list.setOnItemClickListener { _, _, position, _ ->
            val channel = visibleChannels[position]
            player?.setMediaItem(MediaItem.fromUri(channel.url))
            player?.prepare()
            player?.play()
            status.text = "Playing: ${channel.name}"
        }
        setContentView(root)
    }

    private fun loadPlaylist(address: String) {
        if (address.isBlank()) {
            status.text = "Please enter a playlist URL"
            return
        }
        status.text = "Loading playlist..."
        thread {
            try {
                val connection = (URL(address).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "ACIL IPTV Android")
                }
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val parsed = parseM3u(content)
                runOnUiThread {
                    channels.clear()
                    channels.addAll(parsed)
                    filterChannels(search.text.toString())
                    status.text = "Loaded ${channels.size} channels"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Load failed: ${e.message ?: "check URL"}" }
            }
        }
    }

    private fun parseM3u(text: String): List&lt;Channel&gt; {
        val result = mutableListOf&lt;Channel&gt;()
        var name = ""
        var group = "Other"
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("#EXTINF", true)) {
                name = line.substringAfterLast(',').trim().ifBlank { "Unknown channel" }
                group = Regex("""group-title="([^"]*)"""").find(line)?.groupValues?.get(1) ?: "Other"
                val tvg = Regex("""tvg-name="([^"]*)"""").find(line)?.groupValues?.get(1)
                if (!tvg.isNullOrBlank()) name = tvg
            } else if (line.isNotEmpty() && !line.startsWith("#") && name.isNotEmpty()) {
                result.add(Channel(name, group, line))
                name = ""
            }
        }
        return result
    }

    private fun filterChannels(query: String) {
        val q = query.trim()
        visibleChannels = channels.filter {
            q.isEmpty() || it.name.contains(q, true) || it.group.contains(q, true)
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            visibleChannels.map { "${it.name}  •  ${it.group}" })
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}

private class SimpleTextWatcher(val changed: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString() ?: "") }
    override fun afterTextChanged(s: android.text.Editable?) {}
}

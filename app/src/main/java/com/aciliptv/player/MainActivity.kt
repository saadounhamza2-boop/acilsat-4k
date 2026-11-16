private val channels = mutableListOf<Channel>()
private var visibleChannels = listOf<Channel>()

private fun parseM3u(text: String): List<Channel> {
    val result = mutableListOf<Channel>()

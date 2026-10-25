package com.flavio.m3u

import android.content.Context
import android.util.Base64
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

private data class M3UChannel(
    val name: String,
    val streamUrl: String,
    val logo: String,
    val group: String
)

class M3UProvider(private val appContext: Context) : MainAPI() {
    override var mainUrl = "https://example.invalid/"
    override var name = "M3U Channels"
    override var lang = "es"
    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    override val mainPage = mainPageOf("m3u" to "Canales")

    private fun playlistUrl(): String =
        appContext.getSharedPreferences(M3UPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(M3UPlugin.PLAYLIST_KEY, "")?.trim().orEmpty()

    private suspend fun readChannels(): List<M3UChannel> {
        val url = playlistUrl()
        if (url.isBlank()) return emptyList()
        return parsePlaylist(app.get(url).text)
    }

    private fun parsePlaylist(text: String): List<M3UChannel> {
        val result = mutableListOf<M3UChannel>()
        var infoLine: String? = null

        for (line in text.lineSequence().map { it.trim() }) {
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                infoLine = line
                continue
            }
            if (line.isBlank() || line.startsWith("#")) continue

            val info = infoLine ?: continue
            val channelName = info.substringAfterLast(",", "").trim().ifBlank {
                line.substringAfterLast("/")
            }
            val logo = attribute(info, "tvg-logo").orEmpty()
            val group = attribute(info, "group-title").orEmpty()
            result += M3UChannel(channelName, line, logo, group)
            infoLine = null
        }
        return result
    }

    private fun attribute(line: String, key: String): String? {
        val pattern = Regex(Regex.escape(key) + "=\"([^\"]*)\"")
        return pattern.find(line)?.groupValues?.getOrNull(1)
    }

    private fun encode(channel: M3UChannel): String {
        val raw = listOf(channel.name, channel.streamUrl, channel.logo).joinToString("\n")
        return Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
    }

    private fun decode(value: String): M3UChannel {
        val decoded = String(Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        val parts = decoded.split("\n", limit = 3)
        if (parts.size < 2) throw IllegalArgumentException("Canal M3U inválido")
        return M3UChannel(parts[0], parts[1], parts.getOrElse(2) { "" }, "")
    }

    private fun M3UChannel.toResponse() =
        newLiveSearchResponse(name, encode(this), TvType.Live, fix = false) {
            posterUrl = logo.ifBlank { null }
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val rows = readChannels()
            .groupBy { it.group.ifBlank { "Canales" } }
            .map { (group, channels) -> HomePageList(group, channels.map { it.toResponse() }) }
        return newHomePageResponse(rows, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return readChannels()
            .filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.group.contains(query, ignoreCase = true)
            }
            .map { it.toResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val channel = decode(url)
        return newLiveStreamLoadResponse(channel.name, channel.streamUrl, channel.streamUrl) {
            posterUrl = channel.logo.ifBlank { null }
            plot = channel.group.ifBlank { null }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback(
            newExtractorLink(this.name, this.name, data) {
                if (data.substringBefore("?").endsWith(".m3u8", ignoreCase = true)) {
                    type = ExtractorLinkType.M3U8
                }
                referer = ""
            }
        )
        return true
    }
}

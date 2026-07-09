package me.xdan.aperture.util

data class CleanedMediaInfo(
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null
)

object FilenameParser {
    private val YEAR_REGEX = Regex("(?<=\\D)(\\d{4})(?=\\D|$)")
    private val TV_SHOW_REGEX = Regex("(?i)(.*)[. ]S(\\d{1,2})E(\\d{1,2})")
    
    private val NOISE_REGEX = Regex("(?i)[. ](1080p|720p|4k|2160p|x264|x265|h264|h265|web-dl|bluray|brrip|dvdrip|multi|dual-audio|hc|sub|eng|ita|fre|ger|spa|rus|chi|kor|jpn|hevc|aac|ac3|dts|dd5\\.1|xvid|divx|repack|proper|internal|readnfo|nfofix|complete|unrated|extended|directors.cut|theatrical|limited|remastered|criterion).*")

    fun parse(filename: String): CleanedMediaInfo {
        // Strip extension
        val nameWithoutExtension = filename.substringBeforeLast(".")
        
        // Try TV Show match first
        val tvMatch = TV_SHOW_REGEX.find(nameWithoutExtension)
        if (tvMatch != null) {
            val title = cleanTitle(tvMatch.groupValues[1])
            val season = tvMatch.groupValues[2].toIntOrNull()
            val episode = tvMatch.groupValues[3].toIntOrNull()
            return CleanedMediaInfo(title, null, season, episode)
        }
        
        // Try Movie match
        var title = nameWithoutExtension
        val yearMatch = YEAR_REGEX.find(nameWithoutExtension)
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()
        
        if (yearMatch != null) {
            title = nameWithoutExtension.substring(0, yearMatch.range.first)
        }
        
        title = cleanTitle(title)
        
        return CleanedMediaInfo(title, year)
    }

    private fun cleanTitle(title: String): String {
        return title.replace(NOISE_REGEX, "")
            .replace(".", " ")
            .replace("_", " ")
            .trim()
    }
}

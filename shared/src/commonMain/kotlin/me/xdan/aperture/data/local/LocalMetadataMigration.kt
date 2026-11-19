package me.xdan.aperture.data.local

/** Schema 10 to 11: local titles keep genres, a rating and the picture height, for filters. */
object LocalMetadataMigration {
    const val FROM = 10
    const val TO = 11

    val statements: List<String> = listOf(
        "ALTER TABLE `media` ADD COLUMN `genres` TEXT",
        "ALTER TABLE `media` ADD COLUMN `rating` REAL",
        "ALTER TABLE `media` ADD COLUMN `videoHeight` INTEGER"
    )
}

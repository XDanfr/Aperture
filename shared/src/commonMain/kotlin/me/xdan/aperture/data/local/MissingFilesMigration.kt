package me.xdan.aperture.data.local

/** Schema 11 to 12: a local title whose file has gone is marked missing rather than deleted. */
object MissingFilesMigration {
    const val FROM = 11
    const val TO = 12

    val statements: List<String> = listOf(
        "ALTER TABLE `media` ADD COLUMN `missingSince` INTEGER"
    )
}

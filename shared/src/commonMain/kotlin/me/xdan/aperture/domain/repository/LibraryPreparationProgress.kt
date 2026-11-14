package me.xdan.aperture.domain.repository

enum class LibraryPreparationStage {
    IDLE,
    DISCOVERING,
    MATCHING,
    COMPLETE,
    ERROR
}

data class LibraryPreparationProgress(
    val stage: LibraryPreparationStage = LibraryPreparationStage.IDLE,
    val totalItems: Int = 0,
    val completedItems: Int = 0,
    val currentTitle: String? = null,
    val currentItemProgress: Float = 0f,
    val posterPaths: List<String> = emptyList(),
    val errorMessage: String? = null
) {
    val totalProgress: Float
        get() = if (totalItems == 0) {
            if (stage == LibraryPreparationStage.COMPLETE) 1f else 0f
        } else {
            completedItems.toFloat() / totalItems
        }
}

package me.xdan.aperture.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.MediaRecord

/**
 * Media files, read as one profile sees them. Every read that returns [MediaEntity] joins that
 * profile's My List and hidden state; writes take the shared [MediaRecord].
 */
@Dao
interface MediaDao {
    @Query("$SELECT_FOR_PROFILE WHERE $VISIBLE ORDER BY media.dateAdded DESC")
    fun getAllMedia(profileId: Long): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY dateAdded DESC")
    suspend fun getAllMediaOnce(): List<MediaRecord>

    @Query("$SELECT_FOR_PROFILE WHERE media.type = :type AND $VISIBLE ORDER BY media.title COLLATE NOCASE ASC")
    fun getMediaByType(profileId: Long, type: String): Flow<List<MediaEntity>>

    @Query("""
        $SELECT_FOR_PROFILE WHERE $VISIBLE AND (
            state.isFavorite = 1 OR (media.type = 'EPISODE' AND EXISTS (
                SELECT 1 FROM media AS saved
                JOIN profile_media_state AS savedState
                    ON savedState.mediaId = saved.id AND savedState.profileId = :profileId
                WHERE saved.type = 'EPISODE' AND saved.title = media.title AND savedState.isFavorite = 1
            ))
        ) ORDER BY state.favoriteAddedAt DESC, media.dateAdded DESC
    """)
    fun getFavoriteMedia(profileId: Long): Flow<List<MediaEntity>>

    @Query("$SELECT_FOR_PROFILE WHERE state.isHidden = 1 ORDER BY media.title COLLATE NOCASE ASC")
    fun getHiddenMedia(profileId: Long): Flow<List<MediaEntity>>

    @Query("$SELECT_FOR_PROFILE WHERE media.id = :id")
    suspend fun getMediaById(profileId: Long, id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getRecordById(id: Long): MediaRecord?

    @Query("$SELECT_FOR_PROFILE WHERE media.type = 'EPISODE' AND media.title = :showTitle AND $VISIBLE ORDER BY media.seasonNumber ASC, media.episodeNumber ASC, media.filePath ASC")
    suspend fun getEpisodesForShow(profileId: Long, showTitle: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE type = 'EPISODE' AND title = :showTitle ORDER BY seasonNumber ASC, episodeNumber ASC, filePath ASC")
    suspend fun getEpisodeRecordsForShow(showTitle: String): List<MediaRecord>

    @Query("SELECT * FROM media WHERE filePath = :filePath")
    suspend fun getMediaByPath(filePath: String): MediaRecord?

    @Query("SELECT * FROM media WHERE sourceRootUri = :sourceRootUri")
    suspend fun getMediaFromSource(sourceRootUri: String): List<MediaRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaRecord): Long

    @Update
    suspend fun updateMedia(media: MediaRecord)

    @Query("DELETE FROM media WHERE id = :mediaId")
    suspend fun deleteMediaRow(mediaId: Long)

    @Query("DELETE FROM profile_media_state WHERE mediaId = :mediaId")
    suspend fun deleteStateForMedia(mediaId: Long)

    @Query("DELETE FROM playback_progress WHERE mediaId = :mediaId")
    suspend fun deleteProgressForMedia(mediaId: Long)

    /** Removes a file along with every profile's state and progress for it. */
    @Transaction
    suspend fun deleteMedia(mediaId: Long) {
        deleteStateForMedia(mediaId)
        deleteProgressForMedia(mediaId)
        deleteMediaRow(mediaId)
    }

    @Query("$SELECT_FOR_PROFILE WHERE $VISIBLE AND media.title LIKE '%' || :query || '%'")
    fun searchMedia(profileId: Long, query: String): Flow<List<MediaEntity>>

    @Query("INSERT OR IGNORE INTO profile_media_state (profileId, mediaId, isFavorite, isHidden) VALUES (:profileId, :mediaId, 0, 0)")
    suspend fun ensureState(profileId: Long, mediaId: Long)

    @Query("""
        INSERT OR IGNORE INTO profile_media_state (profileId, mediaId, isFavorite, isHidden)
        SELECT :profileId, id, 0, 0 FROM media WHERE type = 'EPISODE' AND title = :showTitle
    """)
    suspend fun ensureShowState(profileId: Long, showTitle: String)

    @Query("UPDATE profile_media_state SET isFavorite = :isFavorite, favoriteAddedAt = CASE WHEN :isFavorite = 1 THEN :changedAt ELSE NULL END WHERE profileId = :profileId AND mediaId = :mediaId")
    suspend fun updateFavorite(profileId: Long, mediaId: Long, isFavorite: Boolean, changedAt: Long)

    @Query("""
        UPDATE profile_media_state
        SET isFavorite = :isFavorite, favoriteAddedAt = CASE WHEN :isFavorite = 1 THEN :changedAt ELSE NULL END
        WHERE profileId = :profileId AND mediaId IN (SELECT id FROM media WHERE type = 'EPISODE' AND title = :showTitle)
    """)
    suspend fun updateShowFavorite(profileId: Long, showTitle: String, isFavorite: Boolean, changedAt: Long)

    @Query("UPDATE profile_media_state SET isHidden = :isHidden WHERE profileId = :profileId AND mediaId = :mediaId")
    suspend fun updateHidden(profileId: Long, mediaId: Long, isHidden: Boolean)

    /** Drops state rows that no longer say anything. */
    @Query("DELETE FROM profile_media_state WHERE profileId = :profileId AND isFavorite = 0 AND isHidden = 0")
    suspend fun pruneEmptyState(profileId: Long)

    @Transaction
    suspend fun setFavorite(profileId: Long, mediaId: Long, isFavorite: Boolean, changedAt: Long) {
        ensureState(profileId, mediaId)
        updateFavorite(profileId, mediaId, isFavorite, changedAt)
        pruneEmptyState(profileId)
    }

    @Transaction
    suspend fun setShowFavorite(profileId: Long, showTitle: String, isFavorite: Boolean, changedAt: Long) {
        ensureShowState(profileId, showTitle)
        updateShowFavorite(profileId, showTitle, isFavorite, changedAt)
        pruneEmptyState(profileId)
    }

    @Transaction
    suspend fun setHidden(profileId: Long, mediaId: Long, isHidden: Boolean) {
        ensureState(profileId, mediaId)
        updateHidden(profileId, mediaId, isHidden)
        pruneEmptyState(profileId)
    }

    @Query("$SELECT_FOR_PROFILE WHERE media.type = 'EPISODE' AND media.title = :showTitle AND state.isFavorite = 1 ORDER BY state.favoriteAddedAt DESC LIMIT 1")
    suspend fun getFavoriteEpisodeForShow(profileId: Long, showTitle: String): MediaEntity?

    companion object {
        const val SELECT_FOR_PROFILE = """
            SELECT media.*,
                COALESCE(state.isFavorite, 0) AS isFavorite,
                state.favoriteAddedAt AS favoriteAddedAt,
                COALESCE(state.isHidden, 0) AS isHidden
            FROM media
            LEFT JOIN profile_media_state AS state
                ON state.mediaId = media.id AND state.profileId = :profileId
        """
        const val VISIBLE = "COALESCE(state.isHidden, 0) = 0"
    }
}

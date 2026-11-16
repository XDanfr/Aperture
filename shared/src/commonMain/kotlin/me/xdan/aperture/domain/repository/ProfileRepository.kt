package me.xdan.aperture.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import me.xdan.aperture.data.local.entity.ProfileEntity

/** Who's watching. There is always at least one profile, and exactly one is active. */
interface ProfileRepository {
    val profiles: Flow<List<ProfileEntity>>
    val activeProfileId: Flow<Long>
    val activeProfile: Flow<ProfileEntity?>
    /** Where the active profile's library comes from. */
    val activeLibraryMode: Flow<me.xdan.aperture.domain.model.LibraryMode>

    /** True once someone has picked a profile since launch, or when there's only one to pick. */
    val profileChosen: StateFlow<Boolean>

    /** Run at launch: makes sure a profile is active, and skips the picker when there's only one. */
    suspend fun resolveLaunchChoice()

    suspend fun selectProfile(profileId: Long)
    /** Creates a profile whose theme starts as its avatar colour. Returns its id. */
    suspend fun createProfile(name: String, colorId: String): Long
    suspend fun updateProfile(profileId: Long, name: String, colorId: String)
    suspend fun setLibraryMode(profileId: Long, mode: me.xdan.aperture.domain.model.LibraryMode)
    /** Deletes a profile and everything it saved. The last profile can't be deleted. */
    suspend fun deleteProfile(profileId: Long)
}

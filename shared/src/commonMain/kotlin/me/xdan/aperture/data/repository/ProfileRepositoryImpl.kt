package me.xdan.aperture.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import me.xdan.aperture.domain.model.LibraryMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.xdan.aperture.data.local.ProfileMigration
import me.xdan.aperture.data.local.dao.ProfileDao
import me.xdan.aperture.data.local.entity.ProfileEntity
import me.xdan.aperture.domain.repository.ProfileRepository
import me.xdan.aperture.domain.repository.UserPreferencesRepository

class ProfileRepositoryImpl(
    private val profileDao: ProfileDao,
    private val preferences: UserPreferencesRepository,
    private val jellyfinDao: me.xdan.aperture.data.local.dao.JellyfinDao,
    private val jellyfinCredentials: me.xdan.aperture.data.jellyfin.JellyfinCredentials
) : ProfileRepository {

    private val defaultMutex = Mutex()
    private var defaultEnsured = false

    /** Fresh installs skip the 8 → 9 migration, so profile 1 is created on first use. */
    private suspend fun ensureDefaultProfile() = defaultMutex.withLock {
        if (defaultEnsured) return@withLock
        if (profileDao.getProfile(ProfileEntity.DEFAULT_ID) == null && profileDao.getProfilesOnce().isEmpty()) {
            profileDao.insertProfile(
                ProfileEntity(
                    id = ProfileEntity.DEFAULT_ID,
                    name = ProfileMigration.DEFAULT_PROFILE_NAME,
                    colorId = ProfileMigration.DEFAULT_PROFILE_COLOR
                )
            )
        }
        defaultEnsured = true
    }

    override val profiles: Flow<List<ProfileEntity>> = profileDao.getProfiles()
        .onStart { ensureDefaultProfile() }

    override val activeProfileId: Flow<Long> = preferences.activeProfileId

    override val activeProfile: Flow<ProfileEntity?> = combine(profiles, activeProfileId) { all, id ->
        all.firstOrNull { it.id == id } ?: all.firstOrNull()
    }

    override val activeLibraryMode: Flow<LibraryMode> = activeProfile
        .map { LibraryMode.fromStored(it?.libraryMode) }
        .distinctUntilChanged()

    private val _profileChosen = MutableStateFlow(false)
    override val profileChosen: StateFlow<Boolean> = _profileChosen

    private val _pickerRequested = MutableStateFlow(false)
    override val pickerRequested: StateFlow<Boolean> = _pickerRequested

    override fun openPicker() {
        _pickerRequested.value = true
        _profileChosen.value = false
    }

    override fun dismissPicker() {
        if (!_pickerRequested.value) return
        _pickerRequested.value = false
        _profileChosen.value = true
    }

    override suspend fun resolveLaunchChoice() {
        ensureDefaultProfile()
        val all = profileDao.getProfilesOnce()
        val active = preferences.activeProfileId.first()
        if (all.none { it.id == active }) all.firstOrNull()?.let { preferences.setActiveProfileId(it.id) }
        if (all.size <= 1) _profileChosen.value = true
    }

    override suspend fun selectProfile(profileId: Long) {
        ensureDefaultProfile()
        if (profileDao.getProfile(profileId) == null) return
        preferences.setActiveProfileId(profileId)
        profileDao.markUsed(profileId, System.currentTimeMillis())
        _pickerRequested.value = false
        _profileChosen.value = true
    }

    override suspend fun createProfile(name: String, colorId: String): Long {
        ensureDefaultProfile()
        val id = profileDao.insertProfile(ProfileEntity(name = name.trim().ifBlank { "Profile" }, colorId = colorId))
        preferences.setThemeIdFor(id, colorId)
        return id
    }

    override suspend fun updateProfile(profileId: Long, name: String, colorId: String) {
        val profile = profileDao.getProfile(profileId) ?: return
        profileDao.updateProfile(profile.copy(name = name.trim().ifBlank { profile.name }, colorId = colorId))
    }

    override suspend fun setLibraryMode(profileId: Long, mode: LibraryMode) {
        profileDao.setLibraryMode(profileId, mode.name)
    }

    override suspend fun deleteProfile(profileId: Long) {
        val all = profileDao.getProfilesOnce()
        if (all.size <= 1 || all.none { it.id == profileId }) return
        if (preferences.activeProfileId.first() == profileId) {
            preferences.setActiveProfileId(all.first { it.id != profileId }.id)
        }
        val jellyfinAccount = jellyfinDao.accountForProfileOnce(profileId)
        profileDao.deleteProfile(profileId)
        jellyfinAccount?.let { jellyfinCredentials.forget(it.id) }
        preferences.clearProfilePreferences(profileId)
    }
}

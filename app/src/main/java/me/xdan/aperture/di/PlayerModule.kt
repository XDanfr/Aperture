@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import me.xdan.aperture.ui.screen.player.PlayerEngine
import me.xdan.aperture.ui.screen.player.PlayerEngineFactory
import me.xdan.aperture.ui.screen.player.PlayerEngineManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun providePlayerEngine(
        manager: PlayerEngineManager
    ): PlayerEngine = manager

    @Provides
    @Singleton
    fun providePlayerEngineManager(
        @ApplicationContext context: Context,
        preferences: UserPreferencesRepository,
        factory: PlayerEngineFactory
    ): PlayerEngineManager = PlayerEngineManager(context, preferences, factory)
}

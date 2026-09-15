@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.xdan.aperture.ui.screen.player.PlayerEngine
import me.xdan.aperture.ui.screen.player.PlayerEngineManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface PlayerModule {

    @Binds
    @Singleton
    fun bindPlayerEngine(
        manager: PlayerEngineManager
    ): PlayerEngine
}

package me.xdan.aperture.mobile

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.xdan.aperture.mobile.data.CompanionSettings
import me.xdan.aperture.mobile.data.TvClient
import me.xdan.aperture.mobile.data.TvDiscovery
import me.xdan.aperture.mobile.data.TvRepository
import okhttp3.OkHttpClient
import me.xdan.aperture.mobile.ui.theme.currentPhoneColors
import java.util.concurrent.TimeUnit

class CompanionApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings by lazy { CompanionSettings(this) }
    val discovery by lazy { TvDiscovery(this) }
    val repository by lazy {
        val http = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        TvRepository(appScope, settings, TvClient(http), discovery) { currentPhoneColors(this) }
    }
}

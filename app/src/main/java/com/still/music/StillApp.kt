package com.still.music

import android.app.Application
import androidx.room.Room
import com.still.music.data.*
import com.still.music.provider.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class StillApp : Application() {
    val graph by lazy { AppGraph(this) }
}
class AppGraph(app: Application) {
    private val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    val audius: MusicProvider = AudiusProvider(http, BuildConfig.AUDIUS_API_KEY)
    val providers = ProviderRegistry(audius)
    val database = Room.databaseBuilder(app, StillDatabase::class.java, "still.db").build()
    val library = LibraryRepository(database.library())
    val preferences = Preferences(app)
    val playbackReady = MutableStateFlow(false)
    val storageError = MutableStateFlow<String?>(null)
}

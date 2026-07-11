package com.streambox.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.streambox.app.data.db.ChannelDao
import com.streambox.app.data.db.ChannelHealthDao
import com.streambox.app.data.db.FavoriteDao
import com.streambox.app.data.db.ProgrammeDao
import com.streambox.app.data.db.RecentDao
import com.streambox.app.data.db.StreamBoxDatabase
import com.streambox.app.data.epg.XmltvParser
import com.streambox.app.data.m3u.M3uParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Browser-like UA: several iptv-org streams reject default HTTP clients.
 */
const val STREAM_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StreamBoxDatabase =
        Room.databaseBuilder(context, StreamBoxDatabase::class.java, "streambox.db")
            .addMigrations(StreamBoxDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideChannelDao(db: StreamBoxDatabase): ChannelDao = db.channelDao()
    @Provides fun provideFavoriteDao(db: StreamBoxDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideRecentDao(db: StreamBoxDatabase): RecentDao = db.recentDao()
    @Provides fun provideProgrammeDao(db: StreamBoxDatabase): ProgrammeDao = db.programmeDao()
    @Provides fun provideChannelHealthDao(db: StreamBoxDatabase): ChannelHealthDao = db.channelHealthDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", STREAM_USER_AGENT)
                        .build()
                )
            }
            .build()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideM3uParser(): M3uParser = M3uParser()

    @Provides
    @Singleton
    fun provideXmltvParser(): XmltvParser = XmltvParser()
}

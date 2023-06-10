package com.m3u.data.koin

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import com.m3u.core.architecture.service.UserInterface
import com.m3u.core.architecture.service.NotificationService
import com.m3u.core.architecture.service.PlayerManager
import com.m3u.core.util.serialization.asConverterFactory
import com.m3u.data.BuildConfig
import com.m3u.data.database.M3UDatabase
import com.m3u.data.remote.api.RemoteApi
import com.m3u.data.remote.parser.m3u.DefaultPlaylistParser
import com.m3u.data.remote.parser.m3u.PlaylistParser
import com.m3u.data.remote.parser.udp.AndroidUdpDiscover
import com.m3u.data.remote.parser.udp.UdpDiscover
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.MediaRepository
import com.m3u.data.repository.PostRepository
import com.m3u.data.repository.RemoteRepository
import com.m3u.data.repository.impl.FeedRepositoryImpl
import com.m3u.data.repository.impl.LiveRepositoryImpl
import com.m3u.data.repository.impl.MediaRepositoryImpl
import com.m3u.data.repository.impl.PostRepositoryImpl
import com.m3u.data.repository.impl.RemoteRepositoryImpl
import com.m3u.data.service.AndroidNotificationService
import com.m3u.data.service.AndroidPlayerManager
import com.m3u.data.service.ConflatedUserInterface
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import retrofit2.Retrofit
import java.net.InetSocketAddress
import java.net.Proxy

object DataModule {
    val AndroidPlatform = module {
        single {
            Room.databaseBuilder(
                get(),
                M3UDatabase::class.java,
                "m3u-database"
            )
                .addMigrations(M3UDatabase.MIGRATION_1_2)
                .build()
        }

        single {
            get<M3UDatabase>().liveDao()
        }

        single {
            get<M3UDatabase>().feedDao()
        }

        single {
            get<M3UDatabase>().postDao()
        }

        factoryOf(::AndroidUdpDiscover) bind UdpDiscover::class
        singleOf(::AndroidPlayerManager) bind PlayerManager::class
        singleOf(::AndroidNotificationService) bind NotificationService::class

        singleOf(::FeedRepositoryImpl) bind FeedRepository::class
        singleOf(::MediaRepositoryImpl) bind MediaRepository::class
        singleOf(::RemoteRepositoryImpl) bind RemoteRepository::class
        singleOf(::LiveRepositoryImpl) bind LiveRepository::class

        single {
            NotificationManagerCompat.from(get())
        }

        single {
            val context: Context = get()
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
    }

    val SharedPlatform = module {
        factory {
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }
        }

        single {
            if (!BuildConfig.DEBUG) Proxy.NO_PROXY
            // AVD special alias to your host loopback interface (127.0.0.1 on your development machine)
            // https://developer.android.com/studio/run/emulator-networking
            else Proxy(Proxy.Type.HTTP, InetSocketAddress("10.0.2.2", 7890))
        }

        single {
            val proxy: Proxy = get()
            val json: Json = get()
            val mediaType = "application/json".toMediaType()
            Retrofit.Builder()
                .client(
                    OkHttpClient.Builder()
                        .proxy(proxy)
                        .build()
                )
                .addConverterFactory(json.asConverterFactory(mediaType))
        }

        factory {
            val builder: Retrofit.Builder = get()
            builder
                .baseUrl("https://api.github.com")
                .build()
                .create(RemoteApi::class.java)
        }
        factoryOf(::DefaultPlaylistParser) bind PlaylistParser::class
        singleOf(::ConflatedUserInterface) bind UserInterface::class
        singleOf(::PostRepositoryImpl) bind PostRepository::class
    }
}
package com.m3u.data.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "provider_accounts",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["url"],
            childColumns = ["playlist_url"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["playlist_url"], unique = true),
        Index(value = ["provider_id", "server_id", "user_id"], unique = true),
    ],
)
@Serializable
data class ProviderAccount(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "provider_id")
    val providerId: String,
    @ColumnInfo(name = "provider_kind")
    val providerKind: String,
    @ColumnInfo(name = "base_url")
    val baseUrl: String,
    @ColumnInfo(name = "server_id")
    val serverId: String,
    @ColumnInfo(name = "server_name")
    val serverName: String,
    @ColumnInfo(name = "server_version")
    val serverVersion: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "username")
    val username: String,
    @ColumnInfo(name = "playlist_url")
    val playlistUrl: String,
    @ColumnInfo(name = "requires_reauthentication", defaultValue = "0")
    val requiresReauthentication: Boolean = false,
    @ColumnInfo(name = "owner_package_name")
    val ownerPackageName: String? = null,
    @ColumnInfo(name = "owner_service_name")
    val ownerServiceName: String? = null,
    @ColumnInfo(name = "owner_certificate_sha256")
    val ownerCertificateSha256: String? = null,
)

data class ProviderAccountSummaryRow(
    @Embedded
    val account: ProviderAccount,
    @ColumnInfo(name = "playlist_title")
    val playlistTitle: String,
)

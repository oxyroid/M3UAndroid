package com.m3u.data.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "provider_credentials",
    foreignKeys = [
        ForeignKey(
            entity = ProviderAccount::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
)
data class ProviderCredentialEntity(
    @PrimaryKey
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "credential_handle")
    val credentialHandle: String,
    @ColumnInfo(name = "ciphertext")
    val ciphertext: String,
    @ColumnInfo(name = "nonce")
    val nonce: String,
    @ColumnInfo(name = "key_version")
    val keyVersion: Int,
)

package com.logicart.bari.core.storage

import android.content.Intent
import android.net.Uri

interface StorageRepository {
    fun accessSnapshot(
        denied: Boolean = false,
        unavailable: Boolean = false,
        unsupported: Boolean = false,
    ): StorageAccessSnapshot

    fun manageAccessSettingsIntent(): Intent

    fun generalManageAccessSettingsIntent(): Intent

    fun selectTree(uri: Uri, resultFlags: Int): StorageLocation

    suspend fun listChildren(location: StorageLocation): List<FileReference>

    suspend fun search(location: StorageLocation, query: String): SearchResponse

    fun openIntent(file: FileReference): Intent

    fun shareIntent(file: FileReference): Intent
}


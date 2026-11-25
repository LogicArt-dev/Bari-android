package com.logicart.bari.core.storage

object StorageAuthorization {
    fun resolve(
        sdkInt: Int,
        manageAllFiles: Boolean,
        legacyRead: Boolean,
        hasSavedTree: Boolean,
        treeReadable: Boolean,
        denied: Boolean = false,
        unavailable: Boolean = false,
        unsupported: Boolean = false,
    ): StorageAccessState {
        if (sdkInt >= 30 && manageAllFiles) return StorageAccessState.GRANTED_BROAD
        if (sdkInt <= 28 && legacyRead) return StorageAccessState.GRANTED_BROAD
        if (treeReadable) return StorageAccessState.GRANTED_SAF
        if (unsupported) return StorageAccessState.UNSUPPORTED
        if (unavailable) return StorageAccessState.UNAVAILABLE
        if (hasSavedTree) return StorageAccessState.REVOKED
        if (denied) return StorageAccessState.DENIED
        return when {
            sdkInt >= 30 -> StorageAccessState.NEEDS_MANAGE_ACCESS
            sdkInt == 29 -> StorageAccessState.NEEDS_SAF_TREE
            else -> StorageAccessState.NEEDS_LEGACY_PERMISSION
        }
    }

    fun supportsManageAllFiles(sdkInt: Int): Boolean = sdkInt >= 30

    fun capabilities(
        state: StorageAccessState,
        isDirectory: Boolean,
        providerCanWrite: Boolean = false,
        providerCanCreateChildren: Boolean = false,
        supportsThumbnails: Boolean = false,
    ): StorageCapabilities {
        val readable = state == StorageAccessState.GRANTED_BROAD ||
            state == StorageAccessState.GRANTED_SAF
        return StorageCapabilities(
            canListChildren = readable && isDirectory,
            canRead = readable,
            canOpen = readable && !isDirectory,
            canShare = readable && !isDirectory,
            canWrite = readable && providerCanWrite,
            canCreateChildren = readable && isDirectory && providerCanCreateChildren,
            supportsThumbnails = readable && supportsThumbnails,
        )
    }
}


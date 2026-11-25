package com.logicart.bari.core.storage

import java.io.IOException
import java.util.Locale

enum class StorageSource {
    BROAD_FILESYSTEM,
    SAF_TREE,
}

enum class StorageAccessState {
    GRANTED_BROAD,
    GRANTED_SAF,
    NEEDS_MANAGE_ACCESS,
    NEEDS_LEGACY_PERMISSION,
    NEEDS_SAF_TREE,
    DENIED,
    REVOKED,
    UNAVAILABLE,
    UNSUPPORTED,
}

data class StorageCapabilities(
    val canListChildren: Boolean,
    val canRead: Boolean,
    val canOpen: Boolean,
    val canShare: Boolean,
    val canWrite: Boolean,
    val canCreateChildren: Boolean = false,
    val supportsThumbnails: Boolean = false,
)

data class FileMetadata(
    val sizeBytes: Long? = null,
    val modifiedAtMillis: Long? = null,
    val mimeType: String? = null,
    val extension: String? = null,
)

data class StorageLocation(
    val source: StorageSource,
    val uri: String,
    val path: String,
    val displayName: String,
    val parentUri: String? = null,
    val treeUri: String? = null,
)

data class FileReference(
    val id: String,
    val source: StorageSource,
    val name: String,
    val uri: String,
    val path: String,
    val isDirectory: Boolean,
    val metadata: FileMetadata,
    val capabilities: StorageCapabilities,
    val parentUri: String,
    val treeUri: String? = null,
) {
    fun asLocation(): StorageLocation = StorageLocation(
        source = source,
        uri = uri,
        path = path,
        displayName = name,
        parentUri = parentUri,
        treeUri = treeUri,
    )
}

data class StorageAccessSnapshot(
    val state: StorageAccessState,
    val location: StorageLocation? = null,
    val broadAccessSupported: Boolean,
    val safSupported: Boolean = true,
)

enum class StorageErrorKind {
    ACCESS_REVOKED,
    INACCESSIBLE,
    UNAVAILABLE,
    UNSUPPORTED_PROVIDER,
    NOT_A_DIRECTORY,
    INVALID_REFERENCE,
    NO_HANDLER,
    UNKNOWN,
}

class StorageException(
    val kind: StorageErrorKind,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

data class SearchResponse(
    val items: List<FileReference>,
    val partialFolderCount: Int = 0,
    val resultLimitReached: Boolean = false,
)

enum class SortOrder {
    NAME,
    TYPE,
    SIZE,
    MODIFIED,
}

object FileSearch {
    fun matches(file: FileReference, query: String): Boolean {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return false
        val fields = listOfNotNull(
            file.name,
            file.path,
            file.metadata.extension,
            file.metadata.mimeType,
            file.metadata.sizeBytes?.toString(),
            file.metadata.modifiedAtMillis?.toString(),
            if (file.isDirectory) "folder directory" else "file",
        )
        return fields.any { it.lowercase(Locale.ROOT).contains(needle) }
    }

    fun isWithinScope(file: FileReference, scope: StorageLocation): Boolean {
        if (file.source != scope.source) return false
        return when (scope.source) {
            StorageSource.BROAD_FILESYSTEM -> {
                val root = scope.path.trimEnd('/')
                file.path == root || file.path.startsWith("$root/")
            }
            StorageSource.SAF_TREE -> {
                file.treeUri == scope.treeUri &&
                    (file.uri == scope.uri || file.path == scope.path ||
                        file.path.startsWith("${scope.path.trimEnd('/')}/"))
            }
        }
    }
}

object FileSorting {
    fun sort(files: List<FileReference>, order: SortOrder): List<FileReference> {
        val comparator = when (order) {
            SortOrder.NAME -> compareBy<FileReference> { it.name.lowercase(Locale.ROOT) }
            SortOrder.TYPE -> compareBy<FileReference> {
                it.metadata.extension.orEmpty().lowercase(Locale.ROOT)
            }.thenBy { it.name.lowercase(Locale.ROOT) }
            SortOrder.SIZE -> compareBy<FileReference> {
                it.metadata.sizeBytes ?: Long.MIN_VALUE
            }.thenBy { it.name.lowercase(Locale.ROOT) }
            SortOrder.MODIFIED -> compareBy<FileReference> {
                it.metadata.modifiedAtMillis ?: Long.MIN_VALUE
            }.thenBy { it.name.lowercase(Locale.ROOT) }
        }
        return files.sortedWith(
            compareBy<FileReference> { !it.isDirectory }
                .then(comparator),
        )
    }
}

data class BrowserPath(val locations: List<StorageLocation> = emptyList()) {
    val current: StorageLocation?
        get() = locations.lastOrNull()

    fun push(location: StorageLocation): BrowserPath =
        copy(locations = locations + location)

    fun parent(): BrowserPath =
        if (locations.size > 1) copy(locations = locations.dropLast(1)) else this
}


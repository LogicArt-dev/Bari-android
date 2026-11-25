package com.logicart.bari.core.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.ArrayDeque
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class AndroidStorageRepository(context: Context) : StorageRepository {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var activeTreeUri: Uri? = null

    override fun accessSnapshot(
        denied: Boolean,
        unavailable: Boolean,
        unsupported: Boolean,
    ): StorageAccessSnapshot {
        val savedTree = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)
        val activeTree = activeTreeUri
        val tree = activeTree ?: savedTree
        val validation = tree?.let { validateTree(it) }
        val treeReadable = validation == TreeValidation.READABLE
        val manageAllFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
        val legacyRead = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        val state = StorageAuthorization.resolve(
            sdkInt = Build.VERSION.SDK_INT,
            manageAllFiles = manageAllFiles,
            legacyRead = legacyRead,
            hasSavedTree = tree != null,
            treeReadable = treeReadable,
            denied = denied,
            unavailable = unavailable || validation == TreeValidation.UNAVAILABLE,
            unsupported = unsupported || validation == TreeValidation.UNSUPPORTED,
        )
        val location = when (state) {
            StorageAccessState.GRANTED_BROAD -> broadRoot()
            StorageAccessState.GRANTED_SAF -> tree?.let(::treeLocation)
            else -> null
        }
        return if ((state == StorageAccessState.GRANTED_BROAD ||
                state == StorageAccessState.GRANTED_SAF) && location == null
        ) {
            StorageAccessSnapshot(
                state = StorageAccessState.UNAVAILABLE,
                broadAccessSupported = StorageAuthorization.supportsManageAllFiles(
                    Build.VERSION.SDK_INT,
                ),
            )
        } else {
            StorageAccessSnapshot(
                state = state,
                location = location,
                broadAccessSupported = StorageAuthorization.supportsManageAllFiles(
                    Build.VERSION.SDK_INT,
                ),
            )
        }
    }

    override fun manageAccessSettingsIntent(): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${appContext.packageName}"),
        )

    override fun generalManageAccessSettingsIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

    override fun selectTree(uri: Uri, resultFlags: Int): StorageLocation {
        if (resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
            throw StorageException(
                StorageErrorKind.INACCESSIBLE,
                "The selected provider did not grant read access.",
            )
        }
        val persistable = resultFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
        var persisted = false
        if (persistable) {
            try {
                resolver.takePersistableUriPermission(
                    uri,
                    resultFlags and
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
                )
                persisted = true
            } catch (_: SecurityException) {
                // The provider may grant a session-only URI even when it cannot persist it.
            }
        }
        activeTreeUri = uri
        if (persisted) {
            preferences.edit().putString(KEY_TREE_URI, uri.toString()).apply()
        } else {
            preferences.edit().remove(KEY_TREE_URI).apply()
        }
        return when (validateTree(uri)) {
            TreeValidation.READABLE -> treeLocation(uri)
            TreeValidation.REVOKED -> throw StorageException(
                StorageErrorKind.ACCESS_REVOKED,
                "The selected folder is no longer available.",
            )
            TreeValidation.UNAVAILABLE -> throw StorageException(
                StorageErrorKind.UNAVAILABLE,
                "The selected storage provider is temporarily unavailable.",
            )
            TreeValidation.UNSUPPORTED -> throw StorageException(
                StorageErrorKind.UNSUPPORTED_PROVIDER,
                "This provider does not expose a readable folder tree.",
            )
        }
    }

    override suspend fun listChildren(location: StorageLocation): List<FileReference> =
        withContext(Dispatchers.IO) {
            when (location.source) {
                StorageSource.BROAD_FILESYSTEM -> listFileChildren(location)
                StorageSource.SAF_TREE -> listSafChildren(location)
            }
        }

    override suspend fun search(location: StorageLocation, query: String): SearchResponse =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchResponse(emptyList())
            val queue = ArrayDeque<StorageLocation>()
            val visited = mutableSetOf<String>()
            val results = ArrayList<FileReference>()
            var partialFolders = 0
            var examined = 0
            var limitReached = false
            queue.add(location)

            while (queue.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                if (examined >= MAX_SEARCH_FOLDERS) {
                    limitReached = true
                    break
                }
                val folder = queue.removeFirst()
                if (!visited.add(folder.uri)) continue
                examined += 1

                val children = try {
                    when (folder.source) {
                        StorageSource.BROAD_FILESYSTEM -> listFileChildren(folder)
                        StorageSource.SAF_TREE -> listSafChildren(folder)
                    }
                } catch (_: SecurityException) {
                    partialFolders += 1
                    continue
                } catch (_: StorageException) {
                    partialFolders += 1
                    continue
                } catch (_: IOException) {
                    partialFolders += 1
                    continue
                }

                for (child in children) {
                    currentCoroutineContext().ensureActive()
                    if (FileSearch.isWithinScope(child, location) &&
                        FileSearch.matches(child, query)
                    ) {
                        if (results.size == MAX_SEARCH_RESULTS) {
                            limitReached = true
                            break
                        }
                        results += child
                    }
                    if (child.isDirectory) queue.addLast(child.asLocation())
                    if (examined % 32 == 0) yield()
                }
                if (limitReached) break
            }
            SearchResponse(
                items = results,
                partialFolderCount = partialFolders,
                resultLimitReached = limitReached,
            )
        }

    override fun openIntent(file: FileReference): Intent {
        val uri = contentUri(file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, file.metadata.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newUri(resolver, file.name, uri)
        if (intent.resolveActivity(appContext.packageManager) == null) {
            throw StorageException(
                StorageErrorKind.NO_HANDLER,
                "No installed app can open this file type.",
            )
        }
        return intent
    }

    override fun shareIntent(file: FileReference): Intent {
        if (!file.capabilities.canShare) {
            throw StorageException(
                StorageErrorKind.INACCESSIBLE,
                "This item cannot be shared with the current access grant.",
            )
        }
        val uri = contentUri(file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(file.metadata.mimeType ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newUri(resolver, file.name, uri)
        if (intent.resolveActivity(appContext.packageManager) == null) {
            throw StorageException(
                StorageErrorKind.NO_HANDLER,
                "No installed app can receive this file.",
            )
        }
        return intent
    }

    private fun broadRoot(): StorageLocation? {
        val root = Environment.getExternalStorageDirectory()
        if (!root.isDirectory || !root.canRead()) return null
        val state = Environment.getExternalStorageState(root)
        if (state != Environment.MEDIA_MOUNTED &&
            state != Environment.MEDIA_MOUNTED_READ_ONLY
        ) {
            return null
        }
        return StorageLocation(
            source = StorageSource.BROAD_FILESYSTEM,
            uri = root.absolutePath,
            path = root.absolutePath,
            displayName = "Internal storage",
        )
    }

    private fun listFileChildren(location: StorageLocation): List<FileReference> {
        val directory = File(location.uri)
        if (!directory.exists()) {
            throw StorageException(
                StorageErrorKind.INACCESSIBLE,
                "This folder is no longer available.",
            )
        }
        if (!directory.isDirectory) {
            throw StorageException(
                StorageErrorKind.NOT_A_DIRECTORY,
                "This location is not a folder.",
            )
        }
        if (!directory.canRead()) {
            throw StorageException(
                StorageErrorKind.INACCESSIBLE,
                "This folder cannot be read with the current access grant.",
            )
        }
        val children = directory.listFiles() ?: throw StorageException(
            StorageErrorKind.INACCESSIBLE,
            "The contents of this folder could not be read.",
        )
        val rootPath = broadRoot()?.path?.let(::canonicalPath)
        return children.mapNotNull { child ->
            val canonical = runCatching { child.canonicalPath }.getOrNull()
                ?: return@mapNotNull null
            if (rootPath != null && canonical != rootPath &&
                !canonical.startsWith("$rootPath/")
            ) {
                return@mapNotNull null
            }
            fileReference(child, location)
        }
    }

    private fun fileReference(file: File, parent: StorageLocation): FileReference {
        val extension = file.extension.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)
        val mimeType = extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        val directory = file.isDirectory
        return FileReference(
            id = "file:${file.absolutePath}",
            source = StorageSource.BROAD_FILESYSTEM,
            name = file.name,
            uri = file.absolutePath,
            path = file.absolutePath,
            isDirectory = directory,
            metadata = FileMetadata(
                sizeBytes = file.takeIf { it.isFile }?.length(),
                modifiedAtMillis = file.lastModified().takeIf { it > 0L },
                mimeType = mimeType,
                extension = extension,
            ),
            capabilities = StorageAuthorization.capabilities(
                StorageAccessState.GRANTED_BROAD,
                directory,
            ),
            parentUri = parent.uri,
        )
    }

    private fun listSafChildren(location: StorageLocation): List<FileReference> {
        val treeUri = location.treeUri?.let(Uri::parse) ?: throw StorageException(
            StorageErrorKind.INVALID_REFERENCE,
            "The selected folder reference is incomplete.",
        )
        val documentId = try {
            DocumentsContract.getDocumentId(Uri.parse(location.uri))
        } catch (error: IllegalArgumentException) {
            throw StorageException(
                StorageErrorKind.INVALID_REFERENCE,
                "The selected document reference is invalid.",
                error,
            )
        }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
        val cursor = try {
            resolver.query(childrenUri, projection, null, null, null)
        } catch (error: SecurityException) {
            throw StorageException(
                StorageErrorKind.ACCESS_REVOKED,
                "Access to this provider was revoked.",
                error,
            )
        } catch (error: UnsupportedOperationException) {
            throw StorageException(
                StorageErrorKind.UNSUPPORTED_PROVIDER,
                "This provider does not support folder browsing.",
                error,
            )
        } ?: throw StorageException(
            StorageErrorKind.UNAVAILABLE,
            "The provider did not return folder contents.",
        )

        return cursor.use { result ->
            val idColumn = result.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = result.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = result.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0) {
                throw StorageException(
                    StorageErrorKind.UNSUPPORTED_PROVIDER,
                    "This provider does not expose required file metadata.",
                )
            }
            buildList {
                while (result.moveToNext()) {
                    val childId = result.getString(idColumn)
                    val name = result.getString(nameColumn)
                    val mime = result.getString(mimeColumn)
                    if (childId.isNullOrBlank() || name.isNullOrBlank() || mime.isNullOrBlank()) {
                        continue
                    }
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    val directory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val flagsColumn =
                        result.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                    val flags = if (flagsColumn >= 0 && !result.isNull(flagsColumn)) {
                        result.getInt(flagsColumn)
                    } else {
                        0
                    }
                    val extension = if (directory) null else
                        name.substringAfterLast('.', "").takeIf { it.isNotBlank() }
                            ?.lowercase(Locale.ROOT)
                    val capabilities = StorageAuthorization.capabilities(
                        state = StorageAccessState.GRANTED_SAF,
                        isDirectory = directory,
                        providerCanWrite = flags and
                            DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0,
                        providerCanCreateChildren = flags and
                            DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0,
                        supportsThumbnails = flags and
                            DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL != 0,
                    )
                    add(
                        FileReference(
                            id = "saf:${treeUri}/$childId",
                            source = StorageSource.SAF_TREE,
                            name = name,
                            uri = childUri.toString(),
                            path = "${location.path.trimEnd('/')}/$name",
                            isDirectory = directory,
                            metadata = FileMetadata(
                                sizeBytes = result.longOrNull(
                                    DocumentsContract.Document.COLUMN_SIZE,
                                ),
                                modifiedAtMillis = result.longOrNull(
                                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                                ),
                                mimeType = mime.takeUnless { directory },
                                extension = extension,
                            ),
                            capabilities = capabilities,
                            parentUri = location.uri,
                            treeUri = treeUri.toString(),
                        ),
                    )
                }
            }
        }
    }

    private fun treeLocation(treeUri: Uri): StorageLocation? {
        val info = treeMetadata(treeUri) ?: return null
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            info.documentId,
        )
        return StorageLocation(
            source = StorageSource.SAF_TREE,
            uri = documentUri.toString(),
            path = info.name,
            displayName = info.name,
            treeUri = treeUri.toString(),
        )
    }

    private fun validateTree(treeUri: Uri): TreeValidation = try {
        when {
            !hasReadGrant(treeUri) -> TreeValidation.REVOKED
            treeMetadata(treeUri) != null -> TreeValidation.READABLE
            else -> TreeValidation.UNAVAILABLE
        }
    } catch (_: SecurityException) {
        TreeValidation.REVOKED
    } catch (_: IllegalArgumentException) {
        TreeValidation.UNSUPPORTED
    } catch (_: UnsupportedOperationException) {
        TreeValidation.UNSUPPORTED
    } catch (_: FileNotFoundException) {
        TreeValidation.REVOKED
    }

    private fun hasReadGrant(uri: Uri): Boolean {
        if (activeTreeUri == uri) return true
        return resolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    private fun treeMetadata(treeUri: Uri): TreeMetadata? {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val cursor = resolver.query(documentUri, projection, null, null, null) ?: return null
        return cursor.use { result ->
            val idColumn = result.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = result.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = result.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0 || !result.moveToFirst()) {
                return@use null
            }
            if (result.getString(mimeColumn) != DocumentsContract.Document.MIME_TYPE_DIR) {
                return@use null
            }
            val id = result.getString(idColumn) ?: return@use null
            val name = result.getString(nameColumn)?.takeIf { it.isNotBlank() }
                ?: "Selected folder"
            TreeMetadata(id, name)
        }
    }

    private fun contentUri(file: FileReference): Uri = when (file.source) {
        StorageSource.BROAD_FILESYSTEM -> try {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                File(file.uri),
            )
        } catch (error: IllegalArgumentException) {
            throw StorageException(
                StorageErrorKind.INVALID_REFERENCE,
                "This file is outside the accessible shared-storage area.",
                error,
            )
        }
        StorageSource.SAF_TREE -> Uri.parse(file.uri)
    }

    private fun canonicalPath(path: String): String = try {
        File(path).canonicalPath
    } catch (_: IOException) {
        path
    } catch (_: SecurityException) {
        path
    }

    private enum class TreeValidation {
        READABLE,
        REVOKED,
        UNAVAILABLE,
        UNSUPPORTED,
    }

    private fun Cursor.longOrNull(columnName: String): Long? {
        val column = getColumnIndex(columnName)
        return if (column < 0 || isNull(column)) null else getLong(column)
    }

    private data class TreeMetadata(val documentId: String, val name: String)

    companion object {
        const val MAX_SEARCH_RESULTS = 200
        const val MAX_SEARCH_FOLDERS = 10_000
        private const val PREFERENCES_NAME = "storage_access"
        private const val KEY_TREE_URI = "selected_tree_uri"
    }
}


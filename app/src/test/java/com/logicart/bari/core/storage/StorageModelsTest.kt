package com.logicart.bari.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageModelsTest {
    @Test
    fun authorizationUsesVersionAppropriateRoutesAndRevocation() {
        assertEquals(
            StorageAccessState.NEEDS_MANAGE_ACCESS,
            StorageAuthorization.resolve(
                sdkInt = 36,
                manageAllFiles = false,
                legacyRead = false,
                hasSavedTree = false,
                treeReadable = false,
            ),
        )
        assertEquals(
            StorageAccessState.NEEDS_SAF_TREE,
            StorageAuthorization.resolve(
                sdkInt = 29,
                manageAllFiles = false,
                legacyRead = false,
                hasSavedTree = false,
                treeReadable = false,
            ),
        )
        assertEquals(
            StorageAccessState.GRANTED_SAF,
            StorageAuthorization.resolve(
                sdkInt = 36,
                manageAllFiles = false,
                legacyRead = false,
                hasSavedTree = true,
                treeReadable = true,
            ),
        )
        assertEquals(
            StorageAccessState.REVOKED,
            StorageAuthorization.resolve(
                sdkInt = 36,
                manageAllFiles = false,
                legacyRead = false,
                hasSavedTree = true,
                treeReadable = false,
            ),
        )
        assertEquals(
            StorageAccessState.GRANTED_BROAD,
            StorageAuthorization.resolve(
                sdkInt = 36,
                manageAllFiles = true,
                legacyRead = false,
                hasSavedTree = false,
                treeReadable = false,
                unavailable = true,
            ),
        )
    }

    @Test
    fun capabilitiesNeverGrantWritesWithoutProviderSupport() {
        val readOnly = StorageAuthorization.capabilities(
            state = StorageAccessState.GRANTED_SAF,
            isDirectory = true,
            providerCanWrite = false,
            providerCanCreateChildren = false,
        )
        assertTrue(readOnly.canListChildren)
        assertTrue(readOnly.canRead)
        assertFalse(readOnly.canWrite)
        assertFalse(readOnly.canCreateChildren)

        val denied = StorageAuthorization.capabilities(
            state = StorageAccessState.REVOKED,
            isDirectory = false,
            providerCanWrite = true,
        )
        assertFalse(denied.canRead)
        assertFalse(denied.canOpen)
        assertFalse(denied.canShare)
        assertFalse(denied.canWrite)
    }

    @Test
    fun fileMetadataAndReferencesRetainSourceAndNullableMetadata() {
        val file = FileReference(
            id = "saf:tree/document",
            source = StorageSource.SAF_TREE,
            name = "report.pdf",
            uri = "content://provider/document/2",
            path = "Work/report.pdf",
            isDirectory = false,
            metadata = FileMetadata(mimeType = "application/pdf", extension = "pdf"),
            capabilities = StorageCapabilities(
                canListChildren = false,
                canRead = true,
                canOpen = true,
                canShare = true,
                canWrite = false,
            ),
            parentUri = "content://provider/document/1",
            treeUri = "content://provider/tree/1",
        )

        assertEquals(StorageSource.SAF_TREE, file.asLocation().source)
        assertEquals("report.pdf", file.asLocation().displayName)
        assertEquals("pdf", file.metadata.extension)
        assertEquals(null, file.metadata.sizeBytes)
        assertEquals("content://provider/tree/1", file.asLocation().treeUri)
    }

    @Test
    fun searchMatchesNamesFoldersPathsExtensionsAndMetadataWithinScope() {
        val scope = StorageLocation(
            source = StorageSource.BROAD_FILESYSTEM,
            uri = "/storage/emulated/0/Documents",
            path = "/storage/emulated/0/Documents",
            displayName = "Documents",
        )
        val inside = file(
            name = "Invoice.PDF",
            path = "/storage/emulated/0/Documents/2026/Invoice.PDF",
            extension = "pdf",
            mimeType = "application/pdf",
        )
        val outside = file(
            name = "Invoice.pdf",
            path = "/storage/emulated/0/Download/Invoice.pdf",
            extension = "pdf",
        )

        assertTrue(FileSearch.matches(inside, "invoice"))
        assertTrue(FileSearch.matches(inside, "documents/2026"))
        assertTrue(FileSearch.matches(inside, ".PDF"))
        assertTrue(FileSearch.matches(inside, "application/pdf"))
        assertFalse(FileSearch.matches(inside, "content of invoice"))
        assertTrue(FileSearch.isWithinScope(inside, scope))
        assertFalse(FileSearch.isWithinScope(outside, scope))
        assertFalse(FileSearch.matches(inside, "  "))
    }

    @Test
    fun safSearchScopeRequiresSameTreeGrant() {
        val scope = StorageLocation(
            source = StorageSource.SAF_TREE,
            uri = "content://provider/document/root",
            path = "Root",
            displayName = "Root",
            treeUri = "content://provider/tree/root",
        )
        val child = file(
            source = StorageSource.SAF_TREE,
            name = "photo.jpg",
            path = "Root/Photos/photo.jpg",
            uri = "content://provider/document/photo",
            treeUri = "content://provider/tree/root",
            extension = "jpg",
        )
        val differentTree = child.copy(treeUri = "content://provider/tree/other")

        assertTrue(FileSearch.isWithinScope(child, scope))
        assertFalse(FileSearch.isWithinScope(differentTree, scope))
    }

    @Test
    fun sortingKeepsFoldersFirstAndUnknownMetadataLast() {
        val files = listOf(
            file(name = "large.bin", size = 900),
            file(name = "a-folder", directory = true),
            file(name = "unknown.bin", size = null),
            file(name = "small.bin", size = 12),
        )

        val sorted = FileSorting.sort(files, SortOrder.SIZE)

        assertEquals(
            listOf("a-folder", "small.bin", "large.bin", "unknown.bin"),
            sorted.map { it.name },
        )
        assertEquals("a-folder", FileSorting.sort(files, SortOrder.NAME).first().name)
    }

    @Test
    fun browserPathPushesAndReturnsToParentWithoutMutatingPriorState() {
        val root = StorageLocation(
            source = StorageSource.BROAD_FILESYSTEM,
            uri = "/storage/emulated/0",
            path = "/storage/emulated/0",
            displayName = "Internal storage",
        )
        val child = root.copy(uri = "/storage/emulated/0/Documents", displayName = "Documents")
        val rootPath = BrowserPath().push(root)
        val nestedPath = rootPath.push(child)

        assertEquals("Internal storage", rootPath.current?.displayName)
        assertEquals("Documents", nestedPath.current?.displayName)
        assertEquals(rootPath, nestedPath.parent())
        assertEquals(rootPath, rootPath.parent())
    }

    private fun file(
        name: String,
        path: String = "/storage/emulated/0/$name",
        source: StorageSource = StorageSource.BROAD_FILESYSTEM,
        uri: String = path,
        treeUri: String? = null,
        extension: String? = null,
        mimeType: String? = null,
        size: Long? = 10,
        directory: Boolean = false,
    ) = FileReference(
        id = uri,
        source = source,
        name = name,
        uri = uri,
        path = path,
        isDirectory = directory,
        metadata = FileMetadata(
            sizeBytes = size,
            mimeType = mimeType,
            extension = extension,
        ),
        capabilities = StorageCapabilities(
            canListChildren = directory,
            canRead = true,
            canOpen = !directory,
            canShare = !directory,
            canWrite = false,
        ),
        parentUri = "/storage/emulated/0",
        treeUri = treeUri,
    )
}

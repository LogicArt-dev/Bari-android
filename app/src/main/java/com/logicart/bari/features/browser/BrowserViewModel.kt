package com.logicart.bari.features.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import com.logicart.bari.core.storage.BrowserPath
import com.logicart.bari.core.storage.FileReference
import com.logicart.bari.core.storage.FileSearch
import com.logicart.bari.core.storage.FileSorting
import com.logicart.bari.core.storage.SearchResponse
import com.logicart.bari.core.storage.SortOrder
import com.logicart.bari.core.storage.StorageAccessState
import com.logicart.bari.core.storage.StorageErrorKind
import com.logicart.bari.core.storage.StorageException
import com.logicart.bari.core.storage.StorageLocation
import com.logicart.bari.core.storage.StorageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BrowserLayout {
    LIST,
    GRID,
}

data class BrowserUiState(
    val path: BrowserPath = BrowserPath(),
    val items: List<FileReference> = emptyList(),
    val sortOrder: SortOrder = SortOrder.NAME,
    val layout: BrowserLayout = BrowserLayout.LIST,
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val searchMode: Boolean = false,
    val searchQuery: String = "",
    val searchScopeName: String? = null,
    val partialFolderCount: Int = 0,
    val resultLimitReached: Boolean = false,
    val error: StorageErrorKind? = null,
    val accessLost: Boolean = false,
)

class BrowserViewModel(
    private val repository: StorageRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = mutableState.asStateFlow()
    private var activeJob: Job? = null

    fun openRoot(location: StorageLocation) {
        if (mutableState.value.path.locations.firstOrNull()?.uri == location.uri) {
            reload()
            return
        }
        mutableState.value = mutableState.value.copy(
            path = BrowserPath(listOf(location)),
            searchMode = false,
            searchQuery = "",
            searchScopeName = null,
            error = null,
            accessLost = false,
        )
        load(location)
    }

    fun openFolder(file: FileReference) {
        if (!file.isDirectory || !file.capabilities.canListChildren) return
        activeJob?.cancel()
        val path = mutableState.value.path.push(file.asLocation())
        mutableState.value = mutableState.value.copy(
            path = path,
            searchMode = false,
            searchQuery = "",
            searchScopeName = null,
            error = null,
        )
        path.current?.let(::load)
    }

    fun navigateToBreadcrumb(index: Int) {
        val locations = mutableState.value.path.locations
        if (index !in locations.indices || index == locations.lastIndex) return
        activeJob?.cancel()
        val path = BrowserPath(locations.take(index + 1))
        mutableState.value = mutableState.value.copy(
            path = path,
            searchMode = false,
            searchQuery = "",
            searchScopeName = null,
            error = null,
        )
        path.current?.let(::load)
    }

    fun navigateParent(): Boolean {
        val path = mutableState.value.path
        if (path.locations.size <= 1) return false
        activeJob?.cancel()
        val parent = path.parent()
        mutableState.value = mutableState.value.copy(
            path = parent,
            searchMode = false,
            searchQuery = "",
            searchScopeName = null,
            error = null,
        )
        parent.current?.let(::load)
        return true
    }

    fun reload() {
        mutableState.value.path.current?.let(::load)
    }

    fun setLayout(layout: BrowserLayout) {
        mutableState.value = mutableState.value.copy(layout = layout)
    }

    fun setSortOrder(order: SortOrder) {
        val current = mutableState.value
        mutableState.value = current.copy(
            sortOrder = order,
            items = FileSorting.sort(current.items, order),
        )
    }

    fun updateSearchQuery(query: String) {
        mutableState.value = mutableState.value.copy(searchQuery = query)
    }

    fun openIntent(file: FileReference): Intent = repository.openIntent(file)

    fun shareIntent(file: FileReference): Intent = repository.shareIntent(file)

    fun search() {
        val current = mutableState.value
        val scope = current.path.current ?: return
        val query = current.searchQuery.trim()
        activeJob?.cancel()
        if (query.isEmpty()) {
            clearSearch()
            return
        }
        mutableState.value = current.copy(
            searchMode = true,
            isSearching = true,
            searchScopeName = scope.displayName,
            error = null,
            partialFolderCount = 0,
            resultLimitReached = false,
        )
        activeJob = viewModelScope.launch {
            try {
                val result: SearchResponse = repository.search(scope, query)
                mutableState.value = mutableState.value.copy(
                    items = FileSorting.sort(result.items, mutableState.value.sortOrder),
                    isSearching = false,
                    partialFolderCount = result.partialFolderCount,
                    resultLimitReached = result.resultLimitReached,
                    error = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: StorageException) {
                mutableState.value = mutableState.value.copy(
                    items = emptyList(),
                    isSearching = false,
                    error = error.kind,
                )
            } catch (error: SecurityException) {
                mutableState.value = mutableState.value.copy(
                    items = emptyList(),
                    isSearching = false,
                    accessLost = true,
                    error = StorageErrorKind.ACCESS_REVOKED,
                )
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    items = emptyList(),
                    isSearching = false,
                    error = StorageErrorKind.UNKNOWN,
                )
            }
        }
    }

    fun clearSearch() {
        activeJob?.cancel()
        val current = mutableState.value
        mutableState.value = current.copy(
            searchMode = false,
            isSearching = false,
            searchQuery = "",
            searchScopeName = null,
            partialFolderCount = 0,
            resultLimitReached = false,
            error = null,
        )
        current.path.current?.let(::load)
    }

    private fun load(location: StorageLocation) {
        activeJob?.cancel()
        mutableState.value = mutableState.value.copy(
            items = emptyList(),
            isLoading = true,
            error = null,
            accessLost = false,
        )
        activeJob = viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    repository.listChildren(location)
                }
                mutableState.value = mutableState.value.copy(
                    items = FileSorting.sort(items, mutableState.value.sortOrder),
                    isLoading = false,
                    error = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: StorageException) {
                mutableState.value = mutableState.value.copy(
                    items = emptyList(),
                    isLoading = false,
                    error = error.kind,
                    accessLost = error.kind == StorageErrorKind.ACCESS_REVOKED,
                )
            } catch (error: SecurityException) {
                mutableState.value = mutableState.value.copy(
                    items = emptyList(),
                    isLoading = false,
                    error = StorageErrorKind.ACCESS_REVOKED,
                    accessLost = true,
                )
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    items = emptyList(),
                    isLoading = false,
                    error = StorageErrorKind.UNKNOWN,
                )
            }
        }
    }
}


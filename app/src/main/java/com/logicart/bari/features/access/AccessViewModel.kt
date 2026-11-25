package com.logicart.bari.features.access

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logicart.bari.core.storage.AndroidStorageRepository
import com.logicart.bari.core.storage.StorageAccessSnapshot
import com.logicart.bari.core.storage.StorageAccessState
import com.logicart.bari.core.storage.StorageErrorKind
import com.logicart.bari.core.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AccessUiState(
    val isLoading: Boolean = true,
    val snapshot: StorageAccessSnapshot? = null,
    val error: StorageErrorKind? = null,
    val denied: Boolean = false,
    val unavailable: Boolean = false,
    val unsupported: Boolean = false,
)

class AccessViewModel(
    private val repository: AndroidStorageRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccessUiState())
    val state: StateFlow<AccessUiState> = mutableState.asStateFlow()

    fun manageAccessSettingsIntent() = repository.manageAccessSettingsIntent()

    fun generalManageAccessSettingsIntent() = repository.generalManageAccessSettingsIntent()

    fun refresh() {
        val previous = mutableState.value
        viewModelScope.launch {
            mutableState.value = previous.copy(isLoading = true)
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    repository.accessSnapshot(
                        denied = previous.denied,
                        unavailable = previous.unavailable,
                        unsupported = previous.unsupported,
                    )
                }
                mutableState.value = previous.copy(
                    isLoading = false,
                    snapshot = snapshot,
                    error = null,
                    unavailable = snapshot.state == StorageAccessState.UNAVAILABLE,
                    unsupported = snapshot.state == StorageAccessState.UNSUPPORTED,
                )
            } catch (error: SecurityException) {
                mutableState.value = previous.copy(
                    isLoading = false,
                    snapshot = StorageAccessSnapshot(
                        state = StorageAccessState.REVOKED,
                        broadAccessSupported = false,
                    ),
                    error = StorageErrorKind.ACCESS_REVOKED,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = previous.copy(
                    isLoading = false,
                    snapshot = StorageAccessSnapshot(
                        state = StorageAccessState.UNAVAILABLE,
                        broadAccessSupported = false,
                    ),
                    error = StorageErrorKind.UNAVAILABLE,
                    unavailable = true,
                )
            }
        }
    }

    fun markDenied() {
        mutableState.value = mutableState.value.copy(denied = true)
        refresh()
    }

    fun clearDenial() {
        mutableState.value = mutableState.value.copy(
            denied = false,
            unavailable = false,
            unsupported = false,
        )
    }

    fun markUnavailable() {
        mutableState.value = mutableState.value.copy(unavailable = true)
        refresh()
    }

    fun markUnsupported() {
        mutableState.value = mutableState.value.copy(unsupported = true)
        refresh()
    }

    fun acceptTree(uri: Uri, resultFlags: Int) {
        clearDenial()
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, error = null)
            try {
                val location = withContext(Dispatchers.IO) {
                    repository.selectTree(uri, resultFlags)
                }
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    snapshot = StorageAccessSnapshot(
                        state = StorageAccessState.GRANTED_SAF,
                        location = location,
                        broadAccessSupported = repository.accessSnapshot().broadAccessSupported,
                    ),
                )
            } catch (error: StorageException) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = error.kind,
                )
            } catch (error: SecurityException) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = StorageErrorKind.ACCESS_REVOKED,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = StorageErrorKind.UNKNOWN,
                )
            }
        }
    }
}


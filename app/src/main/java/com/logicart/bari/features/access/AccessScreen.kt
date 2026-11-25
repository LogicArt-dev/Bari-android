package com.logicart.bari.features.access

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logicart.bari.R
import com.logicart.bari.core.storage.StorageAccessState

@Composable
fun AccessScreen(
    viewModel: AccessViewModel,
    onAccessChanged: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val treePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.acceptTree(
                uri = uri,
                resultFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
    }
    val permissionRequester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onAccessChanged() else viewModel.markDenied()
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(56.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Surface(
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {}
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.access_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.access_explanation),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = stringResource(R.string.access_privacy_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (state.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        AccessStatus(state.snapshot?.state, state.error)
                    }

                    val sdk = Build.VERSION.SDK_INT
                    val canRequestPrimary = sdk >= Build.VERSION_CODES.R ||
                        sdk <= Build.VERSION_CODES.P
                    if (canRequestPrimary) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.clearDenial()
                                if (sdk <= Build.VERSION_CODES.P) {
                                    permissionRequester.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                } else {
                                    val activity = context as? Activity
                                    if (activity == null) {
                                        viewModel.markUnavailable()
                                    } else {
                                        try {
                                            activity.startActivity(
                                                viewModel.manageAccessSettingsIntent(),
                                            )
                                        } catch (_: ActivityNotFoundException) {
                                            try {
                                                activity.startActivity(
                                                    viewModel.generalManageAccessSettingsIntent(),
                                                )
                                            } catch (_: ActivityNotFoundException) {
                                                viewModel.markUnavailable()
                                            } catch (_: SecurityException) {
                                                viewModel.markUnavailable()
                                            }
                                        } catch (_: SecurityException) {
                                            viewModel.markUnavailable()
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                text = if (sdk <= Build.VERSION_CODES.P) {
                                    stringResource(R.string.grant_legacy_access)
                                } else {
                                    stringResource(R.string.manage_access)
                                },
                                modifier = Modifier.padding(vertical = 5.dp),
                            )
                        }
                    }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.clearDenial()
                            try {
                                treePicker.launch(null)
                            } catch (_: ActivityNotFoundException) {
                                viewModel.markUnsupported()
                            } catch (_: SecurityException) {
                                viewModel.markUnavailable()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.select_folder),
                            modifier = Modifier.padding(vertical = 5.dp),
                        )
                    }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::refresh,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(stringResource(R.string.check_access_again))
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun AccessStatus(state: StorageAccessState?, error: com.logicart.bari.core.storage.StorageErrorKind?) {
    val text = when {
        error != null -> when (error) {
            com.logicart.bari.core.storage.StorageErrorKind.ACCESS_REVOKED ->
                stringResource(R.string.access_revoked)
            com.logicart.bari.core.storage.StorageErrorKind.UNSUPPORTED_PROVIDER ->
                stringResource(R.string.provider_unsupported)
            com.logicart.bari.core.storage.StorageErrorKind.UNAVAILABLE ->
                stringResource(R.string.storage_unavailable)
            else -> stringResource(R.string.access_try_again)
        }
        state == null -> stringResource(R.string.access_checking)
        else -> when (state) {
            StorageAccessState.GRANTED_BROAD -> stringResource(R.string.access_granted_broad)
            StorageAccessState.GRANTED_SAF -> stringResource(R.string.access_granted_saf)
            StorageAccessState.NEEDS_MANAGE_ACCESS -> stringResource(R.string.access_needs_manage)
            StorageAccessState.NEEDS_LEGACY_PERMISSION ->
                stringResource(R.string.access_needs_legacy)
            StorageAccessState.NEEDS_SAF_TREE -> stringResource(R.string.access_needs_tree)
            StorageAccessState.DENIED -> stringResource(R.string.access_denied)
            StorageAccessState.REVOKED -> stringResource(R.string.access_revoked)
            StorageAccessState.UNAVAILABLE -> stringResource(R.string.storage_unavailable)
            StorageAccessState.UNSUPPORTED -> stringResource(R.string.access_unsupported)
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (state == StorageAccessState.GRANTED_BROAD ||
            state == StorageAccessState.GRANTED_SAF
        ) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        fontWeight = FontWeight.Medium,
    )
}


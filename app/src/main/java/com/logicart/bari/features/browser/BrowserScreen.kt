package com.logicart.bari.features.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logicart.bari.R
import com.logicart.bari.core.storage.FileReference
import com.logicart.bari.core.storage.SortOrder
import com.logicart.bari.core.storage.StorageErrorKind
import com.logicart.bari.core.storage.StorageException
import com.logicart.bari.core.storage.StorageSource
import com.logicart.bari.features.access.AccessViewModel
import java.io.File
import java.io.IOException
import java.util.Date
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BrowserScreen(
    viewModel: BrowserViewModel,
    accessViewModel: AccessViewModel,
    onAccessRevoked: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searchExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accessViewModel.refresh()
        }

        private val thumbnailSemaphore = Semaphore(2)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.accessLost) {
        if (state.accessLost) onAccessRevoked()
    }
    BackHandler(enabled = state.searchMode || state.path.locations.size > 1) {
        if (state.searchMode) viewModel.clearSearch() else viewModel.navigateParent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.path.current?.let {
                                if (state.path.locations.size == 1 &&
                                    it.source == StorageSource.BROAD_FILESYSTEM
                                ) {
                                    stringResource(R.string.storage_root)
                                } else {
                                    it.displayName
                                }
                            } ?: stringResource(R.string.browser_title),
                            maxLines = 1,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (state.searchMode && state.searchScopeName != null) {
                            Text(
                                text = stringResource(
                                    R.string.search_scope,
                                    state.searchScopeName.orEmpty(),
                                ),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (state.path.locations.size > 1 || state.searchMode) {
                        IconButton(
                            onClick = {
                                if (state.searchMode) viewModel.clearSearch()
                                else viewModel.navigateParent()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.browse_back),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { searchExpanded = !searchExpanded }) {
                        Icon(
                            imageVector = if (searchExpanded) Icons.Outlined.Close
                            else Icons.Outlined.Search,
                            contentDescription = stringResource(
                                if (searchExpanded) R.string.clear_search
                                else R.string.search_files,
                            ),
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Sort,
                                contentDescription = stringResource(R.string.sort_by),
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(sortLabel(order)) },
                                    onClick = {
                                        viewModel.setSortOrder(order)
                                        sortMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            viewModel.setLayout(
                                if (state.layout == BrowserLayout.LIST) BrowserLayout.GRID
                                else BrowserLayout.LIST,
                            )
                        },
                    ) {
                        Icon(
                            imageVector = if (state.layout == BrowserLayout.LIST) {
                                Icons.Outlined.GridView
                            } else {
                                Icons.Outlined.ViewList
                            },
                            contentDescription = stringResource(
                                if (state.layout == BrowserLayout.LIST) R.string.layout_grid
                                else R.string.layout_list,
                            ),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (searchExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = state.searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                        shape = RoundedCornerShape(18.dp),
                    )
                    IconButton(
                        onClick = {
                            if (state.searchMode) {
                                viewModel.clearSearch()
                                searchExpanded = false
                            } else {
                                viewModel.search()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (state.searchMode) Icons.Outlined.Close
                            else Icons.Outlined.Search,
                            contentDescription = stringResource(
                                if (state.searchMode) R.string.clear_search
                                else R.string.search_action,
                            ),
                        )
                    }
                }
            }

            if (!state.searchMode && state.path.locations.isNotEmpty()) {
                Breadcrumbs(
                    names = state.path.locations.map { it.displayName },
                    onSelect = viewModel::navigateToBreadcrumb,
                )
            }

            when {
                state.isLoading || state.isSearching -> LoadingState(
                    searching = state.isSearching,
                )
                state.error != null -> ErrorState(
                    kind = state.error ?: StorageErrorKind.UNKNOWN,
                    onRetry = {
                        if (state.searchMode) viewModel.search() else viewModel.reload()
                    },
                )
                state.items.isEmpty() -> EmptyState(searchMode = state.searchMode)
                state.layout == BrowserLayout.LIST -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(
                        items = state.items,
                        key = { _, file -> file.id },
                    ) { _, file ->
                        FileEntry(
                            file = file,
                            grid = false,
                            onClick = {
                                if (file.isDirectory) {
                                    viewModel.openFolder(file)
                                } else {
                                    performFileAction(
                                        context = context,
                                        file = file,
                                        share = false,
                                        viewModel = viewModel,
                                        onMessage = { message ->
                                            scope.launch { snackbarHost.showSnackbar(message) }
                                        },
                                    )
                                }
                            },
                            onShare = {
                                performFileAction(
                                    context = context,
                                    file = file,
                                    share = true,
                                    viewModel = viewModel,
                                    onMessage = { message ->
                                        scope.launch { snackbarHost.showSnackbar(message) }
                                    },
                                )
                            },
                        )
                    }
                }
                else -> LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Adaptive(minSize = 152.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = state.items,
                        key = { file -> file.id },
                    ) { file ->
                        FileEntry(
                            file = file,
                            grid = true,
                            onClick = {
                                if (file.isDirectory) {
                                    viewModel.openFolder(file)
                                } else {
                                    performFileAction(
                                        context = context,
                                        file = file,
                                        share = false,
                                        viewModel = viewModel,
                                        onMessage = { message ->
                                            scope.launch { snackbarHost.showSnackbar(message) }
                                        },
                                    )
                                }
                            },
                            onShare = {
                                performFileAction(
                                    context = context,
                                    file = file,
                                    share = true,
                                    viewModel = viewModel,
                                    onMessage = { message ->
                                        scope.launch { snackbarHost.showSnackbar(message) }
                                    },
                                )
                            },
                        )
                    }
                }
            }

            if (!state.isLoading && !state.isSearching && state.error == null) {
                if (state.partialFolderCount > 0) {
                    Text(
                        text = stringResource(R.string.partial_results, state.partialFolderCount),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.resultLimitReached) {
                    Text(
                        text = stringResource(R.string.results_limited),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(
    names: List<String>,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(names) { index, name ->
            TextButton(
                onClick = { onSelect(index) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = name,
                    maxLines = 1,
                    color = if (index == names.lastIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (index == names.lastIndex) FontWeight.SemiBold
                    else FontWeight.Normal,
                )
            }
            if (index < names.lastIndex) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun FileEntry(
    file: FileReference,
    grid: Boolean,
    onClick: () -> Unit,
    onShare: () -> Unit,
) {
    var menuExpanded by remember(file.id) { mutableStateOf(false) }
    val title = file.name.ifBlank { stringResource(R.string.file_type_unknown) }
    if (grid) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.35f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    FileArtwork(
                        file = file,
                        modifier = Modifier.size(54.dp),
                        thumbnailSize = 192,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = metadataLabel(file),
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!file.isDirectory) {
                        ItemActions(
                            expanded = menuExpanded,
                            onExpandedChange = { menuExpanded = it },
                            name = file.name,
                            onShare = onShare,
                        )
                    }
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        text = title,
                        maxLines = 1,
                        fontWeight = FontWeight.Medium,
                    )
                },
                supportingContent = {
                    Text(text = metadataLabel(file), maxLines = 1)
                },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(14.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        FileArtwork(
                            file = file,
                            modifier = Modifier.size(28.dp),
                            thumbnailSize = 96,
                        )
                    }
                },
                trailingContent = {
                    if (!file.isDirectory) {
                        ItemActions(
                            expanded = menuExpanded,
                            onExpandedChange = { menuExpanded = it },
                            name = file.name,
                            onShare = onShare,
                        )
                    }
                },
                colors = androidx.compose.material3.ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}

@Composable
private fun ItemActions(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    name: String,
    onShare: () -> Unit,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.item_menu, name),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Share, contentDescription = null)
                },
                onClick = {
                    onExpandedChange(false)
                    onShare()
                },
            )
        }
    }
}

@Composable
private fun FileArtwork(
    file: FileReference,
    modifier: Modifier,
    thumbnailSize: Int,
) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = file.id,
        key2 = thumbnailSize,
    ) {
        if (file.metadata.mimeType?.startsWith("image/") == true && !file.isDirectory) {
            value = try {
                withContext(Dispatchers.IO) {
                    thumbnailSemaphore.withPermit {
                        loadThumbnail(context, file, thumbnailSize)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        } else {
            value = null
        }
    }
    if (thumbnail != null) {
        Image(
            bitmap = thumbnail!!,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Icon(
            imageVector = if (file.isDirectory) Icons.Outlined.Folder
            else Icons.Outlined.InsertDriveFile,
            contentDescription = null,
            modifier = modifier,
            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary,
        )
    }
}

private suspend fun loadThumbnail(
    context: Context,
    file: FileReference,
    bound: Int,
): ImageBitmap? {
    val uri = when (file.source) {
        StorageSource.BROAD_FILESYSTEM -> Uri.fromFile(File(file.uri))
        StorageSource.SAF_TREE -> Uri.parse(file.uri)
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > bound * 2 ||
        bounds.outHeight / sampleSize > bound * 2
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
    return bitmap?.asImageBitmap()
}

@Composable
private fun LoadingState(searching: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(if (searching) R.string.searching else R.string.loading_files),
            modifier = Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(searchMode: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (searchMode) Icons.Outlined.Search else Icons.Outlined.Folder,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(if (searchMode) R.string.empty_search else R.string.empty_folder),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorState(kind: StorageErrorKind, onRetry: () -> Unit) {
    val text = stringResource(
        when (kind) {
            StorageErrorKind.ACCESS_REVOKED -> R.string.error_access_revoked
            StorageErrorKind.INACCESSIBLE -> R.string.error_inaccessible
            StorageErrorKind.UNAVAILABLE -> R.string.error_unavailable
            StorageErrorKind.UNSUPPORTED_PROVIDER -> R.string.error_unsupported
            StorageErrorKind.NOT_A_DIRECTORY -> R.string.error_not_folder
            StorageErrorKind.INVALID_REFERENCE -> R.string.error_invalid
            StorageErrorKind.NO_HANDLER -> R.string.error_no_handler
            StorageErrorKind.UNKNOWN -> R.string.error_unknown
        },
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
            Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.check_access_again))
        }
    }
}

@Composable
private fun sortLabel(order: SortOrder): String = stringResource(
    when (order) {
        SortOrder.NAME -> R.string.sort_name
        SortOrder.TYPE -> R.string.sort_type
        SortOrder.SIZE -> R.string.sort_size
        SortOrder.MODIFIED -> R.string.sort_modified
    },
)

@Composable
private fun metadataLabel(file: FileReference): String {
    val type = if (file.isDirectory) {
        stringResource(R.string.folder)
    } else {
        file.metadata.extension?.uppercase(Locale.getDefault())
            ?: file.metadata.mimeType?.substringBefore('/')
                ?.uppercase(Locale.getDefault())
            ?: stringResource(R.string.file_type_unknown)
    }
    val size = file.metadata.sizeBytes?.let(::formatFileSize)
    val modified = file.metadata.modifiedAtMillis?.let {
        stringResource(
            R.string.modified_on,
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)),
        )
    }
    return listOfNotNull(type, size, modified).joinToString(" · ")
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    do {
        value /= 1024.0
        unit += 1
    } while (value >= 1024.0 && unit < units.lastIndex)
    return "${NumberFormat.getNumberInstance().format(value)} ${units[unit]}"
}

private fun performFileAction(
    context: Context,
    file: FileReference,
    share: Boolean,
    viewModel: BrowserViewModel,
    onMessage: (String) -> Unit,
) {
    try {
        val intent = if (share) viewModel.shareIntent(file) else viewModel.openIntent(file)
        context.startActivity(
            if (share) Intent.createChooser(
                intent,
                context.getString(R.string.share_title, file.name),
            ) else intent,
        )
    } catch (error: StorageException) {
        onMessage(context.getString(errorString(error.kind)))
    } catch (_: ActivityNotFoundException) {
        onMessage(context.getString(R.string.error_no_handler))
    } catch (_: SecurityException) {
        onMessage(context.getString(R.string.error_access_revoked))
    }
}

private fun errorString(kind: StorageErrorKind): Int = when (kind) {
    StorageErrorKind.ACCESS_REVOKED -> R.string.error_access_revoked
    StorageErrorKind.INACCESSIBLE -> R.string.error_inaccessible
    StorageErrorKind.UNAVAILABLE -> R.string.error_unavailable
    StorageErrorKind.UNSUPPORTED_PROVIDER -> R.string.error_unsupported
    StorageErrorKind.NOT_A_DIRECTORY -> R.string.error_not_folder
    StorageErrorKind.INVALID_REFERENCE -> R.string.error_invalid
    StorageErrorKind.NO_HANDLER -> R.string.error_no_handler
    StorageErrorKind.UNKNOWN -> R.string.error_unknown
}


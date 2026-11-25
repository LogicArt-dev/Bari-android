package com.logicart.bari.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logicart.bari.core.storage.StorageAccessState
import com.logicart.bari.features.access.AccessScreen
import com.logicart.bari.features.access.AccessViewModel
import com.logicart.bari.features.browser.BrowserScreen
import com.logicart.bari.features.browser.BrowserViewModel

private object BariRoute {
    const val Access = "access"
    const val Browser = "browser"
}

@Composable
fun BariNavHost(
    accessViewModel: AccessViewModel,
    browserViewModel: BrowserViewModel,
) {
    val navController = rememberNavController()
    val accessState by accessViewModel.state.collectAsStateWithLifecycle()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    LaunchedEffect(accessState.snapshot?.state, accessState.snapshot?.location, currentRoute) {
        val snapshot = accessState.snapshot ?: return@LaunchedEffect
        val location = snapshot.location
        if (location != null &&
            (snapshot.state == StorageAccessState.GRANTED_BROAD ||
                snapshot.state == StorageAccessState.GRANTED_SAF)
        ) {
            browserViewModel.openRoot(location)
            if (currentRoute != BariRoute.Browser) {
                navController.navigate(BariRoute.Browser) {
                    popUpTo(BariRoute.Access) { inclusive = true }
                    launchSingleTop = true
                }
            }
        } else if (currentRoute == BariRoute.Browser) {
            navController.navigate(BariRoute.Access) {
                popUpTo(BariRoute.Browser) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = BariRoute.Access) {
        composable(BariRoute.Access) {
            AccessScreen(
                viewModel = accessViewModel,
                onAccessChanged = { accessViewModel.refresh() },
            )
        }
        composable(BariRoute.Browser) {
            BrowserScreen(
                viewModel = browserViewModel,
                accessViewModel = accessViewModel,
                onAccessRevoked = {
                    accessViewModel.refresh()
                    navController.navigate(BariRoute.Access) {
                        popUpTo(BariRoute.Browser) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}


package com.rawedit.app.ui.nav

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import com.rawedit.app.ui.theme.MotionSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rawedit.app.data.Prefs
import com.rawedit.app.data.VideoItem
import com.rawedit.app.data.VideoStore
import com.rawedit.app.ui.components.PillNav
import com.rawedit.app.ui.screens.AboutScreen
import com.rawedit.app.ui.screens.CreateScreen
import com.rawedit.app.ui.screens.EditorScreen
import com.rawedit.app.ui.screens.HomeScreen
import com.rawedit.app.ui.screens.LibraryScreen
import com.rawedit.app.ui.screens.ProfileScreen
import com.rawedit.app.ui.screens.RenderScreen
import com.rawedit.app.ui.screens.ResultScreen
import com.rawedit.app.ui.theme.Ink
import com.rawedit.app.viewmodel.CreateViewModel

private const val ROUTE_HOME = "home"
private const val ROUTE_CREATE = "create"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_EDITOR = "editor"
private const val ROUTE_RENDER = "render"
private const val ROUTE_RESULT = "result"

@Composable
fun AppNav() {
    val context = LocalContext.current
    val nav = rememberNavController()
    val prefs = remember { Prefs.get(context) }
    val store = remember { VideoStore.get(context) }
    val vm: CreateViewModel = viewModel()

    var name by remember { mutableStateOf(prefs.name) }
    var photo by remember { mutableStateOf(prefs.photoPath) }
    var cacheMessage by remember { mutableStateOf<String?>(null) }
    val videos by store.items.collectAsState()

    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabIndex = when (currentRoute) {
        ROUTE_CREATE -> 1
        ROUTE_LIBRARY -> 2
        else -> 0
    }
    val showNav = currentRoute == ROUTE_HOME || currentRoute == ROUTE_CREATE || currentRoute == ROUTE_LIBRARY

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Background)
    ) {
        NavHost(
            navController = nav,
            startDestination = ROUTE_HOME,
            modifier = Modifier.fillMaxSize(),
            // Fade only. Scaling a full screen forces an offscreen render of the whole
            // hierarchy every frame, which is what made tab switches feel heavy.
            enterTransition = { fadeIn(tween(MotionSpec.SMALL, easing = MotionSpec.Enter)) },
            exitTransition = { fadeOut(tween(90, easing = MotionSpec.Exit)) },
            popEnterTransition = { fadeIn(tween(MotionSpec.SMALL, easing = MotionSpec.Enter)) },
            popExitTransition = { fadeOut(tween(90, easing = MotionSpec.Exit)) }
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    name = name,
                    photoPath = photo,
                    videoCount = videos.size,
                    onCreate = {
                        if (vm.code.isEmpty() && vm.audioUri == null) vm.loadDraft()
                        nav.navigate(ROUTE_CREATE)
                    },
                    onLibrary = { nav.navigate(ROUTE_LIBRARY) },
                    onAbout = { nav.navigate(ROUTE_ABOUT) },
                    onProfile = { nav.navigate(ROUTE_PROFILE) }
                )
            }

            composable(ROUTE_CREATE) {
                CreateScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onOpenEditor = { nav.navigate(ROUTE_EDITOR) },
                    onStartRender = { nav.navigate(ROUTE_RENDER) }
                )
            }

            composable(ROUTE_EDITOR) {
                EditorScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onDone = { nav.popBackStack() }
                )
            }

            composable(ROUTE_RENDER) {
                RenderScreen(
                    vm = vm,
                    onFinished = {
                        nav.navigate(ROUTE_RESULT) {
                            popUpTo(ROUTE_CREATE) { inclusive = true }
                        }
                    },
                    onExitToCode = {
                        if (nav.currentDestination?.route == ROUTE_RENDER) nav.popBackStack()
                    },
                    onCancel = {
                        if (nav.currentDestination?.route == ROUTE_RENDER) nav.popBackStack()
                    }
                )
            }

            composable(ROUTE_RESULT) {
                val path = vm.lastVideoPath
                if (path == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Text("No video yet.", color = Ink.Muted)
                    }
                } else {
                    ResultScreen(
                        videoPath = path,
                        title = "Your video",
                        style = vm.style?.label ?: "Motion Graphics",
                        durationMs = vm.lastDurationMs,
                        onBack = { nav.popBackStack(ROUTE_HOME, false) },
                        onMakeAnother = {
                            vm.startNewProject()
                            nav.navigate(ROUTE_CREATE) {
                                popUpTo(ROUTE_HOME)
                            }
                        }
                    )
                }
            }

            composable(
                route = "result_item/{path}/{title}/{style}/{dur}",
                arguments = listOf(
                    navArgument("path") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                    navArgument("style") { type = NavType.StringType },
                    navArgument("dur") { type = NavType.LongType }
                )
            ) { entry ->
                val path = entry.arguments?.getString("path").orEmpty()
                ResultScreen(
                    videoPath = path,
                    title = entry.arguments?.getString("title").orEmpty(),
                    style = entry.arguments?.getString("style").orEmpty(),
                    durationMs = entry.arguments?.getLong("dur") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onMakeAnother = {
                        vm.startNewProject()
                        nav.navigate(ROUTE_CREATE) { popUpTo(ROUTE_HOME) }
                    }
                )
            }

            composable(ROUTE_LIBRARY) {
                LibraryScreen(
                    items = videos,
                    onCreate = { nav.navigate(ROUTE_CREATE) },
                    onPlay = { item: VideoItem ->
                        nav.navigate(
                            "result_item/" + Uri.encode(item.filePath) + "/" +
                                Uri.encode(item.title) + "/" +
                                Uri.encode(item.style) + "/" + item.durationMs
                        )
                    },
                    onDelete = { store.delete(it.id) }
                )
            }

            composable(ROUTE_ABOUT) {
                AboutScreen(
                    name = name,
                    cacheMessage = cacheMessage,
                    onBack = { nav.popBackStack() },
                    onProfile = { nav.navigate(ROUTE_PROFILE) },
                    onClearCache = {
                        val freed = prefs.clearCache()
                        cacheMessage = "Cleared " + com.rawedit.app.util.Fmt.bytes(freed)
                    }
                )
            }

            composable(ROUTE_PROFILE) {
                ProfileScreen(
                    prefs = prefs,
                    onBack = { nav.popBackStack() },
                    onSaved = { newName, newPhoto ->
                        name = newName
                        photo = newPhoto
                        nav.popBackStack()
                    }
                )
            }
        }

        if (showNav) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp)
            ) {
                PillNav(
                    current = tabIndex,
                    onSelect = { index ->
                        val target = when (index) {
                            1 -> ROUTE_CREATE
                            2 -> ROUTE_LIBRARY
                            else -> ROUTE_HOME
                        }
                        if (currentRoute != target) {
                            nav.navigate(target) {
                                popUpTo(ROUTE_HOME) { inclusive = target == ROUTE_HOME }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }
    }
}

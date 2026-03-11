package com.nuvio.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.addons.AddonsScreen
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaDetailsScreen
import com.nuvio.app.features.home.HomeScreen
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.streams.StreamsRepository
import com.nuvio.app.features.streams.StreamsScreen
import kotlinx.serialization.Serializable

@Serializable
object TabsRoute

@Serializable
data class DetailRoute(val type: String, val id: String)

@Serializable
data class StreamRoute(
    val type: String,
    val videoId: String,
    val title: String,
    val logo: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
)

enum class AppScreenTab {
    Home,
    Addons,
}

@Composable
fun AppScreen(
    tab: AppScreenTab,
    modifier: Modifier = Modifier,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
) {
    when (tab) {
        AppScreenTab.Home -> HomeScreen(
            modifier = modifier,
            onPosterClick = onPosterClick,
        )
        AppScreenTab.Addons -> AddonsScreen(modifier = modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .crossfade(true)
            .build()
    }
    NuvioTheme {
        val navController = rememberNavController()
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        var selectedTab by rememberSaveable { mutableStateOf(AppScreenTab.Home) }

        // iOS-only: StreamsScreen is presented as a native modal sheet instead of
        // a NavHost destination. On Android this stays null and navController is used.
        var pendingStream by remember { mutableStateOf<StreamRoute?>(null) }

        val onPlay: (String, String, String, String?, String?, String?, Int?, Int?, String?, String?) -> Unit =
            { type, videoId, title, logo, poster, background, seasonNumber, episodeNumber, episodeTitle, episodeThumbnail ->
                val route = StreamRoute(
                    type = type,
                    videoId = videoId,
                    title = title,
                    logo = logo,
                    poster = poster,
                    background = background,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    episodeTitle = episodeTitle,
                    episodeThumbnail = episodeThumbnail,
                )
                if (isIos) {
                    pendingStream = route
                } else {
                    navController.navigate(route)
                }
            }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (currentRoute == TabsRoute::class.qualifiedName) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets(0),
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == AppScreenTab.Home,
                            onClick = { selectedTab = AppScreenTab.Home },
                            icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                            label = { Text("Home") },
                        )
                        NavigationBarItem(
                            selected = selectedTab == AppScreenTab.Addons,
                            onClick = { selectedTab = AppScreenTab.Addons },
                            icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
                            label = { Text("Addons") },
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TabsRoute,
            ) {
                composable<TabsRoute> {
                    AppScreen(
                        tab = selectedTab,
                        modifier = Modifier.padding(innerPadding),
                        onPosterClick = { meta ->
                            navController.navigate(DetailRoute(type = meta.type, id = meta.id))
                        },
                    )
                }
                composable<DetailRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<DetailRoute>()
                    MetaDetailsScreen(
                        type = route.type,
                        id = route.id,
                        onBack = {
                            MetaDetailsRepository.clear()
                            navController.popBackStack()
                        },
                        onPlay = onPlay,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
                // Android only: iOS uses the modal sheet below instead
                composable<StreamRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<StreamRoute>()
                    StreamsScreen(
                        type = route.type,
                        videoId = route.videoId,
                        title = route.title,
                        logo = route.logo,
                        poster = route.poster,
                        background = route.background,
                        seasonNumber = route.seasonNumber,
                        episodeNumber = route.episodeNumber,
                        episodeTitle = route.episodeTitle,
                        episodeThumbnail = route.episodeThumbnail,
                        onBack = {
                            StreamsRepository.clear()
                            navController.popBackStack()
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }

        // iOS native modal sheet presentation for StreamsScreen
        pendingStream?.let { route ->
            ModalBottomSheet(
                onDismissRequest = {
                    StreamsRepository.clear()
                    pendingStream = null
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                dragHandle = null,
                contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top) },
            ) {
                StreamsScreen(
                    type = route.type,
                    videoId = route.videoId,
                    title = route.title,
                    logo = route.logo,
                    poster = route.poster,
                    background = route.background,
                    seasonNumber = route.seasonNumber,
                    episodeNumber = route.episodeNumber,
                    episodeTitle = route.episodeTitle,
                    episodeThumbnail = route.episodeThumbnail,
                    onBack = {
                        StreamsRepository.clear()
                        pendingStream = null
                    },
                )
            }
        }
    }
}

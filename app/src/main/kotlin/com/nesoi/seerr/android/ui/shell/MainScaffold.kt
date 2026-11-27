package com.nesoi.seerr.android.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.media.MediaItem
import com.nesoi.seerr.android.media.MediaList
import com.nesoi.seerr.android.media.TitleType
import com.nesoi.seerr.android.media.detailRoute
import com.nesoi.seerr.android.media.SignedInModel
import com.nesoi.seerr.android.ui.detail.CollectionScreen
import com.nesoi.seerr.android.ui.detail.TitleScreen
import com.nesoi.seerr.android.ui.discover.DiscoverScreen
import com.nesoi.seerr.android.ui.media.MediaGridScreen

/** The signed-in app's tabs, in the order they appear. */
enum class AppTab(val route: String, val label: Int, val icon: ImageVector) {
    Discover("discover", R.string.tab_discover, Icons.Filled.Explore),
    Movies("movies", R.string.tab_movies, Icons.Filled.Movie),
    Series("series", R.string.tab_series, Icons.Filled.Tv),
    Requests("requests", R.string.tab_requests, Icons.Filled.Subscriptions),
    Profile("profile", R.string.tab_profile, Icons.Filled.Person),
}

private const val LIST_ROUTE = "list"
private const val MOVIE_ROUTE = "movie"
private const val TV_ROUTE = "tv"
private const val COLLECTION_ROUTE = "collection"

/** Pages pushed over a tab. They cover the tab bar, as on iOS. */
private val pushedRoutes = listOf(LIST_ROUTE, MOVIE_ROUTE, TV_ROUTE, COLLECTION_ROUTE)

@Composable
fun MainScaffold(user: User, model: SignedInModel, onSignOut: () -> Unit, onChangeServer: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination

    val open: (MediaItem) -> Unit = { item -> item.detailRoute?.let { navController.navigate(it) } }
    val back: () -> Unit = { navController.popBackStack() }
    val settings by model.settings.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (pushedRoutes.none { destination?.route?.startsWith(it) == true }) {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    // Each tab keeps where it was.
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = AppTab.Discover.route, modifier = Modifier.padding(padding)) {
            composable(AppTab.Discover.route) {
                DiscoverScreen(model, onSeeAll = { navController.navigate("$LIST_ROUTE/${it.name}") }, onOpen = open)
            }
            composable(AppTab.Movies.route) { ComingSoon(R.string.tab_movies) }
            composable(AppTab.Series.route) { ComingSoon(R.string.tab_series) }
            composable(AppTab.Requests.route) { ComingSoon(R.string.tab_requests) }
            composable(AppTab.Profile.route) { ProfileScreen(user, onSignOut, onChangeServer) }
            composable("$LIST_ROUTE/{list}") { entry ->
                val list = entry.arguments?.getString("list")?.let { name -> MediaList.entries.firstOrNull { it.name == name } }
                if (list != null) {
                    MediaGridScreen(list, model.grid(list), onBack = back, onOpen = open)
                }
            }
            composable("$MOVIE_ROUTE/{id}", arguments = idArgument) { entry ->
                entry.arguments?.getInt("id")?.let { id ->
                    TitleScreen(model.title(TitleType.Movie, id), model.watchlist, model.canEditWatchlist, user, settings, model::requestModel, back, open) {
                        navController.navigate("$COLLECTION_ROUTE/$it")
                    }
                }
            }
            composable("$TV_ROUTE/{id}", arguments = idArgument) { entry ->
                entry.arguments?.getInt("id")?.let { id ->
                    TitleScreen(model.title(TitleType.Series, id), model.watchlist, model.canEditWatchlist, user, settings, model::requestModel, back, open) {}
                }
            }
            composable("$COLLECTION_ROUTE/{id}", arguments = idArgument) { entry ->
                entry.arguments?.getInt("id")?.let { id -> CollectionScreen(model.collection(id), back, open) }
            }
        }
    }
}

private val idArgument = listOf(navArgument("id") { type = NavType.IntType })

@Composable
private fun ComingSoon(title: Int) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.tab_coming_soon, stringResource(title)),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** For now, just who is signed in and how to leave. The rest of Profile comes in a later phase. */
@Composable
private fun ProfileScreen(user: User, onSignOut: () -> Unit, onChangeServer: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.home_signed_in_as, user.displayNameOrEmail),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onSignOut) { Text(stringResource(R.string.home_sign_out)) }
        OutlinedButton(onClick = onChangeServer) { Text(stringResource(R.string.login_change_server)) }
    }
}

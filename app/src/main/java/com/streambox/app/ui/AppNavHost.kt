package com.streambox.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streambox.app.data.settings.HomeGroupBy
import com.streambox.app.player.ZapContext
import com.streambox.app.ui.groups.GroupDetailScreen
import com.streambox.app.ui.groups.GroupsScreen
import com.streambox.app.ui.phone.AboutScreen
import com.streambox.app.ui.phone.PhoneBrowseScreen
import com.streambox.app.ui.phone.PhoneHomeScreen
import com.streambox.app.ui.phone.PhoneSearchScreen
import com.streambox.app.ui.phone.PhoneSettingsScreen
import com.streambox.app.ui.player.PlayerScreen
import com.streambox.app.ui.tv.TvBrowseScreen
import com.streambox.app.ui.tv.TvHomeScreen
import com.streambox.app.ui.tv.TvSearchScreen
import com.streambox.app.ui.tv.TvSettingsScreen

object Routes {
    const val HOME = "home"
    const val BROWSE = "browse"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val GROUPS = "groups"
    const val GROUP_DETAIL = "group/{type}/{key}"
    const val PLAYER = "player/{channelKey}?ctx={ctx}"

    fun player(channelKey: String, ctx: ZapContext): String =
        "player/$channelKey?ctx=${Uri.encode(ctx.encode())}"

    fun groupDetail(type: HomeGroupBy, key: String): String =
        "group/${type.name}/${Uri.encode(key)}"
}

fun NavHostController.openPlayer(channelKey: String, ctx: ZapContext) {
    navigate(Routes.player(channelKey, ctx))
}

@UnstableApi
@Composable
fun AppNavHost(isTv: Boolean) {
    val navController = rememberNavController()
    val openPlayer: (String, ZapContext) -> Unit = navController::openPlayer

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            if (isTv) {
                TvHomeScreen(
                    onPlayChannel = openPlayer,
                    onOpenBrowse = { navController.navigate(Routes.BROWSE) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenGroups = { navController.navigate(Routes.GROUPS) },
                )
            } else {
                PhoneHomeScreen(
                    onPlayChannel = openPlayer,
                    onOpenBrowse = { navController.navigate(Routes.BROWSE) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenGroups = { navController.navigate(Routes.GROUPS) },
                )
            }
        }
        composable(Routes.GROUPS) {
            GroupsScreen(
                onOpenGroup = { type, key ->
                    navController.navigate(Routes.groupDetail(type, key))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.GROUP_DETAIL,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("key") { type = NavType.StringType },
            ),
        ) {
            GroupDetailScreen(
                onPlayChannel = openPlayer,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.BROWSE) {
            if (isTv) {
                TvBrowseScreen(onPlayChannel = openPlayer)
            } else {
                PhoneBrowseScreen(
                    onPlayChannel = openPlayer,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(Routes.SEARCH) {
            if (isTv) {
                TvSearchScreen(onPlayChannel = openPlayer)
            } else {
                PhoneSearchScreen(
                    onPlayChannel = openPlayer,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(Routes.SETTINGS) {
            if (isTv) {
                TvSettingsScreen(onOpenAbout = { navController.navigate(Routes.ABOUT) })
            } else {
                PhoneSettingsScreen(
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("channelKey") { type = NavType.StringType },
                navArgument("ctx") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
    }
}

package com.heartline.ai.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.heartline.ai.HeartlineApplication
import com.heartline.ai.ui.ChatListViewModel
import com.heartline.ai.ui.ChatThreadViewModel
import com.heartline.ai.ui.DiscoverViewModel
import com.heartline.ai.ui.HeartlineViewModelFactory
import com.heartline.ai.ui.OnboardingViewModel
import com.heartline.ai.ui.SettingsViewModel
import com.heartline.ai.ui.chat.ChatListScreen
import com.heartline.ai.ui.chat.ChatThreadScreen
import com.heartline.ai.ui.discover.DiscoverScreen
import com.heartline.ai.ui.onboarding.OnboardingScreen
import com.heartline.ai.ui.settings.SettingsScreen

@Composable
fun HeartlineNav(
    startDestination: String,
    openThreadId: String? = null,
    navController: NavHostController = rememberNavController()
) {
    val container = (LocalContext.current.applicationContext as HeartlineApplication).container
    if (openThreadId != null) {
        androidx.compose.runtime.LaunchedEffect(openThreadId) {
            navController.navigate(Routes.chat(openThreadId))
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.Onboarding) {
            val vm: OnboardingViewModel = viewModel(factory = HeartlineViewModelFactory(container))
            OnboardingScreen(vm) {
                navController.navigate(Routes.Discover) {
                    popUpTo(Routes.Onboarding) { inclusive = true }
                }
            }
        }
        composable(Routes.Discover) {
            val vm: DiscoverViewModel = viewModel(factory = HeartlineViewModelFactory(container))
            DiscoverScreen(
                viewModel = vm,
                onChats = { navController.navigate(Routes.Chats) },
                onSettings = { navController.navigate(Routes.Settings) },
                onOpenThread = { navController.navigate(Routes.chat(it)) }
            )
        }
        composable(Routes.Chats) {
            val vm: ChatListViewModel = viewModel(factory = HeartlineViewModelFactory(container))
            ChatListScreen(
                viewModel = vm,
                onDiscover = { navController.navigate(Routes.Discover) },
                onSettings = { navController.navigate(Routes.Settings) },
                onOpenThread = { navController.navigate(Routes.chat(it)) }
            )
        }
        composable(
            route = Routes.ChatThread,
            arguments = listOf(navArgument("threadId") { type = NavType.StringType })
        ) { backStackEntry ->
            val threadId = requireNotNull(backStackEntry.arguments?.getString("threadId"))
            val vm: ChatThreadViewModel = viewModel(factory = HeartlineViewModelFactory(container, threadId))
            ChatThreadScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onDiscover = { navController.navigate(Routes.Discover) }
            )
        }
        composable(Routes.Settings) {
            val vm: SettingsViewModel = viewModel(factory = HeartlineViewModelFactory(container))
            SettingsScreen(vm, onBack = { navController.popBackStack() })
        }
    }
}

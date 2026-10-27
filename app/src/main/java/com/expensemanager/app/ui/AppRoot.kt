package com.expensemanager.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.expensemanager.app.data.TxType
import com.expensemanager.app.ui.screens.AddEditScreen
import com.expensemanager.app.ui.screens.HomeScreen
import com.expensemanager.app.ui.screens.PeopleScreen
import com.expensemanager.app.ui.screens.SettingsScreen
import com.expensemanager.app.ui.screens.SmsImportScreen
import com.expensemanager.app.ui.screens.SplitsScreen
import com.expensemanager.app.ui.screens.TransactionsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Rounded.Home),
    Tab("transactions", "History", Icons.Rounded.ReceiptLong),
    Tab("splits", "Splits", Icons.Rounded.CallSplit),
    Tab("settings", "Settings", Icons.Rounded.Settings),
)

/** Navigation entry point for adding / editing / duplicating a transaction. */
fun editRoute(id: Long = 0, type: TxType = TxType.EXPENSE, copy: Boolean = false) =
    "edit?id=$id&type=${type.name}&copy=$copy"

@Composable
fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    // Survives individual screens so messages / undo still work after a screen closes.
    val rootScope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = tabs.any { it.route == current }
    // Snackbars live here, but the + button / bottom action bar belong to each screen,
    // so lift the snackbar above them instead of covering them.
    val snackbarLift = when (current) {
        "home", "transactions" -> 80.dp
        "sms" -> 76.dp
        else -> 0.dp
    }

    /**
     * Single way of switching tabs, used by the bottom bar and by in-screen shortcuts
     * (e.g. "You are owed" on Home). Home is the root, so going there just pops back to it.
     */
    fun goToTab(route: String) {
        if (route == "home") {
            nav.popBackStack("home", inclusive = false)
            return
        }
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = snackbarLift)) },
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = { goToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(pad).consumeWindowInsets(pad),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable("home") {
                HomeScreen(vm, onAdd = { nav.navigate(editRoute(type = it)) },
                    onOpen = { nav.navigate(editRoute(it)) },
                    onSeeAll = { goToTab("transactions") },
                    onSplits = { goToTab("splits") },
                    onScanSms = { nav.navigate("sms") })
            }
            composable("sms") {
                SmsImportScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onSplit = { id -> nav.navigate(editRoute(id)) },
                    onMessage = { msg -> rootScope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } },
                )
            }
            composable("transactions") {
                TransactionsScreen(vm, snackbar, onAdd = { nav.navigate(editRoute()) }, onOpen = { nav.navigate(editRoute(it)) })
            }
            composable("splits") { SplitsScreen(vm, snackbar, onOpen = { nav.navigate(editRoute(it)) }, onPeople = { nav.navigate("people") }) }
            composable("settings") { SettingsScreen(vm, snackbar, onPeople = { nav.navigate("people") }) }
            composable("people") { PeopleScreen(vm, onBack = { nav.popBackStack() }) }
            composable(
                "edit?id={id}&type={type}&copy={copy}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("type") { type = NavType.StringType; defaultValue = TxType.EXPENSE.name },
                    navArgument("copy") { type = NavType.BoolType; defaultValue = false },
                ),
                enterTransition = { slideInVertically { it / 8 } + fadeIn() },
                exitTransition = { slideOutVertically { it / 8 } + fadeOut() },
            ) { entry ->
                val args = entry.arguments!!
                AddEditScreen(
                    vm = vm,
                    id = args.getLong("id"),
                    initialType = TxType.valueOf(args.getString("type") ?: TxType.EXPENSE.name),
                    copy = args.getBoolean("copy"),
                    onMessage = { msg -> rootScope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } },
                    onDeleted = { item ->
                        rootScope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val r = snackbar.showSnackbar("Transaction deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                            if (r == SnackbarResult.ActionPerformed) vm.restore(item)
                        }
                    },
                    onClose = { nav.popBackStack() },
                    onDuplicate = { id ->
                        nav.popBackStack()
                        nav.navigate(editRoute(id, copy = true))
                    },
                    onManagePeople = { nav.navigate("people") },
                )
            }
        }
    }
}

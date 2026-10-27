package com.paisafloww.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.paisafloww.app.data.Direction
import com.paisafloww.app.data.TxType
import com.paisafloww.app.ui.screens.HomeScreen
import com.paisafloww.app.ui.screens.LoanDetailScreen
import com.paisafloww.app.ui.screens.LoanFormScreen
import com.paisafloww.app.ui.screens.LoansScreen
import com.paisafloww.app.ui.screens.ReportsScreen
import com.paisafloww.app.ui.screens.SavingsScreen
import com.paisafloww.app.ui.screens.SettingsScreen
import com.paisafloww.app.ui.screens.TransactionDetailScreen
import com.paisafloww.app.ui.screens.TransactionFormScreen
import com.paisafloww.app.ui.screens.TransactionsScreen
import com.paisafloww.app.ui.theme.PaisaTheme

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("transactions", "Transactions", Icons.Default.List),
    Tab("loans", "Loans", Icons.Default.Person),
    Tab("reports", "Reports", Icons.Default.Info),
    Tab("settings", "Settings", Icons.Default.Settings)
)

@Composable
fun AppRoot(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val dark = when (ui?.settings?.theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    PaisaTheme(dark = dark) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier) {
            val state = ui
            if (state == null) LoadingState() else MainContent(vm, state)
        }
    }
}

@Composable
private fun MainContent(vm: MainViewModel, ui: UiState) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onMainTab = TABS.any { it.route == route }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (onMainTab) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, maxLines = 1) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (route == "home" || route == "transactions" || route == "loans") {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { inner ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(inner)) {
            composable("home") {
                HomeScreen(
                    ui = ui,
                    onTx = { nav.navigate("tx/$it") },
                    onLoan = { nav.navigate("loan/$it") },
                    onSavings = { nav.navigate("savings") },
                    onSeeTransactions = { nav.goTab("transactions") },
                    onSeeLoans = { nav.goTab("loans") },
                    onAdd = { kind -> nav.addNew(kind) }
                )
            }
            composable("transactions") { TransactionsScreen(ui, onOpen = { nav.navigate("tx/$it") }) }
            composable("loans") { LoansScreen(ui, onOpen = { nav.navigate("loan/$it") }) }
            composable("reports") { ReportsScreen(ui) }
            composable("settings") { SettingsScreen(ui, vm) }
            composable("savings") { SavingsScreen(ui, vm, onBack = { nav.popBackStack() }) }
            composable(
                "tx/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                TransactionDetailScreen(
                    ui, vm, entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onEdit = { id, type -> nav.navigate("tx_form/$id/$type") }
                )
            }
            composable(
                "tx_form/{id}/{type}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("type") { type = NavType.StringType }
                )
            ) { entry ->
                TransactionFormScreen(
                    ui, vm,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    defaultType = entry.arguments?.getString("type") ?: TxType.EXPENSE,
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                "loan/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                LoanDetailScreen(
                    ui, vm, entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onEdit = { id, dir -> nav.navigate("loan_form/$id/$dir") }
                )
            }
            composable(
                "loan_form/{id}/{dir}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("dir") { type = NavType.StringType }
                )
            ) { entry ->
                LoanFormScreen(
                    ui, vm,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    defaultDirection = entry.arguments?.getString("dir") ?: Direction.BORROWED,
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showAdd = false; nav.addNew("EXPENSE") }) { Text("Expense") }
                    TextButton(onClick = { showAdd = false; nav.addNew("INCOME") }) { Text("Income") }
                    TextButton(onClick = { showAdd = false; nav.addNew("BORROWED") }) { Text("Money I borrowed") }
                    TextButton(onClick = { showAdd = false; nav.addNew("LENT") }) { Text("Money I lent") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

private fun NavController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavController.addNew(kind: String) {
    when (kind) {
        "INCOME", "EXPENSE" -> navigate("tx_form/0/$kind")
        else -> navigate("loan_form/0/$kind")
    }
}

package com.example.a7metros.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.a7metros.domain.Match
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private enum class BottomBarTab(val label: String) {
    Resultados("Resultados"),
    Clasificacion("Clasificación"),
}

@Composable
fun MainScreen(scoresViewModel: ScoresViewModel) {
    val uiState by scoresViewModel.uiState.collectAsState()
    MainScreen(uiState = uiState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(uiState: ScoresUiState) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(BottomBarTab.Resultados) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { MainScreenDrawerContent() },
    ) {
        Scaffold(
            topBar = { MainScreenTopBar(drawerState = drawerState, scope = scope) },
            bottomBar = {
                MainScreenBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                )
            },
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                ScoresContent(uiState = uiState)
            }
        }
    }
}

@Composable
private fun ScoresContent(uiState: ScoresUiState) {
    when (uiState) {
        is ScoresUiState.Loading -> Text(
            text = "Cargando resultados…",
            modifier = Modifier.padding(16.dp),
        )
        is ScoresUiState.Matches -> Column(modifier = Modifier.padding(16.dp)) {
            uiState.matches.forEach { match -> MatchRow(match) }
        }
        is ScoresUiState.Empty -> Text(
            text = "No hay partidos disponibles",
            modifier = Modifier.padding(16.dp),
        )
        is ScoresUiState.Failure -> Text(
            text = "No se han podido cargar los resultados",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun MatchRow(match: Match) {
    val scoreText = match.score?.let { "${it.home} - ${it.away}" } ?: "- : -"
    Text(
        text = "${match.homeTeam} $scoreText ${match.awayTeam} (${match.status})",
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun MainScreenDrawerContent() {
    ModalDrawerSheet {
        Text(
            text = "7metros",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp),
        )
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenTopBar(drawerState: DrawerState, scope: CoroutineScope) {
    CenterAlignedTopAppBar(
        title = { Text("7metros") },
        navigationIcon = {
            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                Icon(Icons.Default.Menu, contentDescription = "Abrir menú")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun MainScreenBottomBar(selectedTab: BottomBarTab, onTabSelected: (BottomBarTab) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.primary,
    ) {
        BottomBarTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = when (tab) {
                            BottomBarTab.Resultados -> Icons.AutoMirrored.Filled.List
                            BottomBarTab.Clasificacion -> Icons.Filled.Star
                        },
                        contentDescription = tab.label,
                    )
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                ),
            )
        }
    }
}

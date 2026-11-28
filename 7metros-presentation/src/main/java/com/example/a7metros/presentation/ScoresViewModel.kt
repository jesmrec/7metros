package com.example.a7metros.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.a7metros.domain.GetCurrentJornadaMatchesUseCase
import com.example.a7metros.domain.Match
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScoresUiState {
    data object Loading : ScoresUiState
    data class Matches(val jornada: Int, val matches: List<Match>) : ScoresUiState
    data class Empty(val jornada: Int) : ScoresUiState
    data object Failure : ScoresUiState
}

class ScoresViewModel(
    private val getCurrentJornadaMatches: GetCurrentJornadaMatchesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScoresUiState>(ScoresUiState.Loading)
    val uiState: StateFlow<ScoresUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // The jornada currently on screen, once known - null only before the first load
    // resolves. A match postponed out of its round's normal window (e.g. played weeks
    // after the round it belongs to) never comes back once the app moves on to later
    // rounds unless the user can navigate back to that exact jornada, so previous/next
    // re-request an explicit jornada number instead of always asking for "current".
    private var viewedJornada: Int? = null

    init {
        viewModelScope.launch { loadMatches(jornada = null) }
    }

    // Keeps the currently displayed list up while a pull-to-refresh is in flight, rather
    // than flipping uiState back to Loading and hiding it - isRefreshing drives the
    // indicator instead. Refreshes whichever jornada is on screen, not necessarily
    // "current", so checking back on a postponed match keeps you where you were.
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadMatches(viewedJornada)
            _isRefreshing.value = false
        }
    }

    fun showPreviousJornada() {
        viewedJornada?.let { jornada -> viewModelScope.launch { loadMatches(jornada - 1) } }
    }

    fun showNextJornada() {
        viewedJornada?.let { jornada -> viewModelScope.launch { loadMatches(jornada + 1) } }
    }

    private suspend fun loadMatches(jornada: Int?) {
        _uiState.value = getCurrentJornadaMatches(jornada).fold(
            onSuccess = { jornadaMatches ->
                viewedJornada = jornadaMatches.jornada
                if (jornadaMatches.matches.isEmpty()) {
                    ScoresUiState.Empty(jornada = jornadaMatches.jornada)
                } else {
                    ScoresUiState.Matches(jornada = jornadaMatches.jornada, matches = jornadaMatches.matches)
                }
            },
            // Leaves viewedJornada untouched on failure, e.g. navigating past the last
            // played jornada, so the user can navigate back without losing their place.
            onFailure = { ScoresUiState.Failure },
        )
    }
}

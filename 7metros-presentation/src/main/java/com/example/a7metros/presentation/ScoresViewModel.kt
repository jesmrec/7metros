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
    data object Empty : ScoresUiState
    data object Failure : ScoresUiState
}

class ScoresViewModel(
    private val getCurrentJornadaMatches: GetCurrentJornadaMatchesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScoresUiState>(ScoresUiState.Loading)
    val uiState: StateFlow<ScoresUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = getCurrentJornadaMatches().fold(
                onSuccess = { jornadaMatches ->
                    if (jornadaMatches.matches.isEmpty()) {
                        ScoresUiState.Empty
                    } else {
                        ScoresUiState.Matches(jornada = jornadaMatches.jornada, matches = jornadaMatches.matches)
                    }
                },
                onFailure = { ScoresUiState.Failure },
            )
        }
    }
}

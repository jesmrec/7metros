package com.example.a7metros

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.a7metros.data.AsobalScoresRepository
import com.example.a7metros.datasource.asobal.AsobalCurrentJornadaResolver
import com.example.a7metros.datasource.asobal.AsobalDataSource
import com.example.a7metros.domain.GetCurrentJornadaMatchesUseCase
import com.example.a7metros.network.OkHttpNetworkClient
import com.example.a7metros.presentation.ScoresViewModel

// Manual composition root (see design.md's "Wiring" decision): constructs the network
// client, datasource, repository, use case, and ViewModel factory in one place, so
// adopting a DI framework later is a localized change rather than a hunt through the
// codebase. No DI framework by design, for this same reason.
object CompositionRoot {

    val scoresViewModelFactory = viewModelFactory {
        initializer {
            val networkClient = OkHttpNetworkClient()
            val dataSource = AsobalDataSource(networkClient, AsobalCurrentJornadaResolver(networkClient))
            val useCase = GetCurrentJornadaMatchesUseCase(AsobalScoresRepository(dataSource))
            ScoresViewModel(useCase)
        }
    }
}

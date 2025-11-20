package com.gswtracker.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gswtracker.data.api.NbaApiClient
import com.gswtracker.data.api.NbaApiService
import com.gswtracker.data.repository.GameRepository

/**
 * Factory for creating GameViewModel with dependencies
 */
class GameViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            val apiClient = NbaApiClient(context)
            val apiService = NbaApiService(apiClient.httpClient)
            val repository = GameRepository(apiService)
            return GameViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.LocationPoint
import com.example.data.database.RunDao
import com.example.data.database.RunSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(private val runDao: RunDao) : ViewModel() {

    val runSessions: StateFlow<List<RunSession>> = runDao.getAllRunSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _sessionPoints = MutableStateFlow<Map<Long, List<LocationPoint>>>(emptyMap())
    val sessionPoints: StateFlow<Map<Long, List<LocationPoint>>> = _sessionPoints.asStateFlow()

    fun loadPointsForSession(sessionId: Long) {
        if (_sessionPoints.value.containsKey(sessionId)) return
        viewModelScope.launch {
            val points = runDao.getLocationPointsForSessionOnce(sessionId)
            _sessionPoints.update { current ->
                current + (sessionId to points)
            }
        }
    }
}

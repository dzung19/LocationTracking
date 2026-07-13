package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.LocationPoint
import com.example.data.database.RunDao
import com.example.data.database.RunSession
import com.example.data.database.RunStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HistoryViewModel(private val runDao: RunDao) : ViewModel() {

    val runSessions: StateFlow<List<RunSession>> = runDao.getAllRunSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val todayStats: StateFlow<RunStats> = runDao.getStatsInRange(
        fromTimeMillis = TimeUtils.getStartOfTodayMillis(),
        toTimeMillis = Long.MAX_VALUE
    ).map { it ?: RunStats() }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RunStats()
    )

    val weekStats: StateFlow<RunStats> = runDao.getStatsInRange(
        fromTimeMillis = TimeUtils.getStartOfWeekMillis(),
        toTimeMillis = Long.MAX_VALUE
    ).map { it ?: RunStats() }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RunStats()
    )

    val monthStats: StateFlow<RunStats> = runDao.getStatsInRange(
        fromTimeMillis = TimeUtils.getStartOfMonthMillis(),
        toTimeMillis = Long.MAX_VALUE
    ).map { it ?: RunStats() }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RunStats()
    )

    private val _selectedDateFilter = MutableStateFlow<Long?>(null)
    val selectedDateFilter: StateFlow<Long?> = _selectedDateFilter.asStateFlow()

    val filteredRunSessions: StateFlow<List<RunSession>> = combine(
        runSessions,
        _selectedDateFilter
    ) { sessions, filterMillis ->
        if (filterMillis == null) {
            sessions
        } else {
            val startOfDay = TimeUtils.getStartOfDayMillis(filterMillis)
            val endOfDay = startOfDay + 24 * 60 * 60 * 1000 - 1
            sessions.filter { it.startTimeInMillis in startOfDay..endOfDay }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setDateFilter(timestamp: Long?) {
        _selectedDateFilter.value = timestamp
    }

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

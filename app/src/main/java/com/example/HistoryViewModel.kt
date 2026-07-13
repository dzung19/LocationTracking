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
import java.util.Calendar
import java.util.TimeZone

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

    val totalXP: StateFlow<Int> = runSessions
        .map { sessions ->
            sessions.sumOf { (it.totalDistanceMeters / 100f).toInt() + it.totalCalories }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val currentStreak: StateFlow<Int> = runSessions
        .map { sessions ->
            calculateStreak(sessions)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private fun calculateStreak(sessions: List<RunSession>): Int {
        if (sessions.isEmpty()) return 0
        
        val tz = TimeZone.getDefault()
        val uniqueDays = sessions.map { session ->
            val cal = Calendar.getInstance(tz).apply { timeInMillis = session.startTimeInMillis }
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        }.distinct()
        
        val todayCal = Calendar.getInstance(tz)
        val todayStr = "${todayCal.get(Calendar.YEAR)}-${todayCal.get(Calendar.MONTH)}-${todayCal.get(Calendar.DAY_OF_MONTH)}"
        
        val yesterdayCal = Calendar.getInstance(tz).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayStr = "${yesterdayCal.get(Calendar.YEAR)}-${yesterdayCal.get(Calendar.MONTH)}-${yesterdayCal.get(Calendar.DAY_OF_MONTH)}"
        
        if (todayStr !in uniqueDays && yesterdayStr !in uniqueDays) {
            return 0
        }
        
        var streak = 0
        val checkCal = Calendar.getInstance(tz)
        if (todayStr in uniqueDays) {
            // Start checking from today
        } else {
            checkCal.add(Calendar.DAY_OF_YEAR, -1) // Start checking from yesterday
        }
        
        while (true) {
            val checkStr = "${checkCal.get(Calendar.YEAR)}-${checkCal.get(Calendar.MONTH)}-${checkCal.get(Calendar.DAY_OF_MONTH)}"
            if (checkStr in uniqueDays) {
                streak++
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
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

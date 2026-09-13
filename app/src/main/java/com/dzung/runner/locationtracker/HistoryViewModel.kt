package com.dzung.runner.locationtracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dzung.runner.locationtracker.data.database.LocationPoint
import com.dzung.runner.locationtracker.data.database.RunDao
import com.dzung.runner.locationtracker.data.database.RunSession
import com.dzung.runner.locationtracker.data.database.RunStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.dzung.runner.locationtracker.data.repository.UserPreferencesRepository
import java.util.Calendar
import java.util.TimeZone

data class RestoreStreakInfo(
    val canRestore: Boolean = false,
    val potentialStreak: Int = 0,
    val datesToRestore: List<String> = emptyList()
)

class HistoryViewModel(
    private val runDao: RunDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val restoredDates: StateFlow<Set<String>> = userPreferencesRepository.restoredStreakDatesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

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

    val currentStreak: StateFlow<Int> = combine(
        runSessions,
        restoredDates
    ) { sessions, restored ->
        calculateStreak(sessions, restored)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val restoreStreakInfo: StateFlow<RestoreStreakInfo> = combine(
        runSessions,
        restoredDates
    ) { sessions, restored ->
        getStreakRestoreInfo(sessions, restored)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RestoreStreakInfo()
    )

    fun restoreStreak() {
        val info = restoreStreakInfo.value
        if (!info.canRestore || info.datesToRestore.isEmpty()) return
        viewModelScope.launch {
            info.datesToRestore.forEach { dateStr ->
                userPreferencesRepository.restoreStreakDate(dateStr)
            }
        }
    }

    private fun calculateStreak(sessions: List<RunSession>, restored: Set<String> = emptySet()): Int {
        if (sessions.isEmpty() && restored.isEmpty()) return 0
        
        val tz = TimeZone.getDefault()
        val sessionDays = sessions.map { session ->
            val cal = Calendar.getInstance(tz).apply { timeInMillis = session.startTimeInMillis }
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        }
        val uniqueDays = (sessionDays + restored).distinct()
        
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

    private fun getStreakRestoreInfo(sessions: List<RunSession>, restored: Set<String>): RestoreStreakInfo {
        if (sessions.isEmpty()) return RestoreStreakInfo()

        val tz = TimeZone.getDefault()
        val current = calculateStreak(sessions, restored)

        val yesterdayCal = Calendar.getInstance(tz).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayStr = "${yesterdayCal.get(Calendar.YEAR)}-${yesterdayCal.get(Calendar.MONTH)}-${yesterdayCal.get(Calendar.DAY_OF_MONTH)}"

        val twoDaysAgoCal = Calendar.getInstance(tz).apply { add(Calendar.DAY_OF_YEAR, -2) }
        val twoDaysAgoStr = "${twoDaysAgoCal.get(Calendar.YEAR)}-${twoDaysAgoCal.get(Calendar.MONTH)}-${twoDaysAgoCal.get(Calendar.DAY_OF_MONTH)}"

        val sessionDays = sessions.map { session ->
            val cal = Calendar.getInstance(tz).apply { timeInMillis = session.startTimeInMillis }
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        }.toSet()
        val effectiveDays = sessionDays + restored

        // Test 1: Only yesterday was missed
        if (yesterdayStr !in effectiveDays) {
            val test1 = calculateStreak(sessions, restored + yesterdayStr)
            if (test1 > current && test1 >= 2) {
                return RestoreStreakInfo(
                    canRestore = true,
                    potentialStreak = test1,
                    datesToRestore = listOf(yesterdayStr)
                )
            }
        }

        // Test 2: Both yesterday and 2 days ago were missed
        if (yesterdayStr !in effectiveDays && twoDaysAgoStr !in effectiveDays) {
            val test2 = calculateStreak(sessions, restored + setOf(yesterdayStr, twoDaysAgoStr))
            if (test2 > current && test2 >= 2) {
                return RestoreStreakInfo(
                    canRestore = true,
                    potentialStreak = test2,
                    datesToRestore = listOf(yesterdayStr, twoDaysAgoStr)
                )
            }
        }

        return RestoreStreakInfo()
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

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            runDao.deleteRunSession(sessionId)
            _sessionPoints.update { current ->
                current - sessionId
            }
        }
    }
}

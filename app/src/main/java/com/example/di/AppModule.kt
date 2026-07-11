package com.example.di

import com.example.LocationViewModel
import com.example.HistoryViewModel
import com.example.data.database.AppDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Provide AppDatabase singleton
    single { AppDatabase.getDatabase(androidContext()) }
    
    // Provide RunDao
    single { get<AppDatabase>().runDao() }
    
    // Provide LocationViewModel
    viewModel { LocationViewModel() }
    
    // Provide HistoryViewModel
    viewModel { HistoryViewModel(get()) }
}

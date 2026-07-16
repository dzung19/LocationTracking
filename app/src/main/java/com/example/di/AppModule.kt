package com.example.di

import com.example.LocationViewModel
import com.example.HistoryViewModel
import com.example.data.database.AppDatabase
import com.example.data.network.OpenWeatherApi
import com.example.data.repository.WeatherRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

val appModule = module {
    // Provide AppDatabase singleton
    single { AppDatabase.getDatabase(androidContext()) }
    
    // Provide RunDao
    single { get<AppDatabase>().runDao() }

    // Provide Moshi
    single { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }

    // Provide OpenWeatherApi
    single<OpenWeatherApi> {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(MoshiConverterFactory.create(get()))
            .build()
            .create(OpenWeatherApi::class.java)
    }

    // Provide WeatherRepository
    single { WeatherRepository(get(), androidContext(), get()) }
    
    // Provide LocationViewModel with injected WeatherRepository and RunDao
    viewModel { LocationViewModel(get(), get()) }
    
    // Provide HistoryViewModel
    viewModel { HistoryViewModel(get()) }
}

package com.victorkoffed.projektandroid.di

import android.content.Context
import com.victorkoffed.projektandroid.data.db.CoffeeDao
import com.victorkoffed.projektandroid.data.db.CoffeeDatabase
import com.victorkoffed.projektandroid.data.repository.BookooScaleRepositoryImpl
import com.victorkoffed.projektandroid.data.repository.CoffeeImageRepository
import com.victorkoffed.projektandroid.data.repository.CoffeeImageRepositoryImpl
import com.victorkoffed.projektandroid.data.repository.CoffeeRepository
import com.victorkoffed.projektandroid.data.repository.CoffeeRepositoryImpl
import com.victorkoffed.projektandroid.data.repository.ScalePreferenceManager
import com.victorkoffed.projektandroid.data.repository.ScaleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // --- DB & DAO ---
    @Provides @Singleton
    fun provideCoffeeDatabase(@ApplicationContext context: Context): CoffeeDatabase =
        CoffeeDatabase.getInstance(context)

    @Provides
    fun provideCoffeeDao(db: CoffeeDatabase): CoffeeDao = db.coffeeDao()

    // --- Repositories ---
    @Provides @Singleton
    fun provideCoffeeRepository(dao: CoffeeDao): CoffeeRepository =
        CoffeeRepositoryImpl(dao)

    @Provides @Singleton
    fun provideScaleRepository(impl: BookooScaleRepositoryImpl): ScaleRepository = impl

    // --- Scale Preference Manager ---
    @Provides @Singleton
    fun provideScalePreferenceManager(@ApplicationContext context: Context): ScalePreferenceManager =
        ScalePreferenceManager(context)

    // --- Coffee Image Repository ---
    @Provides @Singleton
    fun provideCoffeeImageRepository(impl: CoffeeImageRepositoryImpl): CoffeeImageRepository = impl
}
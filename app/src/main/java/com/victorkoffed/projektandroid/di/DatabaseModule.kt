package com.victorkoffed.projektandroid.di

import android.content.Context
import com.victorkoffed.projektandroid.data.db.CoffeeDao
import com.victorkoffed.projektandroid.data.db.CoffeeDatabase
import com.victorkoffed.projektandroid.data.repository.ScalePreferenceManager
import com.victorkoffed.projektandroid.data.repository.implementation.BeanRepositoryImpl
import com.victorkoffed.projektandroid.data.repository.implementation.BookooScaleRepositoryImpl
import com.victorkoffed.projektandroid.data.repository.implementation.BrewRepositoryImpl
import com.victorkoffed.projektandroid.data.repository.implementation.CoffeeImageRepositoryImpl
import com.victorkoffed.projektandroid.data.repository.implementation.GrinderRepositoryImpl
import com.victorkoffed.projektandroid.data.repository.implementation.MethodRepositoryImpl
import com.victorkoffed.projektandroid.data.repository.interfaces.BeanRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.BrewRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.CoffeeImageRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.GrinderRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.MethodRepository
import com.victorkoffed.projektandroid.data.repository.interfaces.ScaleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Centralized Dependency Injection module for the application's data layer.
 * Configures application-wide singletons for local persistence (Room Database & DAOs),
 * external hardware integrations (BLE), and repository implementations.
 * Ensures that the UI and ViewModels interact strictly with domain abstractions.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideCoffeeDatabase(@ApplicationContext context: Context): CoffeeDatabase =
        CoffeeDatabase.getInstance(context)

    @Provides
    fun provideCoffeeDao(db: CoffeeDatabase): CoffeeDao = db.coffeeDao()

    @Provides @Singleton
    fun provideBeanRepository(dao: CoffeeDao): BeanRepository =
        BeanRepositoryImpl(dao)

    @Provides @Singleton
    fun provideBrewRepository(dao: CoffeeDao): BrewRepository =
        BrewRepositoryImpl(dao)

    @Provides @Singleton
    fun provideGrinderRepository(dao: CoffeeDao): GrinderRepository =
        GrinderRepositoryImpl(dao)

    @Provides @Singleton
    fun provideMethodRepository(dao: CoffeeDao): MethodRepository =
        MethodRepositoryImpl(dao)

    @Provides @Singleton
    fun provideScaleRepository(impl: BookooScaleRepositoryImpl): ScaleRepository = impl

    @Provides @Singleton
    fun provideCoffeeImageRepository(impl: CoffeeImageRepositoryImpl): CoffeeImageRepository = impl

    @Provides @Singleton
    fun provideScalePreferenceManager(@ApplicationContext context: Context): ScalePreferenceManager =
        ScalePreferenceManager(context)
}
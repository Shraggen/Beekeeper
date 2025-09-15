package com.bachelorthesis.beekeeperMobile.di

import android.content.Context
import androidx.room.Room
import com.bachelorthesis.beekeeperMobile.persistance.AppDatabase
import com.bachelorthesis.beekeeperMobile.persistance.LogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing a test version of the AppDatabase.
 *
 * This module is used only in instrumentation tests (androidTest). It replaces the
 * production on-disk database with a temporary, in-memory database. This ensures
 * that tests are hermetic (isolated from each other) and do not leave any data
 * on the device after running.
 */
@Module
@InstallIn(SingletonComponent::class)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideInMemoryAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries() // Allowing main thread queries is acceptable only for tests.
            .build()
    }

    @Provides
    @Singleton
    fun provideLogDao(appDatabase: AppDatabase): LogDao {
        return appDatabase.logDao()
    }
}
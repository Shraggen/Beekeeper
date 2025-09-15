package com.bachelorthesis.beekeeperMobile.di

import com.bachelorthesis.beekeeperMobile.assetManager.AssetManager
import com.bachelorthesis.beekeeperMobile.intentRecognizer.IntentRecognizer
import com.bachelorthesis.beekeeperMobile.persistance.LogRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.mockk
import javax.inject.Singleton

/**
 * Hilt module used in tests to replace real dependencies with mocks.
 *
 * This module is installed in place of the production AppModule during tests.
 * It provides a mock instance of LogRepository, allowing us to control its behavior
 * in our SyncWorker tests without needing a real database or network connection.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class] // This is the key: it tells Hilt to use this module instead of the real one.
)
object TestAppModule {

    @Provides
    @Singleton
    fun provideLogRepository(): LogRepository {
        return mockk(relaxed = true)
    }

    @Provides
    @Singleton
    fun provideAssetManager(): AssetManager {
        return mockk(relaxed = true)
    }

    @Provides
    @Singleton
    fun provideIntentRecognizer(): IntentRecognizer {
        return mockk(relaxed = true)
    }
}
package com.bachelorthesis.beekeeperMobile.di

import android.content.Context
import com.bachelorthesis.beekeeperMobile.assetManager.AssetManager
import com.bachelorthesis.beekeeperMobile.intentRecognizer.IntentRecognizer
import com.bachelorthesis.beekeeperMobile.intentRecognizer.LLMIntentRecognizer
import com.bachelorthesis.beekeeperMobile.intentRecognizer.RegexIntentRecognizer
import com.bachelorthesis.beekeeperMobile.persistance.ApiService
import com.bachelorthesis.beekeeperMobile.persistance.AppDatabase
import com.bachelorthesis.beekeeperMobile.persistance.LogDao
import com.bachelorthesis.beekeeperMobile.persistance.LogRepository
import com.bachelorthesis.beekeeperMobile.persistance.RetrofitClient
import com.bachelorthesis.beekeeperMobile.speechEngine.SpeechEngine
import com.bachelorthesis.beekeeperMobile.speechEngine.SpeechEngineInterface
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideLogDao(appDatabase: AppDatabase): LogDao {
        return appDatabase.logDao()
    }

    @Provides
    @Singleton
    fun provideApiService(): ApiService {
        return RetrofitClient.instance
    }

    @Provides
    @Singleton
    fun provideLogRepository(logDao: LogDao, apiService: ApiService): LogRepository {
        return LogRepository(logDao, apiService)
    }

    @Provides
    @Singleton
    fun provideAssetManager(@ApplicationContext context: Context): AssetManager {
        return AssetManager(context)
    }

    @Provides
    @Singleton
    fun provideIntentRecognizer(
        @ApplicationContext context: Context,
        assetManager: AssetManager
    ): IntentRecognizer {
        val llmRecognizer = LLMIntentRecognizer()
        var recognizer: IntentRecognizer = llmRecognizer // Assume LLM is primary

        // This is a simplified, synchronous check for the purpose of DI.
        // The service will still handle the full async initialization.
        val llmModelFile = assetManager.getLlmModelPath()
        if (!llmModelFile.exists() || llmModelFile.length() == 0L) {
            recognizer = RegexIntentRecognizer()
        }

        return recognizer
    }

    // [FIX] Tell Hilt how to provide an SpeechEngineInterface.
    // We can use @Binds for this, which is more efficient than @Provides
    // when the implementation has an @Inject constructor.
    // However, for clarity and consistency, we will use @Provides for now.
    @Provides
    @Singleton
    fun provideSpeechEngine(
        @ApplicationContext context: Context,
        assetManager: AssetManager
    ): SpeechEngineInterface {
        return SpeechEngine(context, assetManager)
    }
}
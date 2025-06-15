package com.bachelorthesis.beekeeperMobile

import com.bachelorthesis.beekeeperMobile.models.CreateLogRequest
import com.bachelorthesis.beekeeperMobile.models.Log
import com.bachelorthesis.beekeeperMobile.models.Task
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface defining the three required API endpoints for the mobile app.
 */
interface ApiService {
    /**
     * Sends a request to create a new log entry.
     * @param createLogRequest The request body containing hive ID and content.
     * @return A Response object containing the created Log.
     */
    @POST("api/logs")
    suspend fun createLog(@Body createLogRequest: CreateLogRequest): Response<Log>

    /**
     * Fetches the last log for a specific hive.
     * Note: Assumes the backend supports a query parameter e.g., 'hive_id'
     * based on the project requirements.
     * @param hiveId The ID of the hive to get the last log for.
     * @return A Response object containing the last Log.
     */
    @GET("api/logs/last")
    suspend fun getLastLogForHive(@Query("hive_id") hiveId: Int): Response<Log>

    /**
     * Fetches the last task for a specific hive.
     * Note: Assumes the backend supports a query parameter e.g., 'hive_id'
     * based on the project requirements.
     * @param hiveId The ID of the hive to get the last task for.
     * @return A Response object containing the last Task.
     */
    @GET("api/tasks/last")
    suspend fun getLastTaskForHive(@Query("hive_id") hiveId: Int): Response<Task>
}

/**
 * Singleton object to provide a configured Retrofit instance.
 */
object RetrofitClient {
    private const val BASE_URL = "http://localhost:8000/"

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}

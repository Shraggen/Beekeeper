package com.bachelorthesis.beekeeperMobile

import com.bachelorthesis.beekeeperMobile.models.CreateLogRequest
import com.bachelorthesis.beekeeperMobile.models.Hive
import com.bachelorthesis.beekeeperMobile.models.Log
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("api/logs")
    suspend fun createLog(@Body createLogRequest: CreateLogRequest): Response<Log>

    @GET("api/hives/{id}")
    suspend fun getHive(@Path("id") hiveId: Int): Response<Hive>
}

object RetrofitClient {
    private const val BASE_URL = "http://localhost:8000/" // "10.0.2.2" is localhost for the Android emulator

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}
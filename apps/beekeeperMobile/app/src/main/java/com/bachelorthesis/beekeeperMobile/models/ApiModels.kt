package com.bachelorthesis.beekeeperMobile.models

import com.google.gson.annotations.SerializedName

/**
 * Represents a single log entry for a hive.
 */
data class Log(
    val id: Int,
    @SerializedName("hive_id") val hiveId: Int,
    val content: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

/**
 * Represents a single task associated with a hive.
 */
data class Task(
    val id: Int,
    @SerializedName("hive_id") val hiveId: Int,
    val content: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

/**
 * Represents the request body for creating a new log entry.
 */
data class CreateLogRequest(
    @SerializedName("hiveID") val hiveID: Int,
    val content: String
)
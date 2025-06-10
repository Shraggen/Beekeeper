package com.bachelorthesis.beekeeperMobile.models

data class Hive(
    val id: Long,
    val hive_name: Int,
    val created_at: String,
    val updated_at: String,
    val logs: List<Log>?,
    val tasks: List<Task>?
)

data class Log(
    val id: Long,
    val hive_id: Int,
    val content: String,
    val created_at: String,
    val updated_at: String
)

data class Task(
    val id: Long,
    val hive_id: Int,
    val content: String,
    val created_at: String,
    val updated_at: String
)

// For creating new logs/tasks
data class CreateLogRequest(val hiveID: Int, val content: String)
data class CreateTaskRequest(val hiveID: Int, val content: String)
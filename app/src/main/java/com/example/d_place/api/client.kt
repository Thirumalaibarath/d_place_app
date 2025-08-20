package com.example.d_place.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

class ApiClient(private val baseHttpUrl: String) {
    private val client = OkHttpClient()

    suspend fun createSession(sessionName: String, userId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val url = "$baseHttpUrl/new_session?session_name=$sessionName&user_id=$userId"
            val req = Request.Builder()
                .url(url)
                .post(RequestBody.create(null, ByteArray(0)))
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) Result.success(Unit)
                    else Result.failure(Exception("HTTP ${resp.code}"))
                }
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    suspend fun joinSession(sessionName: String, userId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val url = "$baseHttpUrl/join_session?session_id=$sessionName&user_id=$userId"
            val req = Request.Builder()
                .url(url)
                .post(RequestBody.create(null, ByteArray(0)))
                .build()
            try {
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) Result.success(Unit)
                    else Result.failure(Exception("HTTP ${resp.code}"))
                }
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

}



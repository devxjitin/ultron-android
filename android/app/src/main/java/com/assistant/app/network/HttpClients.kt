package com.assistant.app.network

import okhttp3.OkHttpClient

/**
 * One OkHttpClient for the whole app. Each caller derives its own timeouts with `newBuilder()`,
 * which shares this client's connection pool and dispatcher instead of creating new ones.
 */
object HttpClients {
    val shared: OkHttpClient by lazy { OkHttpClient() }
}

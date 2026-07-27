package com.lightpass.reader

import android.util.Log
import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.shared.LightServerData
import kotlinx.coroutines.flow.StateFlow

@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    override suspend fun onToolCreate(serverData: StateFlow<LightServerData?>) {
        Log.d("LightPass", "Light Pass initialized")
    }

    override suspend fun onPushNotification(data: ByteArray) {
        Log.d("LightPass", "Ignoring unsupported push payload")
    }
}

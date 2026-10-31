package com.awd.teledrive.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SlotTdUpdate(
    val slotId: String,
    val update: TdApi.Object
)

@Singleton
class TelegramClient @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = ConcurrentHashMap<String, Client>()
    private val recreatingSlots = ConcurrentHashMap.newKeySet<String>()

    private val _activeSlotId = MutableStateFlow("default")
    val activeSlotId: StateFlow<String> = _activeSlotId.asStateFlow()

    private val _updates = MutableSharedFlow<TdApi.Object>(extraBufferCapacity = 500)
    val updates = _updates.asSharedFlow()

    private val _slotUpdates = MutableSharedFlow<SlotTdUpdate>(extraBufferCapacity = 500)
    val slotUpdates = _slotUpdates.asSharedFlow()

    val fileUpdates = updates.filterIsInstance<TdApi.UpdateFile>()

    @Volatile
    private var isNativeLoaded = false

    init {
        ensureNativeLoaded()
        getOrCreateClient("default")
    }

    private fun ensureNativeLoaded() {
        if (isNativeLoaded) return
        synchronized(this) {
            if (isNativeLoaded) return
            try {
                System.loadLibrary("tdjni")
                isNativeLoaded = true
                Log.d("TelegramClient", "TDLib native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("TelegramClient", "Failed to load TDLib native library", e)
            } catch (e: Exception) {
                Log.e("TelegramClient", "Error loading TDLib native library", e)
            }
        }
    }

    @Synchronized
    fun getOrCreateClient(slotId: String): Client? {
        ensureNativeLoaded()
        val cleanSlot = slotId.ifBlank { "default" }
        clients[cleanSlot]?.let { return it }
        return try {
            val newClient = Client.create(
                { update ->
                    _slotUpdates.tryEmit(SlotTdUpdate(cleanSlot, update))
                    if (_activeSlotId.value == cleanSlot) {
                        _updates.tryEmit(update)
                    }
                },
                null,
                null
            )
            clients[cleanSlot] = newClient
            newClient
        } catch (e: Exception) {
            Log.e("TelegramClient", "Error creating TDLib client for slot $cleanSlot", e)
            null
        }
    }

    fun switchSlot(slotId: String) {
        val cleanSlot = slotId.ifBlank { "default" }
        _activeSlotId.value = cleanSlot
        getOrCreateClient(cleanSlot)
    }

    fun isRecreating(slotId: String): Boolean {
        val cleanSlot = slotId.ifBlank { "default" }
        return recreatingSlots.contains(cleanSlot)
    }

    fun recreateSlotClient(slotId: String, onRecreated: (() -> Unit)? = null) {
        val cleanSlot = slotId.ifBlank { "default" }
        if (!recreatingSlots.add(cleanSlot)) {
            Log.d("TelegramClient", "Slot $cleanSlot is already undergoing recreation, queuing callback")
            scope.launch {
                var waitCount = 0
                while (recreatingSlots.contains(cleanSlot) && waitCount < 10) {
                    delay(100)
                    waitCount++
                }
                withContext(Dispatchers.Main) {
                    onRecreated?.invoke()
                }
            }
            return
        }

        val oldClient = clients.remove(cleanSlot)

        scope.launch(Dispatchers.IO) {
            if (oldClient != null) {
                val closedLatch = CountDownLatch(1)
                try {
                    oldClient.send(TdApi.Close()) {
                        closedLatch.countDown()
                    }
                    closedLatch.await(700, TimeUnit.MILLISECONDS)
                } catch (e: Exception) {
                    Log.e("TelegramClient", "Error while closing old client on slot $cleanSlot", e)
                }
            }
            // Give native OS file descriptors time to unlock td.binlog
            delay(150)
            getOrCreateClient(cleanSlot)
            recreatingSlots.remove(cleanSlot)
            withContext(Dispatchers.Main) {
                onRecreated?.invoke()
            }
        }
    }

    fun removeSlotClient(slotId: String) {
        val cleanSlot = slotId.ifBlank { "default" }
        val removed = clients.remove(cleanSlot)
        if (removed != null) {
            try {
                removed.send(TdApi.Close()) {}
            } catch (_: Exception) {}
        }
    }

    fun send(
        function: TdApi.Function<out TdApi.Object>,
        resultHandler: Client.ResultHandler = Client.ResultHandler { }
    ) {
        sendToSlot(_activeSlotId.value, function, resultHandler)
    }

    fun sendToSlot(
        slotId: String,
        function: TdApi.Function<out TdApi.Object>,
        resultHandler: Client.ResultHandler = Client.ResultHandler { }
    ) {
        val currentClient = getOrCreateClient(slotId)
        if (currentClient != null) {
            currentClient.send(function, resultHandler)
        } else {
            Log.e("TelegramClient", "Cannot send request: client is null for slot $slotId")
            resultHandler.onResult(TdApi.Error(500, "TDLib client not initialized"))
        }
    }
}

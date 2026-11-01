package com.batmon.app.webhooks

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.IOException

// ponytail: JSON array in DataStore Preferences for webhook queue and delivery history. Ceiling: ~50 history items; upgrade to Room if querying thousands of relational records.
private val Context.webhookDataStore: DataStore<Preferences> by preferencesDataStore(name = "batmon_webhook_queue")

class WebhookQueueRepository(private val context: Context? = null) {

    private object Keys {
        val PENDING_QUEUE_JSON = stringPreferencesKey("pending_queue_json")
        val DELIVERY_HISTORY_JSON = stringPreferencesKey("delivery_history_json")
    }

    private val inMemoryPending = kotlinx.coroutines.flow.MutableStateFlow<List<WebhookQueueItem>>(emptyList())
    private val inMemoryHistory = kotlinx.coroutines.flow.MutableStateFlow<List<WebhookQueueItem>>(emptyList())

    val pendingQueue: Flow<List<WebhookQueueItem>> = if (context != null) {
        context.webhookDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { prefs -> parseItems(prefs[Keys.PENDING_QUEUE_JSON]) }
    } else {
        inMemoryPending
    }

    val deliveryHistory: Flow<List<WebhookQueueItem>> = if (context != null) {
        context.webhookDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { prefs -> parseItems(prefs[Keys.DELIVERY_HISTORY_JSON]) }
    } else {
        inMemoryHistory
    }

    suspend fun getPendingItems(): List<WebhookQueueItem> = pendingQueue.first()

    suspend fun getHistory(): List<WebhookQueueItem> = deliveryHistory.first()

    suspend fun enqueue(item: WebhookQueueItem) {
        if (context != null) {
            context.webhookDataStore.edit { prefs ->
                val items = parseItems(prefs[Keys.PENDING_QUEUE_JSON]).toMutableList()
                items.add(item)
                prefs[Keys.PENDING_QUEUE_JSON] = serializeItems(items)
            }
        } else {
            val items = inMemoryPending.value.toMutableList()
            items.add(item)
            inMemoryPending.value = items
        }
    }

    suspend fun recordCompletion(item: WebhookQueueItem) {
        if (context != null) {
            context.webhookDataStore.edit { prefs ->
                val pending = parseItems(prefs[Keys.PENDING_QUEUE_JSON]).filterNot { it.id == item.id }
                prefs[Keys.PENDING_QUEUE_JSON] = serializeItems(pending)

                val history = parseItems(prefs[Keys.DELIVERY_HISTORY_JSON]).toMutableList()
                history.add(0, item)
                val capped = history.take(50)
                prefs[Keys.DELIVERY_HISTORY_JSON] = serializeItems(capped)
            }
        } else {
            val pending = inMemoryPending.value.filterNot { it.id == item.id }
            inMemoryPending.value = pending

            val history = inMemoryHistory.value.toMutableList()
            history.add(0, item)
            inMemoryHistory.value = history.take(50)
        }
    }

    suspend fun updatePending(item: WebhookQueueItem) {
        if (context != null) {
            context.webhookDataStore.edit { prefs ->
                val pending = parseItems(prefs[Keys.PENDING_QUEUE_JSON]).map {
                    if (it.id == item.id) item else it
                }
                prefs[Keys.PENDING_QUEUE_JSON] = serializeItems(pending)
            }
        } else {
            val pending = inMemoryPending.value.map {
                if (it.id == item.id) item else it
            }
            inMemoryPending.value = pending
        }
    }

    suspend fun clearHistory() {
        if (context != null) {
            context.webhookDataStore.edit { prefs ->
                prefs.remove(Keys.DELIVERY_HISTORY_JSON)
            }
        } else {
            inMemoryHistory.value = emptyList()
        }
    }

    suspend fun clearQueue() {
        if (context != null) {
            context.webhookDataStore.edit { prefs ->
                prefs.remove(Keys.PENDING_QUEUE_JSON)
            }
        } else {
            inMemoryPending.value = emptyList()
        }
    }

    private fun parseItems(jsonStr: String?): List<WebhookQueueItem> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<WebhookQueueItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                if (obj != null) {
                    list.add(obj.toWebhookQueueItem())
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeItems(items: List<WebhookQueueItem>): String {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        return array.toString()
    }
}

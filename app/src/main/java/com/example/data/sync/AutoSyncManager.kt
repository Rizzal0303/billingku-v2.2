package com.example.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutoSyncManager(
    private val networkMonitor: NetworkMonitor,
    private val mysqlSyncManager: MysqlSyncManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var periodicJob: Job? = null

    init {
        // Automatically trigger sync as soon as internet connection is restored
        networkMonitor.setOnNetworkAvailableListener {
            scope.launch {
                if (mysqlSyncManager.isAutoSyncEnabled() && mysqlSyncManager.getApiUrl().isNotBlank()) {
                    mysqlSyncManager.performFullAutoSync()
                }
            }
        }
    }

    private var lastQuickSyncTime = 0L

    fun startPeriodicSync() {
        if (periodicJob?.isActive == true) return
        periodicJob = scope.launch {
            // Initial sync on app launch / start if connected
            if (networkMonitor.isOnline.value && mysqlSyncManager.isAutoSyncEnabled() && mysqlSyncManager.getApiUrl().isNotBlank()) {
                mysqlSyncManager.performFullAutoSync()
            }

            while (isActive) {
                delay(15_000L) // Poll every 15 seconds to ensure fast multi-device consistency
                if (networkMonitor.isOnline.value && mysqlSyncManager.isAutoSyncEnabled() && mysqlSyncManager.getApiUrl().isNotBlank()) {
                    mysqlSyncManager.performFullAutoSync()
                }
            }
        }
    }

    fun stopPeriodicSync() {
        periodicJob?.cancel()
        periodicJob = null
    }

    fun triggerQuickSyncIfConnected() {
        val now = System.currentTimeMillis()
        if (now - lastQuickSyncTime < 5_000L) return // Debounce 5 seconds to avoid spamming
        lastQuickSyncTime = now
        scope.launch {
            if (networkMonitor.isOnline.value && mysqlSyncManager.isAutoSyncEnabled() && mysqlSyncManager.getApiUrl().isNotBlank()) {
                mysqlSyncManager.performFullAutoSync()
            }
        }
    }

    fun triggerManualSync(onComplete: (Result<String>) -> Unit = {}) {
        scope.launch {
            val result = mysqlSyncManager.performFullAutoSync()
            onComplete(result)
        }
    }
}

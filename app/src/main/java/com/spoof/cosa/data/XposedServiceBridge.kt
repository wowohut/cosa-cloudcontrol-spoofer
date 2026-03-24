package com.spoof.cosa.data

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

object XposedServiceBridge {

    interface Listener {
        fun onServiceChanged(service: XposedService?)
    }

    private val initialized = AtomicBoolean(false)
    private val listeners = CopyOnWriteArraySet<Listener>()

    @Volatile
    private var service: XposedService? = null

    fun addListener(listener: Listener) {
        ensureInitialized()
        listeners += listener
        listener.onServiceChanged(service)
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    private fun ensureInitialized() {
        if (!initialized.compareAndSet(false, true)) return
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                this@XposedServiceBridge.service = service
                notifyListeners(service)
            }

            override fun onServiceDied(service: XposedService) {
                if (this@XposedServiceBridge.service == service) {
                    this@XposedServiceBridge.service = null
                    notifyListeners(null)
                }
            }
        })
    }

    private fun notifyListeners(service: XposedService?) {
        listeners.forEach { it.onServiceChanged(service) }
    }
}

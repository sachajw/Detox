package com.wix.detox.reactnative.idlingresources

import android.util.Log
import androidx.test.espresso.IdlingResource
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactContext
import com.wix.detox.reactnative.helpers.RNHelpers
import org.joor.Reflect
import java.util.concurrent.Executor

class SerialExecutorReflectedz(private val executor: Reflect) {
    fun hasPendingTasks(): Boolean = pendingTasks().isNotEmpty()
    fun hasActiveTask(): Boolean = (activeTask() != null)
    fun executeTask(runnable: Runnable) = (executor.get() as Executor).execute(runnable)

    private fun pendingTasks() = executor.field("mTasks").get<Collection<Any>>()
    private fun activeTask() = executor.field("mActive").get<Runnable?>()
}

private class ModuleReflectedz(module: NativeModule) {
    private val reflected = Reflect.on(module)
    private val executorReflected = SerialExecutorReflectedz(reflected.field("executor"))

    val sexecutor: SerialExecutorReflectedz
        get() = executorReflected
}

class AsyncStorageIdlingResourceLegacyz(module: NativeModule): AsyncStorageIdlingResourcez(module)

open class AsyncStorageIdlingResourcez internal constructor(module: NativeModule): IdlingResource {
    private val moduleReflected = ModuleReflectedz(module)
    private var callback: IdlingResource.ResourceCallback? = null
    private var interrogationRunnable: Runnable? = null

    override fun getName(): String = this.javaClass.name

    override fun isIdleNow() =
        checkIdle().also { result ->
            if (!result) {
                Log.d(LOG_TAG, "Busy!")
                enqueueInterrogationTask()
            }
        }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
        this.callback = callback
        enqueueInterrogationTask()
    }

    private fun checkIdle(): Boolean =
        synchronized(moduleReflected.sexecutor) {
            with (moduleReflected.sexecutor) {
                if (interrogationRunnable != null) {
                    Log.d(LOG_TAG, "checkIdle: Already have an interrogation job enqueued")
                    return false
                }

                if (hasPendingTasks()) {
                    Log.d(LOG_TAG, "checkIdle: Detected pending tasks!")
                    return false
                }

                if (hasActiveTask()) {
                    Log.d(LOG_TAG, "checkIdle: Detected an active task!")
                    return false
                }
                return true
            }
        }

    private fun enqueueInterrogationTask() =
        synchronized(moduleReflected.sexecutor) {
            if (interrogationRunnable != null) {
                return
            }

            interrogationRunnable = Runnable {
                synchronized(moduleReflected.sexecutor) {
                    if (!moduleReflected.sexecutor.hasPendingTasks()) {
                        interrogationRunnable = null
                        this.callback?.onTransitionToIdle()
                        return@Runnable
                    }
                }
                moduleReflected.sexecutor.executeTask(interrogationRunnable!!)
            }
            moduleReflected.sexecutor.executeTask(interrogationRunnable!!)
        }

    companion object {
        private const val LOG_TAG = "AsyncStorageIR"

        fun createIfNeeded(reactContext: ReactContext, legacy: Boolean): AsyncStorageIdlingResourcez? {
            Log.d(LOG_TAG, "Checking whether a custom IR for Async-Storage is required... (legacy=$legacy)")

            val packageName = if (legacy) "com.facebook.react.modules.storage" else "com.reactnativecommunity.asyncstorage"
            val className = "$packageName.AsyncStorageModule"

            RNHelpers.getNativeModule(reactContext, className)?.let { module ->
                Log.d(LOG_TAG, "IR for Async-Storage is required! (legacy=$legacy)")
                return if (legacy) AsyncStorageIdlingResourceLegacyz(module) else AsyncStorageIdlingResourcez(module)
            }
            return null
        }
    }
}

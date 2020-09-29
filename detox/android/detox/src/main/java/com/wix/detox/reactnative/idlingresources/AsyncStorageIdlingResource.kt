package com.wix.detox.reactnative.idlingresources

import android.util.Log
import com.facebook.react.bridge.NativeModule
import org.joor.Reflect
import java.util.concurrent.Executor

private typealias SExecutorReflectedGenFnType = (executor: Executor) -> SerialExecutorReflected
private val defaultSExecutorReflectedGenFn: SExecutorReflectedGenFnType = { executor: Executor -> SerialExecutorReflected(executor) }

private class ModuleReflected(module: NativeModule, sexecutorReflectedGen: SExecutorReflectedGenFnType) {
    private val executorReflected: SerialExecutorReflected

    init {
        val reflected = Reflect.on(module)
        val executor: Executor = reflected.field("executor").get()
        executorReflected = sexecutorReflectedGen(executor)
    }

    val sexecutor: SerialExecutorReflected
        get() = executorReflected
}

class AsyncStorageIdlingResource(
        module: NativeModule,
        sexecutorReflectedGenFn: SExecutorReflectedGenFnType = defaultSExecutorReflectedGenFn) {

    private val moduleReflected = ModuleReflected(module, sexecutorReflectedGenFn)
    fun getName(): String = javaClass.name
    fun isIdleNow(): Boolean =
        with(moduleReflected.sexecutor) {
            synchronized(executor()) {
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

    companion object {
        private const val LOG_TAG = "AsyncStorageIR"
    }
}

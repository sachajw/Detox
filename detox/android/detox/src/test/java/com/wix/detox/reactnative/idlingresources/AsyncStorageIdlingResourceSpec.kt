package com.wix.detox.reactnative.idlingresources

import com.facebook.react.bridge.NativeModule
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wix.detox.UTHelpers.yieldToOtherThreads
import org.assertj.core.api.Assertions.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private class AsyncStorageModuleStub: NativeModule {
    val executor: Executor = mock(name = "native-module's executor")
    override fun getName() = "stub"
    override fun initialize() {}
    override fun canOverrideExistingModule() = false
    override fun onCatalystInstanceDestroy() {}
}

class AsyncStorageIdlingResourceSpec: Spek({
    describe("React Native Async-Storage idling-resource") {
        lateinit var sexecutor: Executor
        lateinit var sexecutorReflected: SerialExecutorReflected
        lateinit var sexecutorReflectedGenFn: (executor: Executor) -> SerialExecutorReflected
        lateinit var module: AsyncStorageModuleStub
        lateinit var uut: AsyncStorageIdlingResource

        beforeEachTest {
            sexecutor = mock()
            module = AsyncStorageModuleStub()
            sexecutorReflected = mock() {
                on { executor() }.thenReturn(sexecutor)
            }
            sexecutorReflectedGenFn = mock() {
                on { invoke(eq(module.executor)) }.thenReturn(sexecutorReflected)
            }

            uut = AsyncStorageIdlingResource(module, sexecutorReflectedGenFn)
        }

        fun givenNoActiveTasks() = whenever(sexecutorReflected.hasActiveTask()).thenReturn(false)
        fun givenAnActiveTask() = whenever(sexecutorReflected.hasActiveTask()).thenReturn(true)
        fun givenNoPendingTasks() = whenever(sexecutorReflected.hasPendingTasks()).thenReturn(false)
        fun givenPendingTasks() = whenever(sexecutorReflected.hasPendingTasks()).thenReturn(true)

        it("should have a name") {
            assertThat(uut.getName()).isEqualTo("com.wix.detox.reactnative.idlingresources.AsyncStorageIdlingResource")
        }

        describe("idle-check") {
            it("should be idle") {
                givenNoActiveTasks()
                givenNoPendingTasks()
                assertThat(uut.isIdleNow()).isTrue()
            }

            it("should be busy if executor is executing") {
                givenAnActiveTask()
                givenNoPendingTasks()
                assertThat(uut.isIdleNow()).isFalse()
            }

            it("should be busy if executor has pending tasks") {
                givenNoActiveTasks()
                givenPendingTasks()
                assertThat(uut.isIdleNow()).isFalse()
            }

            it("should be synchronized over sexecutor") {
                val localExecutor = Executors.newSingleThreadExecutor()
                var isIdle: Boolean? = null
                synchronized(sexecutor) {
                    localExecutor.submit {
                        isIdle = uut.isIdleNow()
                    }
                    yieldToOtherThreads(localExecutor)
                    assertThat(isIdle).isNull()
                }
                yieldToOtherThreads(localExecutor)
                assertThat(isIdle).isNotNull()
            }
        }
    }
})

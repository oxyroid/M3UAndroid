package com.m3u.business.setting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class ExtensionPluginOperationStateTest {
    @Test
    fun onlyOnePluginOperationCanRunAtATime() {
        val controller = ExtensionPluginOperationController()
        val refresh = assertNotNull(
            controller.tryStart(ExtensionPluginOperation.Refresh)
        )

        val rejected = controller.tryStart(
            ExtensionPluginOperation.Disable("dev.example.extension")
        )

        assertNull(rejected)
        assertSame(refresh, controller.state.value)
    }

    @Test
    fun completingActiveOperationAllowsNextOperation() {
        val controller = ExtensionPluginOperationController()
        val first = assertNotNull(
            controller.tryStart(ExtensionPluginOperation.Refresh)
        )

        val refreshPending = controller.finishAndConsumePendingRefresh(first)
        val second = assertNotNull(
            controller.tryStart(
                ExtensionPluginOperation.Enable(
                    packageName = "dev.example.extension",
                    serviceName = "dev.example.extension.Service",
                )
            )
        )

        assertEquals(false, refreshPending)
        assertEquals(
            ExtensionPluginOperation.Enable(
                packageName = "dev.example.extension",
                serviceName = "dev.example.extension.Service",
            ),
            second.operation,
        )
        assertSame(second, controller.state.value)
    }

    @Test
    fun staleEqualStateCannotFinishTheActiveLease() {
        val controller = ExtensionPluginOperationController()
        val active = assertNotNull(
            controller.tryStart(ExtensionPluginOperation.Refresh)
        )
        val equalButNotActive =
            ExtensionPluginOperationState.Running(ExtensionPluginOperation.Refresh)

        val refreshPending =
            controller.finishAndConsumePendingRefresh(equalButNotActive)

        assertEquals(false, refreshPending)
        assertSame(active, assertIs<ExtensionPluginOperationState.Running>(controller.state.value))
    }

    @Test
    fun refreshRequestedDuringAnOperationIsCoalesced() {
        val controller = ExtensionPluginOperationController()
        val active = assertNotNull(
            controller.tryStart(
                ExtensionPluginOperation.Disable("dev.example.extension")
            )
        )

        assertNull(
            controller.tryStart(
                operation = ExtensionPluginOperation.Refresh,
                queueRefreshIfBusy = true,
            )
        )
        assertNull(
            controller.tryStart(
                operation = ExtensionPluginOperation.Refresh,
                queueRefreshIfBusy = true,
            )
        )

        assertEquals(true, controller.finishAndConsumePendingRefresh(active))
        assertSame(ExtensionPluginOperationState.Idle, controller.state.value)
        assertEquals(
            false,
            controller.finishAndConsumePendingRefresh(active),
            "The pending refresh must only be consumed once",
        )
    }

    @Test
    fun ordinaryRefreshRequestWhileBusyIsIgnored() {
        val controller = ExtensionPluginOperationController()
        val active = assertNotNull(
            controller.tryStart(
                ExtensionPluginOperation.Disable("dev.example.extension")
            )
        )

        assertNull(controller.tryStart(ExtensionPluginOperation.Refresh))

        assertEquals(
            false,
            controller.finishAndConsumePendingRefresh(active),
        )
    }
}

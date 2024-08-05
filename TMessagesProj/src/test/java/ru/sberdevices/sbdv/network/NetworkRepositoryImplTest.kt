package ru.sberdevices.sbdv.network

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.NotificationCenter
import org.telegram.tgnet.ConnectionsManager
import ru.sberdevices.common.coroutines.CoroutineDispatchers.Default.io
import ru.sberdevices.test.common.MainCoroutineScopeRule

class NetworkRepositoryImplTest {

    @get:Rule
    val scope = MainCoroutineScopeRule()

    private val notificationCenter: NotificationCenter = mockk()
    private val connectionsManager: ConnectionsManager = mockk()

    private val listOfNoInternetConnectionStates = listOf(
        ConnectionsManager.ConnectionStateConnecting,
        ConnectionsManager.ConnectionStateWaitingForNetwork,
        ConnectionsManager.ConnectionStateConnectingToProxy,
        ConnectionsManager.ConnectionStateUpdating,
    )

    private val internetConnectedState = ConnectionsManager.ConnectionStateConnected

    @Test
    fun `initialization, networkState=AVAILABLE when internet is available`() = runBlockingTest {
        //prepare
        prepare(internetConnected = true)

        //do
        val repo = NetworkRepositoryImpl(notificationCenter, connectionsManager)

        //verify
        val resultState = repo.networkState.first()
        assertTrue(resultState == NetworkState.AVAILABLE)
    }

    @Test
    fun `initialization, networkState=NOT_AVAILABLE when internet is unavailable`() = runBlockingTest {
        //prepare
        prepare(internetConnected = false)

        //do
        val repo = NetworkRepositoryImpl(notificationCenter, connectionsManager)

        //verify
        val resultState = repo.networkState.first()
        assertTrue(resultState == NetworkState.NOT_AVAILABLE)
    }

    @Test
    fun `when network loses internet, networkState changes its value from AVAILABLE to NOT_AVAILABLE`() =
        runBlockingTest {
            //prepare
            prepare(internetConnected = true)
            val repo = NetworkRepositoryImpl(notificationCenter, connectionsManager)

            //do
            every { connectionsManager.connectionState } returnsMany listOfNoInternetConnectionStates
            every { notificationCenter.postNotificationName(NotificationCenter.didUpdateConnectionState) } returns Unit
            notificationCenter.postNotificationName(NotificationCenter.didUpdateConnectionState)

            //verify
            val resultState = repo.networkState.first()
            assertEquals(NetworkState.NOT_AVAILABLE, resultState)
        }

    @Test
    fun `when network becomes available, networkState changes its value from NOT_AVAILABLE to AVAILABLE`() =
        runBlockingTest {
            //prepare
            prepare(internetConnected = false)
            val repo = NetworkRepositoryImpl(notificationCenter, connectionsManager)

            //do
            every { connectionsManager.connectionState } returns internetConnectedState
            every { notificationCenter.postNotificationName(NotificationCenter.didUpdateConnectionState) } returns Unit
            notificationCenter.postNotificationName(NotificationCenter.didUpdateConnectionState)

            //verify
            val resultState = repo.networkState.first()
            assertEquals(NetworkState.AVAILABLE, resultState)
        }

    private fun prepare(internetConnected: Boolean) {
        every { notificationCenter.addObserver(any(), NotificationCenter.didUpdateConnectionState) } returns Unit
        every { notificationCenter.removeObserver(any(), NotificationCenter.didUpdateConnectionState) } returns Unit

        if (internetConnected) every { connectionsManager.connectionState } returns internetConnectedState
        else every { connectionsManager.connectionState } returnsMany listOfNoInternetConnectionStates
    }
}
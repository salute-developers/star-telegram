package ru.sberdevices.sbdv.auth.view

import android.graphics.Bitmap
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.telegram.tgnet.TLRPC
import ru.sberdevices.sbdv.auth.repo.AuthRepository
import ru.sberdevices.sbdv.auth.repo.QrCodeLoginUpdate
import ru.sberdevices.test.common.MainCoroutineScopeRule

class QrLoginViewControllerTest {

    @get:Rule
    val scope = MainCoroutineScopeRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val view: QrLoginViewController.View = mockk()
    private val repo: AuthRepository = mockk()
    private val loginSuccessListener: QrLoginViewController.OnLoginSuccessListener = mockk()

    private val controller = QrLoginViewController(view, repo, scope.dispatcher, loginSuccessListener)

    @Test
    fun `onResume() called, subscribed for QR codes`() {
        val flow: Flow<QrCodeLoginUpdate> = flow { }
        every { repo.observeQrCodeLogin() } returns flow

        controller.onResume()

        verify { repo.observeQrCodeLogin() }
    }

    @Test
    fun `onPause() called, unsubscribed from QR codes`() {
        val flow: Flow<QrCodeLoginUpdate> = flow { }
        every { repo.observeQrCodeLogin() } returns flow
        controller.onResume()
        val job = controller.observeQrCodeLogin!!

        controller.onPause()

        assertTrue(!job.isActive)
        assertEquals(null, controller.observeQrCodeLogin)
    }

    @Test
    fun `onDestroy called, scope cancelled`() {
        every { repo.observeQrCodeLogin() } returns mockk()

        controller.onResume()
        controller.onPause()

        controller.onDestroy()

        assertTrue(!controller.scope.isActive)
    }

    @Test
    fun `got NewQrCode, QR code shown`() {
        val bitmap: Bitmap = mockk()
        val flow = MutableStateFlow<QrCodeLoginUpdate>(QrCodeLoginUpdate.NewQrCode(bitmap))
        every { repo.observeQrCodeLogin() } returns flow

        controller.onResume()

        verify { view.showQr(eq(bitmap)) }
    }

    @Test
    fun `got NoInternet, QR code hid`() {
        val flow = MutableStateFlow<QrCodeLoginUpdate>(QrCodeLoginUpdate.NoNetwork)
        every { repo.observeQrCodeLogin() } returns flow

        controller.onResume()

        verify { view.hideQr() }
    }

    @Test
    fun `got LoginSuccess, OnLoginSuccessListener triggered`() {
        val authorization = TLRPC.TL_auth_authorization()
        val flow = MutableStateFlow<QrCodeLoginUpdate>(QrCodeLoginUpdate.LoginSuccess(authorization))
        every { repo.observeQrCodeLogin() } returns flow

        controller.onResume()

        verify { loginSuccessListener.onSuccess(any()) }
    }

    @Test
    fun `got NeedPassword, show2faPage shown`() {
        val flow = MutableStateFlow<QrCodeLoginUpdate>(QrCodeLoginUpdate.NeedPassword)
        every { repo.observeQrCodeLogin() } returns flow

        controller.onResume()

        verify { view.show2faPage() }
    }
}
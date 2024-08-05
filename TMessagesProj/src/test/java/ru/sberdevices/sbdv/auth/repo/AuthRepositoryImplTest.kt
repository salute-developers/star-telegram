package ru.sberdevices.sbdv.auth.repo

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.telegram.tgnet.TLRPC
import ru.sberdevices.sbdv.network.NetworkRepository
import ru.sberdevices.sbdv.network.NetworkState
import ru.sberdevices.sbdv.auth.remoteapi.AuthRemoteApi
import ru.sberdevices.sbdv.auth.remoteapi.LoginToken
import ru.sberdevices.test.common.MainCoroutineScopeRule
import java.util.Date
import java.util.concurrent.TimeUnit

class AuthRepositoryImplTest {

    @get:Rule
    val scope = MainCoroutineScopeRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val authApi: AuthRemoteApi = mockk()
    private val qrCodeFactory: AuthRepositoryImpl.QrCodeFactory = mockk()

    private val networkRepository: NetworkRepository = mockk(relaxed = true)

    private val repo = AuthRepositoryImpl(authApi, scope.dispatcher, qrCodeFactory, networkRepository)

    @Test
    fun `observeQrCodeLogin() called and QR not yet accepted, got some QrCodes`() = scope.runBlockingTest {
        val expirationTimeMs = TimeUnit.SECONDS.toMillis(30)
        coEvery { authApi.exportLoginToken() } answers {
            LoginToken.QrCode(
                expires = Date(System.currentTimeMillis() + expirationTimeMs),
                token = ByteArray(20)
            )
        }
        coEvery { authApi.awaitLoginSuccess() } coAnswers {
            delay(Long.MAX_VALUE)
        }
        every { qrCodeFactory.create(any(), any()) } answers { mockk() }

        every { networkRepository.networkState } returns MutableStateFlow(NetworkState.AVAILABLE)

        val loginEvents = repo.observeQrCodeLogin()
            .take(5)
            .toList()

        loginEvents.forEach { event ->
            assertTrue(event is QrCodeLoginUpdate.NewQrCode)
        }
    }

    @Test
    fun `observeQrCodeLogin() called and QR already accepted, got LoginSuccess`() = scope.runBlockingTest {
        coEvery { authApi.exportLoginToken() } returns LoginToken.Success(TLRPC.TL_auth_authorization())
        coEvery { authApi.awaitLoginSuccess() } returns Unit
        every { networkRepository.networkState } returns MutableStateFlow(NetworkState.AVAILABLE)

        val event = repo.observeQrCodeLogin().first()

        assertTrue(event is QrCodeLoginUpdate.LoginSuccess)
    }

    @Test
    fun `observeQrCodeLogin() called and QR already accepted and migration required, got LoginSuccess`() =
        scope.runBlockingTest {
            val migrateTo = LoginToken.MigrateTo(100500, ByteArray(10))
            coEvery { authApi.exportLoginToken() } returns migrateTo
            coEvery { authApi.awaitLoginSuccess() } returns Unit
            coEvery { authApi.importLoginToken(eq(migrateTo)) } returns LoginToken.Success(TLRPC.TL_auth_authorization())

            every { networkRepository.networkState } returns MutableStateFlow(NetworkState.AVAILABLE)

            val event = repo.observeQrCodeLogin().first()

            assertTrue(event is QrCodeLoginUpdate.LoginSuccess)
        }

    @Test
    fun `observeQrCodeLogin() called and without accepted QR and than it accepted, got QrCode and then LoginSuccess`() =
        scope.runBlockingTest {
            val expirationTimeMs = TimeUnit.SECONDS.toMillis(30)

            val qrCode = LoginToken.QrCode(
                expires = Date(System.currentTimeMillis() + expirationTimeMs),
                token = ByteArray(20)
            )
            coEvery { authApi.exportLoginToken() } returns qrCode
            coEvery { authApi.awaitLoginSuccess() } coAnswers {
                delay(TimeUnit.SECONDS.toMillis(2))
            }
            every { qrCodeFactory.create(any(), any()) } answers { mockk() }

            every { networkRepository.networkState } returns MutableStateFlow(NetworkState.AVAILABLE)

            val loginEvents = mutableListOf<QrCodeLoginUpdate>()
            val job = launch {
                loginEvents.addAll(
                    repo.observeQrCodeLogin()
                        .take(2)
                        .toList()
                )
            }

            coEvery { authApi.exportLoginToken() } returns LoginToken.Success(TLRPC.TL_auth_authorization())
            job.join()

            assertTrue(loginEvents[0] is QrCodeLoginUpdate.NewQrCode)
            assertTrue(loginEvents[1] is QrCodeLoginUpdate.LoginSuccess)
        }

    @Test
    fun `observeQrCodeLogin() called, tg link passed to QrCodeFactory`() = scope.runBlockingTest {
        val expirationTimeMs = TimeUnit.SECONDS.toMillis(30)
        coEvery { authApi.exportLoginToken() } answers {
            LoginToken.QrCode(
                expires = Date(System.currentTimeMillis() + expirationTimeMs),
                token = ByteArray(31) { i -> i.toByte() }
            )
        }
        coEvery { authApi.awaitLoginSuccess() } coAnswers {
            delay(Long.MAX_VALUE)
        }
        every { qrCodeFactory.create(any(), any()) } answers { mockk() }

        every { networkRepository.networkState } returns MutableStateFlow(NetworkState.AVAILABLE)

        async {
            repo.observeQrCodeLogin().first()
        }.await()

        verify { qrCodeFactory.create(eq("tg://login?token=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHg"), any()) }
    }

    @Test
    fun `internet in unavailable, observeQrCodeLogin() called and got NoInternet`() = scope.runBlockingTest {
        coEvery { authApi.exportLoginToken() } returns any()
        coEvery { authApi.awaitLoginSuccess() } returns Unit
        every { networkRepository.networkState } returns MutableStateFlow(NetworkState.NOT_AVAILABLE)

        val event = repo.observeQrCodeLogin().first()

        assertEquals(QrCodeLoginUpdate.NoNetwork, event)
    }
}
package ru.sberdevices.sbdv.auth.remoteapi

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.MessagesController
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import ru.sberdevices.sbdv.remoteapi.Request
import ru.sberdevices.sbdv.remoteapi.Response
import java.util.Date
import java.util.concurrent.TimeUnit

class AuthRemoteApiImplTest {

    private val sender: Request.Sender = mockk(relaxed = true)
    private val callbackManager: AuthRemoteApiImpl.CallbackManager = mockk(relaxed = true)

    private val api = AuthRemoteApiImpl(sender, callbackManager)

    @Test
    fun `awaitLoginSuccess() called, execution suspended`() = runBlockingTest {
        val job = launch { api.awaitLoginSuccess() }

        assertTrue(job.isActive)
        job.cancel()
    }

    @Test
    fun `awaitLoginSuccess() called and then got onLoginTokenUpdate, execution continued`() = runBlockingTest {
        val callback = slot<MessagesController.LoginTokenUpdateCallback>()
        every { callbackManager.addLoginTokenUpdateCallback(capture(callback)) } answers {
            callback.captured.onLoginTokenUpdate()
        }

        val job = launch { api.awaitLoginSuccess() }

        assertTrue(job.isCompleted)
    }

    @Test
    fun `awaitLoginSuccess() called and then got onLoginTokenUpdate, callback removed`() = runBlockingTest {
        val callback = slot<MessagesController.LoginTokenUpdateCallback>()
        every { callbackManager.addLoginTokenUpdateCallback(capture(callback)) } answers {
            callback.captured.onLoginTokenUpdate()
        }

        launch { api.awaitLoginSuccess() }

        verify { callbackManager.removeLoginTokenUpdateCallback(callback.captured) }
    }

    @Test
    fun `awaitLoginSuccess() called and then cancelled, callback removed`() = runBlockingTest {
        val callback = slot<MessagesController.LoginTokenUpdateCallback>()
        every { callbackManager.addLoginTokenUpdateCallback(capture(callback)) } returns Unit

        launch { api.awaitLoginSuccess() }.cancel()

        verify { callbackManager.removeLoginTokenUpdateCallback(callback.captured) }
    }

    @Test
    fun `exportLoginToken() called and code was not scanned, LoginToken_QrCode returned`() = runBlockingTest {
        val expiresSec = 1_000_000_000
        val tokenArray = ByteArray(40)
        val senderResponse = Response(TLRPC.TL_auth_loginToken().apply {
            expires = expiresSec
            token = tokenArray
        })
        coEvery { sender.send(any()) } returns senderResponse

        val response = api.exportLoginToken()

        val code = response as LoginToken.QrCode
        assertEquals(Date(TimeUnit.SECONDS.toMillis(expiresSec.toLong())), code.expires)
        assertEquals(tokenArray, response.token)
    }

    @Test
    fun `exportLoginToken() called, TL_auth_loginTokenMigrateTo returned`() = runBlockingTest {
        val datacenterId = ConnectionsManager.DEFAULT_DATACENTER_ID
        val tokenArray = ByteArray(40)
        val senderResponse = Response(TLRPC.TL_auth_loginTokenMigrateTo().apply {
            dc_id = datacenterId
            token = tokenArray
        })

        coEvery { sender.send(any()) } returns senderResponse

        val response = api.exportLoginToken()
        val migrationToToken = response as LoginToken.MigrateTo

        assertEquals(migrationToToken.datacenterId, datacenterId)
        assertEquals(migrationToToken.token, tokenArray)
    }

    @Test(expected = UnexpectedResponseException::class)
    fun `exportLoginToken() called, TL_error returned`() = runBlockingTest {
        val tlError = TLRPC.TL_error().apply {
            text = "error"
            code = 999
        }
        val senderResponse = Response(tlError)
        coEvery { sender.send(any()) } returns senderResponse

        api.exportLoginToken()

        Assert.fail("Must return from catch block")
    }

    @Test
    fun `exportLoginToken() called, TL_error_401 and SessionPasswordNeeded got`() = runBlockingTest {
        val expectedResult = LoginToken.SessionPasswordNeeded

        val tlError = TLRPC.TL_error().apply {
            text = "SESSION_PASSWORD_NEEDED"
            code = 401
        }
        val senderResponse = Response(tlError)
        coEvery { sender.send(any()) } returns senderResponse

        val response = api.exportLoginToken()
        val sessionPasswordNeeded = response as LoginToken.SessionPasswordNeeded

        assertEquals(expectedResult, sessionPasswordNeeded)
    }

    @Test
    fun `exportLoginToken() called and code was scanned, LoginToken_Success returned`() = runBlockingTest {
        val loginTokenSuccess = TLRPC.TL_auth_loginTokenSuccess().apply {
            authorization = TLRPC.TL_auth_authorization()
        }
        val senderResponse = Response(loginTokenSuccess)
        coEvery { sender.send(any()) } returns senderResponse

        val response = api.exportLoginToken()

        val success = response as LoginToken.Success
        assertEquals(loginTokenSuccess.authorization, success.authorization)
    }

    @Test
    fun `importLoginToken() called, request with TL_auth_importLoginToken is sent`() = runBlockingTest {
        val response = Response(
            TLRPC.TL_auth_loginTokenSuccess().apply {
                authorization = TLRPC.TL_auth_authorization()
            }
        )
        coEvery { sender.send(any()) } returns response
        val migrationToken = LoginToken.MigrateTo(100500, ByteArray(4))

        api.importLoginToken(migrationToken)

        coVerify { sender.send(any()) }
    }

    @Test
    fun `importLoginToken() called, TL_auth_authorization is returned`() = runBlockingTest {
        val loginTokenSuccess = TLRPC.TL_auth_loginTokenSuccess().apply {
            authorization = TLRPC.TL_auth_authorization()
        }
        val senderResponse = Response(loginTokenSuccess)
        coEvery { sender.send(any()) } returns senderResponse
        val migrationToken = LoginToken.MigrateTo(100500, ByteArray(4))

        val response = api.importLoginToken(migrationToken)

        assertEquals(loginTokenSuccess.authorization, response.authorization)
    }

    @Test
    fun `exportLoginToken() called, returned TL_auth_loginTokenMigrateTo passed to importLoginToken() and TL_auth_authorization returned`() =
        runBlockingTest {
            val datacenterId = ConnectionsManager.DEFAULT_DATACENTER_ID
            val tokenArray = ByteArray(40)
            val onExportLoginTokenSenderResponse = Response(TLRPC.TL_auth_loginTokenMigrateTo().apply {
                dc_id = datacenterId
                token = tokenArray
            })

            coEvery { sender.send(any()) } returns onExportLoginTokenSenderResponse

            val migrationToken = api.exportLoginToken() as LoginToken.MigrateTo

            val loginTokenSuccess = TLRPC.TL_auth_loginTokenSuccess().apply {
                authorization = TLRPC.TL_auth_authorization()
            }
            val onImportLoginTokenSenderResponse = Response(
                loginTokenSuccess
            )

            coEvery { sender.send(any()) } returns onImportLoginTokenSenderResponse

            val success = api.importLoginToken(migrationToken)

            assertEquals(success.authorization, loginTokenSuccess.authorization)
        }
}

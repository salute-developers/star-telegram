package ru.sberdevices.sbdv.remoteapi

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.telegram.tgnet.RequestDelegateTimestamp
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import java.util.EnumSet

class RequestSenderImplTest {

    private val sendFunction: SendFunction = mockk()
    private val cancelFunction: CancelFunction = mockk()

    private val sender = RequestSenderImpl(sendFunction, cancelFunction)

    @Test
    fun `send() called with a request, got a response`() = runBlockingTest {
        val tlResponse = TLObject()
        val delegate = slot<RequestDelegateTimestamp>()
        every { sendFunction.send(any(), capture(delegate), any(), any(), any()) } answers {
            delegate.captured.run(tlResponse, null, 0)
            100500
        }

        val request = Request(
            tlObject = TLObject(),
            flags = EnumSet.of(
                Request.Flag.FAIL_ON_SERVER_ERRORS,
                Request.Flag.WITHOUT_LOGIN,
                Request.Flag.TRY_DIFFERENT_DC,
                Request.Flag.ENABLE_UNAUTHORIZED,
            )
        )

        val response = sender.send(request)

        verify { sendFunction.send(eq(request.tlObject), any(), any(), any(), any()) }
        assertEquals(tlResponse, response.tlObject)
    }

    @Test
    fun `send() called with a request, got an exception`() = runBlockingTest {
        val tlError = TLRPC.TL_error()
        tlError.apply {
            text = "any not 401 error code text message"
            code = 999
        }

        val delegate = slot<RequestDelegateTimestamp>()
        every { sendFunction.send(any(), capture(delegate), any(), any(), any()) } answers {
            delegate.captured.run(null, tlError, 0)
            100500
        }

        val request = Request(
            tlObject = TLObject(),
            flags = EnumSet.of(
                Request.Flag.FAIL_ON_SERVER_ERRORS,
                Request.Flag.WITHOUT_LOGIN,
                Request.Flag.TRY_DIFFERENT_DC,
                Request.Flag.ENABLE_UNAUTHORIZED,
            )
        )

        try {
            sender.send(request)
        } catch (e: Exception) {
            verify { sendFunction.send(eq(request.tlObject), any(), any(), any(), any()) }
            assertTrue(e is Request.Sender.Exception)
            assertEquals(tlError, (e as Request.Sender.Exception).tlError)
            return@runBlockingTest
        }

        fail("Must return from catch block")
    }

    @Test
    fun `send() called with a request, got session_password_needed`() = runBlockingTest {
        val tlPasswordNeededError = TLRPC.TL_error()
        tlPasswordNeededError.apply {
            text = "SESSION_PASSWORD_NEEDED"
            code = 401
        }

        val delegate = slot<RequestDelegateTimestamp>()
        every { sendFunction.send(any(), capture(delegate), any(), any(), any()) } answers {
            delegate.captured.run(null, tlPasswordNeededError, 0)
            100500
        }

        val request = Request(
            tlObject = TLObject(),
            flags = EnumSet.of(
                Request.Flag.FAIL_ON_SERVER_ERRORS,
                Request.Flag.WITHOUT_LOGIN,
                Request.Flag.TRY_DIFFERENT_DC,
                Request.Flag.ENABLE_UNAUTHORIZED,
            )
        )

        val response = sender.send(request)

        verify { sendFunction.send(eq(request.tlObject), any(), any(), any(), any()) }
        assertEquals(tlPasswordNeededError, response.tlObject)
    }

    @Test
    fun `send() called with a request and than cancelled, cancelFunc called`() = runBlockingTest {
        val delegate = slot<RequestDelegateTimestamp>()
        every { sendFunction.send(any(), capture(delegate), any(), any(), any()) } returns 100500
        every { cancelFunction.cancel(any()) } returns Unit

        val request = Request(
            tlObject = TLObject(),
            flags = EnumSet.of(
                Request.Flag.FAIL_ON_SERVER_ERRORS,
                Request.Flag.WITHOUT_LOGIN,
                Request.Flag.TRY_DIFFERENT_DC,
                Request.Flag.ENABLE_UNAUTHORIZED,
            )
        )

        launch { sender.send(request) }.cancel()

        verify { cancelFunction.cancel(100500) }
    }
}

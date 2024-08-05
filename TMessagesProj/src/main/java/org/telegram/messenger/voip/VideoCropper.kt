package org.telegram.messenger.voip

import android.graphics.Rect
import org.webrtc.Camera2Capturer
import org.webrtc.CapturerObserver
import org.webrtc.VideoFrame
import ru.sberdevices.cv.detection.entity.humans.Humans
import ru.sberdevices.sbdv.SbdvServiceLocator
import ru.sberdevices.smartfocus.SmartFocusFactory
import ru.sberdevices.smartfocus.SmartFocusMode
import ru.sberdevices.smartfocus.animator.SmartFocusAnimatorMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

const val CROP_WIDTH = 1280
const val CROP_HEIGHT = 720

class VideoCropper(
    private val capturerObserver: CapturerObserver,
    private val capturer: Camera2Capturer,
    private val useHalCrop: Boolean,
) : CapturerObserver {

    override fun onCapturerStarted(success: Boolean) {
        capturerObserver.onCapturerStarted(success)
    }

    override fun onCapturerStopped() {
        capturerObserver.onCapturerStopped()
    }

    override fun onFrameCaptured(frame: VideoFrame?) {
        val (sensorAreaWidth, tracker) = focusTracker.get()
        val cropRect = if (useInstantFocusOnNextUpdate.compareAndSet(true, false)) {
            tracker.updateWithAnimatorMode(instantAnimatorMode)
        } else {
            tracker.update()
        }

        if (useHalCrop) {
            val mirrored = Rect(cropRect)
            mirrored.left = sensorAreaWidth - cropRect.right
            mirrored.right = mirrored.left + cropRect.width()
            capturer.setCropArea(mirrored)

            capturerObserver.onFrameCaptured(frame)
        } else {
            val croppedFrame = createCroppedFrame(frame, cropRect)
            capturerObserver.onFrameCaptured(croppedFrame)
            croppedFrame?.release()
        }
    }

    private fun createCroppedFrame(frame: VideoFrame?, cropRect: Rect): VideoFrame? {
        var croppedFrame: VideoFrame? = null
        if (frame != null) {
            val mirroredLeft = frame.buffer.width - cropRect.right
            val croppedBuffer = frame.buffer.cropAndScale(
                mirroredLeft,
                cropRect.top,
                cropRect.width(),
                cropRect.height(),
                CROP_WIDTH,
                CROP_HEIGHT,
            )
            croppedFrame = VideoFrame(croppedBuffer, frame.rotation, frame.timestampNs)
        }
        return croppedFrame
    }

    companion object {

        private val useInstantFocusOnNextUpdate = AtomicBoolean(false)
        private val instantAnimatorMode = SmartFocusAnimatorMode.Instant()

        private val focusTracker = AtomicReference(
            Pair(
                CROP_WIDTH,
                SmartFocusFactory.create(
                    CROP_WIDTH,
                    CROP_HEIGHT,
                    SbdvServiceLocator.config.smartFocusMode,
                )
            )
        )

        @JvmStatic
        fun setActiveSensorArea(area: Rect) {
            focusTracker.set(
                Pair(
                    area.width(),
                    SmartFocusFactory.create(area.width(), area.height(), SbdvServiceLocator.config.smartFocusMode)
                )
            )
        }

        @JvmStatic
        fun setSmartFocusEnabled(enable: Boolean) {
            if (enable) {
                focusTracker.get().second.setMode(SbdvServiceLocator.config.smartFocusMode)
            } else {
                focusTracker.get().second.setMode(SmartFocusMode.Disabled)
            }
            useInstantFocusOnNextUpdate.set(true)
        }

        @JvmStatic
        fun followNextHuman() {
            focusTracker.get().second.followNextHuman()
            useInstantFocusOnNextUpdate.set(true)
        }

        @JvmStatic
        fun setHumans(humans: Humans) {
            focusTracker.get().second.setHumans(humans)
        }
    }
}

package org.telegram.messenger.voip;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.WindowManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.webrtc.Camera2Capturer;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.voiceengine.WebRtcAudioRecord;

import ru.sberdevices.sbdv.SbdvServiceLocator;
import ru.sberdevices.sbdv.util.SbdvVoIPUtil;

@TargetApi(18)
public class VideoCapturerDevice {

    private static final String TAG = "VideoCapturerDevice";

    public static final Size[] CROP_SOURCE_RESOLUTIONS = {
            new Size(1280, 720),
            new Size(1408, 792),
            new Size(1536, 864),
            new Size(1664, 936),
            new Size(1792, 1008),
            new Size(1920, 1080),
            new Size(2304, 1296),
            new Size(2560, 1440),
            new Size(2816, 1584),
            new Size(3072, 1728),
            new Size(3328, 1872),
            new Size(3584, 2016),
            new Size(3840, 2160),
    };

    public static final Size DEFAULT_CROP_SOURCE_RESOLUTION = CROP_SOURCE_RESOLUTIONS[5];

    private static final int HAL_CROP_CAPTURE_WIDTH = 1280;
    private static final int HAL_CROP_CAPTURE_HEIGHT = 720;
    public static final int HAL_CROP_CAPTURE_FPS = 20;

    private static final int CAPTURE_WIDTH = 1920;
    private static final int CAPTURE_HEIGHT = 1080;
    private static final int CAPTURE_FPS = 30;

    public static EglBase eglBase;

    public static Intent mediaProjectionPermissionResultData;

    private boolean useHalCrop;
    private VideoCapturer videoCapturer;
    private SurfaceTextureHelper videoCapturerSurfaceTextureHelper;

    private HandlerThread thread;
    private Handler handler;
    private int currentWidth;
    private int currentHeight;

    private long nativePtr;

    private static VideoCapturerDevice[] instance = new VideoCapturerDevice[2];
    private CapturerObserver nativeCapturerObserver;

    public VideoCapturerDevice(boolean screencast) {
        Log.d(TAG, "VideoCaptureDevice(screencast: " + screencast + ")");
        if (Build.VERSION.SDK_INT < 18) {
            return;
        }
        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);
        Logging.d(TAG, "device model = " + Build.MANUFACTURER + Build.MODEL);
        AndroidUtilities.runOnUIThread(() -> {
            if (eglBase == null) {
                eglBase = EglBase.create(null, EglBase.CONFIG_PLAIN);
            }
            instance[screencast ? 1 : 0] = this;
            thread = new HandlerThread("CallThread");
            thread.start();
            handler = new Handler(thread.getLooper());
        });
    }

    public static void checkScreenCapturerSize() {
        if (instance[1] == null) {
            return;
        }
        Point size = getScreenCaptureSize();
        if (instance[1].currentWidth != size.x || instance[1].currentHeight != size.y) {
            instance[1].currentWidth = size.x;
            instance[1].currentHeight = size.y;
            VideoCapturerDevice device = instance[1];
            instance[1].handler.post(() -> {
                if (device.videoCapturer != null) {
                    device.videoCapturer.changeCaptureFormat(size.x, size.y, CAPTURE_FPS);
                }
            });
        }
    }

    private static Point getScreenCaptureSize() {
        WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);

        float aspect;
        if (size.x > size.y) {
            aspect = size.y / (float) size.x;
        } else {
            aspect = size.x / (float) size.y;
        }
        int dx = -1;
        int dy = -1;
        for (int a = 1; a <= 100; a++) {
            float val = a * aspect;
            if (val == (int) val) {
                if (size.x > size.y) {
                    dx = a;
                    dy = (int) (a * aspect);
                } else {
                    dy = a;
                    dx = (int) (a * aspect);
                }
                break;
            }
        }
        if (dx != -1 && aspect != 1) {
            while (size.x > 1000 || size.y > 1000 || size.x % 4 != 0 || size.y % 4 != 0) {
                size.x -= dx;
                size.y -= dy;
                if (size.x < 800 && size.y < 800) {
                    dx = -1;
                    break;
                }
            }
        }
        if (dx == -1 || aspect == 1) {
            float scale = Math.max(size.x / 970.0f, size.y / 970.0f);
            size.x = (int) Math.ceil((size.x / scale) / 4.0f) * 4;
            size.y = (int) Math.ceil((size.y / scale) / 4.0f) * 4;
        }
        return size;
    }

    private void init(long ptr, String deviceName) {
        Log.d(TAG, "init()");
        if (Build.VERSION.SDK_INT < 18) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (eglBase == null) {
                return;
            }
            nativePtr = ptr;
            if ("screen".equals(deviceName)) {
                if (Build.VERSION.SDK_INT < 21) {
                    return;
                }
                if (videoCapturer == null) {
                    videoCapturer = new ScreenCapturerAndroid(mediaProjectionPermissionResultData, new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (VoIPService.getSharedInstance() != null) {
                                    VoIPService.getSharedInstance().stopScreenCapture();
                                }
                            });
                        }
                    });

                    Point size = getScreenCaptureSize();
                    currentWidth = size.x;
                    currentHeight = size.y;
                    videoCapturerSurfaceTextureHelper = SurfaceTextureHelper.create("ScreenCapturerThread", eglBase.getEglBaseContext());
                    handler.post(() -> {
                        if (videoCapturerSurfaceTextureHelper == null || nativePtr == 0) {
                            return;
                        }
                        nativeCapturerObserver = nativeGetJavaVideoCapturerObserver(nativePtr);
                        Log.d(TAG, "Not expected be here. Could affect XBUGS-8636");
                        videoCapturer.initialize(videoCapturerSurfaceTextureHelper, ApplicationLoader.applicationContext, nativeCapturerObserver);
                        videoCapturer.startCapture(size.x, size.y, CAPTURE_FPS);
                        WebRtcAudioRecord audioRecord = WebRtcAudioRecord.Instance;
                        if (audioRecord != null) {
                            audioRecord.initDeviceAudioRecord(((ScreenCapturerAndroid) videoCapturer).getMediaProjection());
                        }
                    });
                }
            } else {
                Camera2Enumerator enumerator = new Camera2Enumerator(ApplicationLoader.applicationContext);
                int index = -1;
                String[] names = enumerator.getDeviceNames();
                for (int a = 0; a < names.length; a++) {
                    boolean isFrontFace = enumerator.isFrontFacing(names[a]);
                    if (isFrontFace == "front".equals(deviceName)) {
                        index = a;
                        break;
                    }
                }
                if (index == -1) {
                    if (names.length == 0) {
                        return;
                    }
                    Logging.d("VideoCameraCapturer", "Use first camera in list");
                    index = 0;
                }
                String cameraName = names[index];
                Logging.d("VideoCameraCapturer", cameraName + "supported formats: " + enumerator.getSupportedFormats(cameraName));

                useHalCrop = enumerator.isHalCropAvailable(cameraName);
                Rect sensorArea = enumerator.getActiveSensorArea(cameraName);
                Logging.d("VideoCameraCapturer", "sensorArea " + sensorArea);
                if (useHalCrop && sensorArea != null) {
                    VideoCropper.Companion.setActiveSensorArea(sensorArea);
                } else {
                    VideoCropper.Companion.setActiveSensorArea(new Rect(0, 0, CAPTURE_WIDTH, CAPTURE_HEIGHT));
                }

                if (videoCapturer == null) {
                    videoCapturer = enumerator.createCapturer(cameraName, new CameraVideoCapturer.CameraEventsHandler() {
                        @Override
                        public void onCameraError(String errorDescription) {

                        }

                        @Override
                        public void onCameraDisconnected() {

                        }

                        @Override
                        public void onCameraFreezed(String errorDescription) {

                        }

                        @Override
                        public void onCameraOpening(String cameraName) {

                        }

                        @Override
                        public void onFirstFrameAvailable() {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (VoIPService.getSharedInstance() != null) {
                                    VoIPService.getSharedInstance().onCameraFirstFrameAvailable();
                                }
                            });
                        }

                        @Override
                        public void onCameraClosed() {

                        }
                    });
                    videoCapturerSurfaceTextureHelper = SurfaceTextureHelper.create("VideoCapturerThread", eglBase.getEglBaseContext());
                    final boolean finalUseHalCrop = useHalCrop;
                    handler.post(() -> {
                        if (videoCapturerSurfaceTextureHelper == null) {
                            return;
                        }
                        nativeCapturerObserver = nativeGetJavaVideoCapturerObserver(nativePtr);
                        VideoCropper cropper = new VideoCropper(nativeCapturerObserver, (Camera2Capturer) videoCapturer, useHalCrop);
                        Log.d(TAG, "init videoCapturer");
                        videoCapturer.initialize(videoCapturerSurfaceTextureHelper, ApplicationLoader.applicationContext, cropper);
                        if (!SbdvVoIPUtil.isStateWaitingIncoming()) {
                            sbdvStartCapture(finalUseHalCrop);
                        } else {
                            Log.d(TAG, "stateWaitingIncoming, no need to startCapture on init");
                        }
                    });
                } else {
                    handler.post(() -> ((CameraVideoCapturer) videoCapturer).switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                        @Override
                        public void onCameraSwitchDone(boolean isFrontCamera) {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (VoIPService.getSharedInstance() != null) {
                                    VoIPService.getSharedInstance().setSwitchingCamera(false, isFrontCamera);
                                }
                            });
                        }

                        @Override
                        public void onCameraSwitchError(String errorDescription) {

                        }
                    }, cameraName));
                }
            }
        });
    }

    private void sbdvStartCapture(boolean finalUseHalCrop) {
        Log.d(TAG, "sbdvStartCapture()");
        int captureWidth = finalUseHalCrop ? HAL_CROP_CAPTURE_WIDTH : CAPTURE_WIDTH;
        int captureHeight = finalUseHalCrop ? HAL_CROP_CAPTURE_HEIGHT : CAPTURE_HEIGHT;
        int captureFps = finalUseHalCrop ? getCaptureFps() : CAPTURE_FPS;
        if (videoCapturer != null) {
            videoCapturer.startCapture(captureWidth, captureHeight, captureFps);
        } else {
            Log.d(TAG, "videoCapturer is null. Do nothing");
        }
    }

    public static MediaProjection getMediaProjection() {
        if (instance[1] == null) {
            return null;
        }
        return ((ScreenCapturerAndroid) instance[1].videoCapturer).getMediaProjection();
    }

    private int getCaptureFps() {
        return SbdvServiceLocator.getLocalConfigSharedInstance().getLocalConfigStateFlow().getValue().getCaptureFps();
    }

    private void onAspectRatioRequested(float aspectRatio) {
        /*if (aspectRatio < 0.0001f) {
            return;
        }
        handler.post(() -> {
            if (nativeCapturerObserver instanceof NativeCapturerObserver) {
                int w;
                int h;
                if (aspectRatio < 1.0f) {
                    h = CAPTURE_HEIGHT;
                    w = (int) (h / aspectRatio);
                } else {
                    w = CAPTURE_WIDTH;
                    h = (int) (w * aspectRatio);
                }
                if (w <= 0 || h <= 0) {
                    return;
                }
                NativeCapturerObserver observer = (NativeCapturerObserver) nativeCapturerObserver;
                NativeAndroidVideoTrackSource source = observer.getNativeAndroidVideoTrackSource();
                source.adaptOutputFormat(new VideoSource.AspectRatio(w, h), w * h, new VideoSource.AspectRatio(h, w), w * h, CAPTURE_FPS);
            }
        });*/
    }

    private void onStateChanged(long ptr, int state) {
        if (Build.VERSION.SDK_INT < 18) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            Log.d(TAG, "onStateChanged(), state: " + SbdvVoIPUtil.getCurrentState());
            if (nativePtr != ptr) {
                return;
            }
            final boolean finalUseHalCrop = useHalCrop;
            handler.post(() -> {
                if (videoCapturer == null) {
                    return;
                }
                if (state == Instance.VIDEO_STATE_ACTIVE) {
                    Log.d(TAG, "state: VIDEO_STATE_ACTIVE");
                    Boolean isStateWaitingIncoming = SbdvVoIPUtil.isStateWaitingIncoming();
                    Log.d(TAG, "stateWaitingIncoming: " + isStateWaitingIncoming);
                    if (!isStateWaitingIncoming) {
                        sbdvStartCapture(finalUseHalCrop);
                    } else {
                        Log.d(TAG, "do nothing");
                    }
                } else {
                    try {
                        Log.d(TAG, "stopCapture()");
                        videoCapturer.stopCapture();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        });
    }

    private void onDestroy() {
        if (Build.VERSION.SDK_INT < 18) {
            return;
        }
        nativePtr = 0;
        AndroidUtilities.runOnUIThread(() -> {
//            if (eglBase != null) {
//                eglBase.release();
//                eglBase = null;
//            }
            for (int a = 0; a < instance.length; a++) {
                if (instance[a] == this) {
                    instance[a] = null;
                    break;
                }
            }
            handler.post(() -> {
                if (videoCapturer instanceof ScreenCapturerAndroid) {
                    WebRtcAudioRecord audioRecord = WebRtcAudioRecord.Instance;
                    if (audioRecord != null) {
                        audioRecord.stopDeviceAudioRecord();
                    }
                }
                if (videoCapturer != null) {
                    try {
                        videoCapturer.stopCapture();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    videoCapturer.dispose();
                    videoCapturer = null;
                }
                if (videoCapturerSurfaceTextureHelper != null) {
                    videoCapturerSurfaceTextureHelper.dispose();
                    videoCapturerSurfaceTextureHelper = null;
                }
            });
            try {
                thread.quitSafely();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private EglBase.Context getSharedEGLContext() {
        if (eglBase == null) {
            eglBase = EglBase.create(null, EglBase.CONFIG_PLAIN);
        }
        return eglBase != null ? eglBase.getEglBaseContext() : null;
    }

    public static EglBase getEglBase() {
        if (eglBase == null) {
            eglBase = EglBase.create(null, EglBase.CONFIG_PLAIN);
        }
        return eglBase;
    }

    private static native CapturerObserver nativeGetJavaVideoCapturerObserver(long ptr);
}

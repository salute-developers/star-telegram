/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.graphics.Point;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

/**
 * Display the video stream on a SurfaceView.
 */
public class SurfaceViewRenderer extends SurfaceView
    implements SurfaceHolder.Callback, VideoSink, RendererCommon.RendererEvents {
  private static final String TAG = "SurfaceViewRenderer";

  // Cached resource name.
  private final String resourceName;
  private final RendererCommon.VideoLayoutMeasure videoLayoutMeasure =
      new RendererCommon.VideoLayoutMeasure();
  private final SurfaceEglRenderer eglRenderer;

  // Callback for reporting renderer events. Read-only after initialization so no lock required.
  private RendererCommon.RendererEvents rendererEvents;

  // Accessed only on the main thread.
  public int rotatedFrameWidth;
  public int rotatedFrameHeight;

  private int videoWidth;
  private int videoHeight;
  private boolean enableFixedSize;
  private int surfaceWidth;
  private int surfaceHeight;
  private boolean isCamera;
  private boolean mirror;
  private boolean rotateTextureWitchScreen;
  private int screenRotation;

  private OrientationHelper orientationHelper;
  private int maxTextureSize;

  Runnable updateScreenRunnable;

  /**
   * Standard View constructor. In order to render something, you must first call init().
   */
  public SurfaceViewRenderer(Context context) {
    super(context);
    this.resourceName = getResourceName();
    eglRenderer = new SurfaceEglRenderer(resourceName);
    getHolder().addCallback(this);
    getHolder().addCallback(eglRenderer);
  }

  /**
   * Standard View constructor. In order to render something, you must first call init().
   */
  public SurfaceViewRenderer(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.resourceName = getResourceName();
    eglRenderer = new SurfaceEglRenderer(resourceName);
    getHolder().addCallback(this);
    getHolder().addCallback(eglRenderer);
  }

  public void clearFirstFrame() {
    eglRenderer.firstFrameRendered = false;
    eglRenderer.isFirstFrameRendered = false;
  }

  /**
   * Initialize this class, sharing resources with |sharedContext|. It is allowed to call init() to
   * reinitialize the renderer after a previous init()/release() cycle.
   */
  public void init(EglBase.Context sharedContext, RendererCommon.RendererEvents rendererEvents) {
    init(sharedContext, rendererEvents, EglBase.CONFIG_PLAIN, new GlRectDrawer());
  }

  /**
   * Initialize this class, sharing resources with |sharedContext|. The custom |drawer| will be used
   * for drawing frames on the EGLSurface. This class is responsible for calling release() on
   * |drawer|. It is allowed to call init() to reinitialize the renderer after a previous
   * init()/release() cycle.
   */
  public void init(final EglBase.Context sharedContext,
      RendererCommon.RendererEvents rendererEvents, final int[] configAttributes,
      RendererCommon.GlDrawer drawer) {
    ThreadUtils.checkIsOnMainThread();
    this.rendererEvents = rendererEvents;
    rotatedFrameWidth = 0;
    rotatedFrameHeight = 0;
    eglRenderer.init(sharedContext, this /* rendererEvents */, configAttributes, drawer);
  }

  public void setIsCamera(boolean value) {
    isCamera = value;
    if (!isCamera) {
      orientationHelper = new OrientationHelper() {
        @Override
        protected void onOrientationUpdate(int orientation) {
          updateRotation();
        }
      };
      orientationHelper.start();
    }
  }

  /**
   * Block until any pending frame is returned and all GL resources released, even if an interrupt
   * occurs. If an interrupt occurs during release(), the interrupt flag will be set. This function
   * should be called before the Activity is destroyed and the EGLContext is still valid. If you
   * don't call this function, the GL resources might leak.
   */
  public void release() {
    eglRenderer.release();
    if (orientationHelper != null) {
      orientationHelper.stop();
    }
  }

  public void updateRotation() {
    if (orientationHelper == null || rotatedFrameWidth == 0 || rotatedFrameHeight == 0) {
      return;
    }
    View parentView = (View) getParent();
    if (parentView == null) {
      return;
    }
    int orientation = orientationHelper.getOrientation();
    float viewWidth = getMeasuredWidth();
    float viewHeight = getMeasuredHeight();
    float w;
    float h;
    float targetWidth = parentView.getMeasuredWidth();
    float targetHeight = parentView.getMeasuredHeight();
    if (orientation == 90 || orientation == 270) {
      w = viewHeight;
      h = viewWidth;
    } else {
      w = viewWidth;
      h = viewHeight;
    }
    float scale;
    if (w < h) {
      scale = Math.max(w / viewWidth, h / viewHeight);
    } else {
      scale = Math.min(w / viewWidth, h / viewHeight);
    }
    w *= scale;
    h *= scale;
    if (Math.abs(w / h - targetWidth / targetHeight) < 0.1f) {
      scale *= Math.max(targetWidth / w, targetHeight / h);
    }
    if (orientation == 270) {
      orientation = -90;
    }
    animate().scaleX(scale).scaleY(scale).rotation(-orientation).setDuration(180).start();
  }

  /**
   * Register a callback to be invoked when a new video frame has been received.
   *
   * @param listener The callback to be invoked. The callback will be invoked on the render thread.
   *                 It should be lightweight and must not call removeFrameListener.
   * @param scale    The scale of the Bitmap passed to the callback, or 0 if no Bitmap is
   *                 required.
   * @param drawer   Custom drawer to use for this frame listener.
   */
  public void addFrameListener(
      EglRenderer.FrameListener listener, float scale, RendererCommon.GlDrawer drawerParam) {
    eglRenderer.addFrameListener(listener, scale, drawerParam);
  }

  public void getRenderBufferBitmap(GlGenericDrawer.TextureCallback callback) {
    eglRenderer.getTexture(callback);
  }

  /**
   * Register a callback to be invoked when a new video frame has been received. This version uses
   * the drawer of the EglRenderer that was passed in init.
   *
   * @param listener The callback to be invoked. The callback will be invoked on the render thread.
   *                 It should be lightweight and must not call removeFrameListener.
   * @param scale    The scale of the Bitmap passed to the callback, or 0 if no Bitmap is
   *                 required.
   */
  public void addFrameListener(EglRenderer.FrameListener listener, float scale) {
    eglRenderer.addFrameListener(listener, scale);
  }

  public void removeFrameListener(EglRenderer.FrameListener listener) {
    eglRenderer.removeFrameListener(listener);
  }

  /**
   * Enables fixed size for the surface. This provides better performance but might be buggy on some
   * devices. By default this is turned off.
   */
  public void setEnableHardwareScaler(boolean enabled) {
    ThreadUtils.checkIsOnMainThread();
    enableFixedSize = enabled;
    updateSurfaceSize();
  }

  /**
   * Set if the video stream should be mirrored or not.
   */
  public void setMirror(final boolean mirror) {
    if (this.mirror != mirror) {
      this.mirror = mirror;
      if (rotateTextureWitchScreen) {
        onRotationChanged();
      } else {
        eglRenderer.setMirror(mirror);
      }
      updateSurfaceSize();
      requestLayout();
    }
  }

  /**
   * Set how the video will fill the allowed layout area.
   */
  public void setScalingType(RendererCommon.ScalingType scalingType) {
    ThreadUtils.checkIsOnMainThread();
    videoLayoutMeasure.setScalingType(scalingType);
    requestLayout();
  }

  public void setScalingType(RendererCommon.ScalingType scalingTypeMatchOrientation,
      RendererCommon.ScalingType scalingTypeMismatchOrientation) {
    ThreadUtils.checkIsOnMainThread();
    videoLayoutMeasure.setScalingType(scalingTypeMatchOrientation, scalingTypeMismatchOrientation);
    requestLayout();
  }

  /**
   * Limit render framerate.
   *
   * @param fps Limit render framerate to this value, or use Float.POSITIVE_INFINITY to disable fps
   *            reduction.
   */
  public void setFpsReduction(float fps) {
    eglRenderer.setFpsReduction(fps);
  }

  public void disableFpsReduction() {
    eglRenderer.disableFpsReduction();
  }

  public void pauseVideo() {
    eglRenderer.pauseVideo();
  }

  // VideoSink interface.
  @Override
  public void onFrame(VideoFrame frame) {
    eglRenderer.onFrame(frame);
  }

  // View layout interface.
  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    ThreadUtils.checkIsOnMainThread();
    if (!isCamera && rotateTextureWitchScreen) {
      updateVideoSizes();
    }

    Point size;
    if (maxTextureSize > 0) {
      size = videoLayoutMeasure.measure(isCamera, MeasureSpec.makeMeasureSpec(Math.min(maxTextureSize, MeasureSpec.getSize(widthSpec)), MeasureSpec.getMode(widthSpec)), MeasureSpec.makeMeasureSpec(Math.min(maxTextureSize, MeasureSpec.getSize(heightSpec)), MeasureSpec.getMode(heightSpec)), rotatedFrameWidth, rotatedFrameHeight);
    } else {
      size = videoLayoutMeasure.measure(isCamera, widthSpec, heightSpec, rotatedFrameWidth, rotatedFrameHeight);
    }
    setMeasuredDimension(size.x, size.y);
    // logD("onMeasure(). New size: " + size.x + "x" + size.y);
    if (rotatedFrameWidth != 0 && rotatedFrameHeight != 0) {
      eglRenderer.setLayoutAspectRatio(getMeasuredWidth() / (float) getMeasuredHeight());
    }
    updateSurfaceSize();
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    ThreadUtils.checkIsOnMainThread();
    eglRenderer.setLayoutAspectRatio((right - left) / (float) (bottom - top));
    updateSurfaceSize();
  }

  private void updateSurfaceSize() {
    ThreadUtils.checkIsOnMainThread();
    if (enableFixedSize && rotatedFrameWidth != 0 && rotatedFrameHeight != 0 && getWidth() != 0
        && getHeight() != 0) {
      final float layoutAspectRatio = getWidth() / (float) getHeight();
      final float frameAspectRatio = rotatedFrameWidth / (float) rotatedFrameHeight;
      final int drawnFrameWidth;
      final int drawnFrameHeight;
      if (frameAspectRatio > layoutAspectRatio) {
        drawnFrameWidth = (int) (rotatedFrameHeight * layoutAspectRatio);
        drawnFrameHeight = rotatedFrameHeight;
      } else {
        drawnFrameWidth = rotatedFrameWidth;
        drawnFrameHeight = (int) (rotatedFrameWidth / layoutAspectRatio);
      }
      // Aspect ratio of the drawn frame and the view is the same.
      final int width = Math.min(getWidth(), drawnFrameWidth);
      final int height = Math.min(getHeight(), drawnFrameHeight);
      logD("updateSurfaceSize. Layout size: " + getWidth() + "x" + getHeight() + ", frame size: "
          + rotatedFrameWidth + "x" + rotatedFrameHeight + ", requested surface size: " + width
          + "x" + height + ", old surface size: " + surfaceWidth + "x" + surfaceHeight);
      if (width != surfaceWidth || height != surfaceHeight) {
        surfaceWidth = width;
        surfaceHeight = height;
        getHolder().setFixedSize(width, height);
      }
    } else {
      surfaceWidth = surfaceHeight = 0;
      getHolder().setSizeFromLayout();
    }
  }

  // SurfaceHolder.Callback interface.
  @Override
  public void surfaceCreated(final SurfaceHolder holder) {
    ThreadUtils.checkIsOnMainThread();
    surfaceWidth = surfaceHeight = 0;
    updateSurfaceSize();
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {}

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

  private String getResourceName() {
    try {
      return getResources().getResourceEntryName(getId());
    } catch (NotFoundException e) {
      return "";
    }
  }

  /**
   * Post a task to clear the SurfaceView to a transparent uniform color.
   */
  public void clearImage() {
    eglRenderer.clearImage();
  }

  @Override
  public void onFirstFrameRendered() {
    if (rendererEvents != null) {
      rendererEvents.onFirstFrameRendered();
    }
  }

  int textureRotation;
  @Override
  public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {
    if (rendererEvents != null) {
      rendererEvents.onFrameResolutionChanged(videoWidth, videoHeight, rotation);
    }
    textureRotation = rotation;
    int rotatedWidth, rotatedHeight;

    if (rotateTextureWitchScreen) {
      if (isCamera) {
        onRotationChanged();
      }
      if (useCameraRotation) {
        rotatedWidth = screenRotation == 0 ? videoHeight : videoWidth;
        rotatedHeight = screenRotation == 0 ? videoWidth : videoHeight;
      } else {
        rotatedWidth = textureRotation == 0 || textureRotation == 180 || textureRotation == -180 ? videoWidth : videoHeight;
        rotatedHeight = textureRotation == 0 || textureRotation == 180 || textureRotation == -180 ? videoHeight : videoWidth;
      }
    } else {
      if (isCamera) {
        eglRenderer.setRotation(-OrientationHelper.cameraRotation);
      }
      rotation -= OrientationHelper.cameraOrientation;
      rotatedWidth = rotation == 0 || rotation == 180 || rotation == -180 ? videoWidth : videoHeight;
      rotatedHeight = rotation == 0 || rotation == 180 || rotation == -180? videoHeight : videoWidth;
    }
    // run immediately if possible for ui thread tests
    synchronized (eglRenderer.layoutLock) {
      if (updateScreenRunnable != null) {
        AndroidUtilities.cancelRunOnUIThread(updateScreenRunnable);
      }
      postOrRun(updateScreenRunnable = () -> {
        updateScreenRunnable = null;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;

        rotatedFrameWidth = rotatedWidth;
        rotatedFrameHeight = rotatedHeight;

        updateSurfaceSize();
        requestLayout();
      });
    }
  }

  public boolean isFirstFrameRendered() {
    return eglRenderer.isFirstFrameRendered;
  }

  public void setScreenRotation(int screenRotation) {
    this.screenRotation = screenRotation;
    onRotationChanged();
    updateVideoSizes();
  }

  private void updateVideoSizes() {
    if (videoHeight != 0 && videoWidth != 0) {
      int rotatedWidth;
      int rotatedHeight;
      if (rotateTextureWitchScreen) {
        if (useCameraRotation) {
          rotatedWidth = screenRotation == 0 ? videoHeight : videoWidth;
          rotatedHeight = screenRotation == 0 ? videoWidth : videoHeight;
        } else {
          rotatedWidth = textureRotation == 0 || textureRotation == 180 || textureRotation == -180 ? videoWidth : videoHeight;
          rotatedHeight = textureRotation == 0 || textureRotation == 180 || textureRotation == -180 ? videoHeight : videoWidth;
        }
      } else {
        int rotation = textureRotation;
        rotation -= OrientationHelper.cameraOrientation;
        rotatedWidth = rotation == 0 || rotation == 180 || rotation == -180 ? videoWidth : videoHeight;
        rotatedHeight = rotation == 0 || rotation == 180 || rotation == -180 ? videoHeight : videoWidth;

      }
      if (rotatedFrameWidth != rotatedWidth || rotatedFrameHeight != rotatedHeight) {
        synchronized (eglRenderer.layoutLock) {
          if (updateScreenRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(updateScreenRunnable);
          }
          postOrRun(updateScreenRunnable = () -> {
            updateScreenRunnable = null;

            rotatedFrameWidth = rotatedWidth;
            rotatedFrameHeight = rotatedHeight;

            updateSurfaceSize();
            requestLayout();
          });
        }
      }
    }
  }

  public void setRotateTextureWithScreen(boolean rotateTextureWitchScreen) {
    if (this.rotateTextureWitchScreen != rotateTextureWitchScreen) {
      this.rotateTextureWitchScreen = rotateTextureWitchScreen;
      requestLayout();
    }
  }

  boolean useCameraRotation;

  public void setUseCameraRotation(boolean useCameraRotation) {
    if (this.useCameraRotation != useCameraRotation) {
      this.useCameraRotation = useCameraRotation;
      onRotationChanged();
      updateVideoSizes();
    }
  }
  private void onRotationChanged() {
    int rotation = useCameraRotation ? OrientationHelper.cameraOrientation : 0;
    if (mirror) {
      rotation = 360 - rotation;
    }
    int r = -rotation;
    if (useCameraRotation) {
      if (screenRotation == 1) {
        r += mirror ? 90 : -90;
      } else if (screenRotation == 3) {
        r += mirror ? 270 : -270;
      }
    }

    eglRenderer.setRotation(r);
    eglRenderer.setMirror(mirror);
  }

  private void postOrRun(Runnable r) {
    if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
      r.run();
    } else {
      post(r);
    }
  }

  private void logD(String string) {
    Logging.d(TAG, resourceName + ": " + string);
  }

  public void setMaxTextureSize(int maxTextureSize) {
    this.maxTextureSize = maxTextureSize;
  }
}

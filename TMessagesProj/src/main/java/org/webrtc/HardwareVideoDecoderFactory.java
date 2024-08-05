/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.media.MediaCodecInfo;

import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VoIPService;

import androidx.annotation.Nullable;

import ru.sberdevices.sbdv.SbdvServiceLocator;
import ru.sberdevices.sbdv.util.DeviceUtils;

import static org.webrtc.MediaCodecUtils.AMLOGIC_PREFIX;

/** Factory for Android hardware VideoDecoders. */
public class HardwareVideoDecoderFactory extends MediaCodecVideoDecoderFactory {
  private final static Predicate<MediaCodecInfo> defaultAllowedPredicate =
      new Predicate<MediaCodecInfo>() {
        @Override
        public boolean test(MediaCodecInfo arg) {
            if (!MediaCodecUtils.isHardwareAccelerated(arg)) {
                return false;
            }
            String[] types = arg.getSupportedTypes();
            if (types == null || types.length == 0) {
                return false;
            }
            Instance.ServerConfig config = Instance.getGlobalServerConfig();
            for (int a = 0; a < types.length; a++) {
                switch (types[a]) {
                    case "video/x-vnd.on2.vp8":
                        return DeviceUtils.isHuawei();
                    case "video/x-vnd.on2.vp9":
                        // There are problems with amlogic and huawei VP9 decoders. See XBUGS-2677
                        return false;
                    case "video/avc":
                        return SbdvServiceLocator.getConfig().getVoipH264Enabled();
                    case "video/hevc":
                        // XBUGS-5243: There is a problem with OMX.amlogic.video.encoder.hevc decoder latency. Use software alternative for now.
                        boolean isAmlogic = arg.getName().startsWith(AMLOGIC_PREFIX);
                        return SbdvServiceLocator.getConfig().getVoipH265Enabled() && !isAmlogic;
                }
            }
            return true;
        }
      };

  /** Creates a HardwareVideoDecoderFactory that does not use surface textures. */
  @Deprecated // Not removed yet to avoid breaking callers.
  public HardwareVideoDecoderFactory() {
    this(null);
  }

  /**
   * Creates a HardwareVideoDecoderFactory that supports surface texture rendering.
   *
   * @param sharedContext The textures generated will be accessible from this context. May be null,
   *                      this disables texture support.
   */
  public HardwareVideoDecoderFactory(@Nullable EglBase.Context sharedContext) {
    this(sharedContext, /* codecAllowedPredicate= */ null);
  }

  /**
   * Creates a HardwareVideoDecoderFactory that supports surface texture rendering.
   *
   * @param sharedContext The textures generated will be accessible from this context. May be null,
   *                      this disables texture support.
   * @param codecAllowedPredicate predicate to filter codecs. It is combined with the default
   *                              predicate that only allows hardware codecs.
   */
  public HardwareVideoDecoderFactory(@Nullable EglBase.Context sharedContext,
      @Nullable Predicate<MediaCodecInfo> codecAllowedPredicate) {
    super(sharedContext,
        (codecAllowedPredicate == null ? defaultAllowedPredicate
                                       : codecAllowedPredicate.and(defaultAllowedPredicate)));
  }
}

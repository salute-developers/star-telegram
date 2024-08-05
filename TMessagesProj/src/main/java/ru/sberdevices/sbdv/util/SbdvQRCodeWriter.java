/*
 * Copyright 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.sberdevices.sbdv.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.GradientDrawable;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import java.util.Arrays;
import java.util.Map;

/**
 * This object renders a QR Code as a BitMatrix 2D array of greyscale values.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 *
 * This class is refactored version of com.google.zxing.qrcode.QRCodeWriter
 */
public final class SbdvQRCodeWriter {
    private ByteMatrix input;
    private final float[] radii = new float[8];
    private int sideQuadSize;

    private final int backgroundColor = 0x00000000;
    private final int brushColor = 0xF5FFFFFF;

    public Bitmap encode(String contents, int width, int height, Map<EncodeHintType, ?> hints, Bitmap bitmap) throws WriterException {

        if (contents.isEmpty()) {
            throw new IllegalArgumentException("Found empty contents");
        }

        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Requested dimensions are too small: " + width + 'x' + height);
        }

        ErrorCorrectionLevel errorCorrectionLevel = ErrorCorrectionLevel.L;
        if (hints != null && hints.containsKey(EncodeHintType.ERROR_CORRECTION)) {
            errorCorrectionLevel = ErrorCorrectionLevel.valueOf(hints.get(EncodeHintType.ERROR_CORRECTION).toString());
        }

        QRCode code = Encoder.encode(contents, errorCorrectionLevel, hints);

        input = code.getMatrix();
        if (input == null) {
            throw new IllegalStateException();
        }
        int inputWidth = input.getWidth();
        int inputHeight = input.getHeight();

        for (int x = 0; x < inputWidth; x++) {
            if (has(x, 0)) {
                sideQuadSize++;
            } else {
                break;
            }
        }

        int qrWidth = inputWidth;
        int qrHeight = inputHeight;
        int outputWidth = Math.max(width, qrWidth);
        int outputHeight = Math.max(height, qrHeight);

        int multiple = Math.min(outputWidth / qrWidth, outputHeight / qrHeight);

        int padding = 16;

        int size = multiple * inputWidth + padding * 2;
        if (bitmap == null || bitmap.getWidth() != size) {
            bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        }
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(backgroundColor);
        Paint qrCodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        qrCodePaint.setColor(backgroundColor);

        GradientDrawable rect = new GradientDrawable();
        rect.setShape(GradientDrawable.RECTANGLE);
        rect.setCornerRadii(radii);

        for (int a = 0; a < 3; a++) {
            int x, y;
            if (a == 0) {
                x = padding;
                y = padding;
            } else if (a == 1) {
                x = size - sideQuadSize * multiple - padding;
                y = padding;
            } else {
                x = padding;
                y = size - sideQuadSize * multiple - padding;
            }

            Paint cornerQrSquarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            float r = (sideQuadSize * multiple) / (3.0f * 2);
            cornerQrSquarePaint.setColor(brushColor);
            canvas.drawRoundRect(x, y, x + sideQuadSize * multiple, y + sideQuadSize * multiple, r, r, cornerQrSquarePaint);

            r = (sideQuadSize * multiple) / (4.0f * 2);
            cornerQrSquarePaint.setColor(backgroundColor);
            cornerQrSquarePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawRoundRect(x + multiple, y + multiple, x + (sideQuadSize - 1) * multiple, y + (sideQuadSize - 1) * multiple, r, r, cornerQrSquarePaint);

            r = ((sideQuadSize - 2) * multiple) / (4.0f * 2);
            cornerQrSquarePaint.setColor(brushColor);
            cornerQrSquarePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT));
            canvas.drawRoundRect(x + multiple * 2, y + multiple * 2, x + (sideQuadSize - 2) * multiple, y + (sideQuadSize - 2) * multiple, r, r, cornerQrSquarePaint);
        }
        float r = multiple / 2.0f;

        for (int y = 0, outputY = padding; y < inputHeight; y++, outputY += multiple) {
            for (int x = 0, outputX = padding; x < inputWidth; x++, outputX += multiple) {
                if (has(x, y)) {
                    Arrays.fill(radii, r);
                    if (has(x, y - 1)) {
                        radii[0] = radii[1] = 0;
                        radii[2] = radii[3] = 0;
                    }
                    if (has(x, y + 1)) {
                        radii[6] = radii[7] = 0;
                        radii[4] = radii[5] = 0;
                    }
                    if (has(x - 1, y)) {
                        radii[0] = radii[1] = 0;
                        radii[6] = radii[7] = 0;
                    }
                    if (has(x + 1, y)) {
                        radii[2] = radii[3] = 0;
                        radii[4] = radii[5] = 0;
                    }
                    rect.setColor(brushColor);
                    rect.setBounds(outputX, outputY, outputX + multiple, outputY + multiple);
                    rect.draw(canvas);
                } else {
                    boolean has = false;
                    Arrays.fill(radii, 0);
                    if (has(x - 1, y - 1) && has(x - 1, y) && has(x, y - 1)) {
                        radii[0] = radii[1] = r;
                        has = true;
                    }
                    if (has(x + 1, y - 1) && has(x + 1, y) && has(x, y - 1)) {
                        radii[2] = radii[3] = r;
                        has = true;
                    }
                    if (has(x - 1, y + 1) && has(x - 1, y) && has(x, y + 1)) {
                        radii[6] = radii[7] = r;
                        has = true;
                    }
                    if (has(x + 1, y + 1) && has(x + 1, y) && has(x, y + 1)) {
                        radii[4] = radii[5] = r;
                        has = true;
                    }
                    if (has) {
                        canvas.drawRect(outputX, outputY, outputX + multiple, outputY + multiple, qrCodePaint);
                        rect.setColor(backgroundColor);
                        rect.setBounds(outputX, outputY, outputX + multiple, outputY + multiple);
                        rect.draw(canvas);
                    }
                }
            }
        }

        return bitmap;
    }

    private boolean has(int x, int y) {
        if ((x < sideQuadSize || x >= input.getWidth() - sideQuadSize) && y < sideQuadSize) {
            return false;
        }
        if (x < sideQuadSize && y >= input.getHeight() - sideQuadSize) {
            return false;
        }
        return x >= 0 && y >= 0 && x < input.getWidth() && y < input.getHeight() && input.get(x, y) == 1;
    }
}
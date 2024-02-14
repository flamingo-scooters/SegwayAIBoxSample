/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.segway.robot.sample.aibox.segmentation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.segmenter.ColoredLabel;
import org.tensorflow.lite.task.vision.segmenter.Segmentation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class OverlayView extends View {

    public OverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public static final int ALPHA_COLOR = 128;

    private Bitmap scaleBitmap;
    private OverlayViewListener listener;

    void setOnOverlayViewListener(OverlayViewListener listener) {
        this.listener = listener;
    }

    void clear() {
        scaleBitmap = null;
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (scaleBitmap != null) {
            canvas.drawBitmap(scaleBitmap, 0f, 0f, null);
        }
    }

    void setResults(
            List<Segmentation> segmentResult,
            int imageHeight,
            int imageWidth
    ) {
        if (segmentResult != null && segmentResult.size() > 0) {
            List<ColoredLabel> coloredLabels = segmentResult.get(0).getColoredLabels();
            List<ColorLabel> colorLabels = new ArrayList<>(coloredLabels.size());
            for (int i = 0; i < coloredLabels.size(); i++) {
                ColoredLabel coloredLabel = coloredLabels.get(i);
                colorLabels.add(new ColorLabel(
                        i,
                        coloredLabel.getlabel(),
                        coloredLabel.getArgb()
                ));
            }

            // Create the mask bitmap with colors and the set of detected labels.
            // We only need the first mask for this sample because we are using
            // the OutputType CATEGORY_MASK, which only provides a single mask.
            TensorImage maskTensor = segmentResult.get(0).getMasks().get(0);
            byte[] maskArray = maskTensor.getBuffer().array();
            int[] pixels = new int[maskArray.length];

            for (int i = 0; i < maskArray.length; i++) {
                // Set isExist flag to true if any pixel contains this color.
                if (colorLabels != null) {
                    ColorLabel colorLabel = colorLabels.get((int) maskArray[i]);
                    colorLabel.isExist = true;
                    int color = colorLabel.getColor();
                    pixels[i] = color;
                }
            }

            Bitmap image = Bitmap.createBitmap(
                    pixels,
                    maskTensor.getWidth(),
                    maskTensor.getHeight(),
                    Bitmap.Config.ARGB_8888
            );

            // PreviewView is in FILL_START mode. So we need to scale up the bounding
            // box to match with the size that the captured images will be displayed.
            float scaleFactor = Math.max(getWidth() * 1f / imageWidth, getHeight() * 1f / imageHeight);
            int scaleWidth = Math.round(imageWidth * scaleFactor);
            int scaleHeight = Math.round(imageHeight * scaleFactor);

            scaleBitmap = Bitmap.createScaledBitmap(image, scaleWidth, scaleHeight, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                listener.onLabels(colorLabels.stream().filter(new Predicate<ColorLabel>() {
                    @Override
                    public boolean test(ColorLabel colorLabel) {
                        return colorLabel.isExist;
                    }
                }).collect(Collectors.toList()));
            }
        }
    }

    interface OverlayViewListener {
        void onLabels(List<ColorLabel> colorLabels);
    }


    class ColorLabel {

        int id;
        String label;
        int rgbColor;
        boolean isExist;

        public ColorLabel(int id,
                          String label,
                          int rgbColor) {
            this.id = id;
            this.label = label;
            this.rgbColor = rgbColor;
            this.isExist = false;
        }

        int getColor() {
            // Use completely transparent for the background color.
            if (id == 0) {
                return Color.TRANSPARENT;
            } else {
                return Color.argb(
                        ALPHA_COLOR,
                        Color.red(rgbColor),
                        Color.green(rgbColor),
                        Color.blue(rgbColor)
                );
            }
        }
    }
}

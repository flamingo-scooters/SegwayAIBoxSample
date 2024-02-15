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
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
//import androidx.annotation.RequiresApi
//import androidx.core.graphics.get
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.segmenter.ImageSegmenter;
import org.tensorflow.lite.task.vision.segmenter.OutputType;
import org.tensorflow.lite.task.vision.segmenter.Segmentation;

import java.io.IOException;
import java.lang.Exception;
import java.util.*;

/**
 * Class responsible to run the Image Segmentation model. more information about the DeepLab model
 * being used can be found here:
 * https://ai.googleblog.com/2018/03/semantic-image-segmentation-with.html
 * https://github.com/tensorflow/models/tree/master/research/deeplab
 * <p>
 * Label names: 'background', 'aeroplane', 'bicycle', 'bird', 'boat', 'bottle', 'bus', 'car', 'cat',
 * 'chair', 'cow', 'diningtable', 'dog', 'horse', 'motorbike', 'person', 'pottedplant', 'sheep',
 * 'sofa', 'train', 'tv'
 */
public class ImageSegmentationHelper {

    public ImageSegmentationHelper(int numThreads, int currentDelegate, Context context, SegmentationListener imageSegmentationListener) {
        this.numThreads = numThreads;
        this.currentDelegate = currentDelegate;
        this.context = context;
        this.imageSegmentationListener = imageSegmentationListener;
        setupImageSegmenter();
    }

    int numThreads = 2;
    int currentDelegate = 0;
    private Context context;
    private SegmentationListener imageSegmentationListener;

    private ImageSegmenter imageSegmenter;


    void clearImageSegmenter() {
        imageSegmenter = null;
    }

    private void setupImageSegmenter() {
        // Create the base options for the segment
        ImageSegmenter.ImageSegmenterOptions.Builder optionsBuilder =
                ImageSegmenter.ImageSegmenterOptions.builder();

        // Set general segmentation options, including number of used threads
        BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads);

        // Use the specified hardware for running the model. Default to CPU
        if (DELEGATE_GPU == currentDelegate) {
            if (new CompatibilityList().isDelegateSupportedOnThisDevice()) {
                baseOptionsBuilder.useGpu();
            } else {
                imageSegmentationListener.onError("GPU is not supported on this device");
            }
        } else if (DELEGATE_NNAPI == currentDelegate) {
            baseOptionsBuilder.useNnapi();
        }

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build());

        /*
        CATEGORY_MASK is being specifically used to predict the available objects
        based on individual pixels in this sample. The other option available for
        OutputType, CONFIDENCE_MAP, provides a gray scale mapping of the image
        where each pixel has a confidence score applied to it from 0.0f to 1.0f
         */
        optionsBuilder.setOutputType(OutputType.CATEGORY_MASK);
        try {
            imageSegmenter =
                    ImageSegmenter.createFromFileAndOptions(
                            context,
                            MODEL_DEEPLABV3,
                            optionsBuilder.build()
                    );
        } catch (IllegalStateException | IOException e) {
            imageSegmentationListener.onError(
                    "Image segmentation failed to initialize. See error logs for details"
            );
            Log.e(TAG, "TFLite failed to load model with error: " + e.getMessage());
        }
    }

    //    @RequiresApi(Build.VERSION_CODES.Q)
    public void segment(Bitmap image, int imageRotation) {

        if (imageSegmenter == null) {
            setupImageSegmenter();
        }

        // Inference time is the difference between the system time at the start and finish of the
        // process
        long inferenceTime = SystemClock.uptimeMillis();

        // Create preprocessor for the image.
        // See https://www.tensorflow.org/lite/inference_with_metadata/
        //            lite_support#imageprocessor_architecture
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new Rot90Op(-imageRotation / 90))
                        .build();

        // Preprocess the image and convert it into a TensorImage for segmentation.
        TensorImage tensorImage = imageProcessor.process(TensorImage.fromBitmap(image));

        List<Segmentation> segmentResult = imageSegmenter.segment(tensorImage);
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime;

        imageSegmentationListener.onResults(
                segmentResult,
                inferenceTime,
                tensorImage.getHeight(),
                tensorImage.getWidth()
        );
    }

    static final int DELEGATE_CPU = 0;
    static final int DELEGATE_GPU = 1;
    static final int DELEGATE_NNAPI = 2;
    static final String MODEL_DEEPLABV3 = "deeplabv3.tflite";

    private static final String TAG = "bg-image-segmentation";

    public interface SegmentationListener {
        void onError(String error);

        void onResults(
                List<? extends Segmentation> results,
                long inferenceTime,
                int imageHeight,
                int imageWidth
        );
    }
}

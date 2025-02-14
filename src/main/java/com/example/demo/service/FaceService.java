package com.example.demo.service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.springframework.stereotype.Service;

@Service
public class FaceService {

    static {
        // Load the OpenCV native library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    private final CascadeClassifier faceDetector;

    public FaceService() {
        faceDetector = loadCascadeClassifier();
    }

    /**
     * Loads the Haar Cascade XML file from the classpath by copying it
     * to a temporary file and then initializing the CascadeClassifier with it.
     */
    private CascadeClassifier loadCascadeClassifier() {
        try (InputStream is = getClass().getResourceAsStream("/haarcascade_frontalface_alt.xml")) {
            if (is == null) {
                throw new RuntimeException("Cascade Classifier XML file not found in classpath. " +
                        "Please ensure 'haarcascade_frontalface_alt.xml' is placed in src/main/resources.");
            }
            File tempFile = File.createTempFile("haarcascade_frontalface_alt", ".xml");
            tempFile.deleteOnExit();
            Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            CascadeClassifier classifier = new CascadeClassifier(tempFile.getAbsolutePath());
            if (classifier.empty()) {
                throw new RuntimeException("Failed to load CascadeClassifier from file: " 
                        + tempFile.getAbsolutePath());
            }
            return classifier;
        } catch (IOException e) {
            throw new RuntimeException("Error loading cascade classifier", e);
        }
    }

    /**
     * Detects a face in the given BufferedImage, extracts the face region,
     * resizes it to 150x150 for normalization, and returns it as a Mat.
     */
    public Mat detectAndAlignFace(BufferedImage image) {
        Mat matImage = bufferedImageToMat(image);
        MatOfRect faceDetections = new MatOfRect();
        faceDetector.detectMultiScale(matImage, faceDetections);

        if (faceDetections.empty()) {
            return null;
        }

        // Use the first detected face.
        Rect faceRect = faceDetections.toArray()[0];
        Mat faceMat = new Mat(matImage, faceRect);
        Mat resizedFace = new Mat();
        Imgproc.resize(faceMat, resizedFace, new Size(150, 150)); // Resize for normalization
        return resizedFace;
    }

    /**
     * Extracts normalized histogram features from the face Mat.
     * The method converts the face image to grayscale and then computes
     * a histogram. The histogram is then converted to CV_64F so that it can
     * be safely extracted into a double[].
     */
    public double[] extractFaceFeatures(Mat faceMat) {
        // Convert the face image to grayscale if it has 3 channels.
        Mat grayFace = new Mat();
        if (faceMat.channels() == 3) {
            Imgproc.cvtColor(faceMat, grayFace, Imgproc.COLOR_BGR2GRAY);
        } else {
            grayFace = faceMat;
        }

        // Compute the histogram on the grayscale image.
        Mat hist = new Mat();
        Imgproc.calcHist(
                Collections.singletonList(grayFace),
                new MatOfInt(0),
                new Mat(),
                hist,
                new MatOfInt(128),
                new org.opencv.core.MatOfFloat(0, 256)
        );

        // Convert the histogram from CV_32F to CV_64F so that we can extract it into a double array.
        Mat histDouble = new Mat();
        hist.convertTo(histDouble, CvType.CV_64F);

        double[] histogram = new double[(int) histDouble.total()];
        histDouble.get(0, 0, histogram);
        return normalize(histogram);
    }

    /**
     * Normalizes an array so that the sum of its elements equals 1.
     */
    public double[] normalize(double[] data) {
        double sum = Arrays.stream(data).sum();
        if (sum == 0) {
            return data;
        }
        return Arrays.stream(data)
                     .map(d -> d / sum)
                     .toArray();
    }

    /**
     * Computes the Euclidean distance between two feature arrays.
     * If the arrays have different lengths, a large value is returned,
     * effectively skipping the comparison.
     */
    public double computeEuclideanDistance(double[] features1, double[] features2) {
        if (features1.length != features2.length) {
            System.err.println("Feature arrays have different lengths: " 
                    + features1.length + " vs " + features2.length);
            return Double.MAX_VALUE; // Return a very large distance
        }
        double sum = 0;
        for (int i = 0; i < features1.length; i++) {
            double diff = features1[i] - features2[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /**
     * Converts a BufferedImage to an OpenCV Mat.
     * This method ensures the image is converted to 3-byte BGR (TYPE_3BYTE_BGR)
     * before extracting the pixel data.
     */
    private Mat bufferedImageToMat(BufferedImage image) {
        // Convert image to 3-byte BGR if it is not already.
        if (image.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            BufferedImage convertedImage = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g2d = convertedImage.createGraphics();
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();
            image = convertedImage;
        }
        
        byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        // Create a Mat of type CV_8UC3 (8-bit unsigned, 3 channels)
        Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, pixels);
        return mat;
    }
}

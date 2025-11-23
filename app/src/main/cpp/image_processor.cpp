#include "image_processor.h"
#include <opencv2/imgproc.hpp>
#include <android/log.h>

#define LOG_TAG "ImageProcessor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

const int ImageProcessor::CANNY_LOWER_THRESHOLD = 50;
const int ImageProcessor::CANNY_UPPER_THRESHOLD = 150;

cv::Mat ImageProcessor::convertToGrayscale(const cv::Mat& input) {
    if (input.empty()) {
        LOGE("Input image is empty for grayscale conversion");
        return input;
    }
    
    LOGI("convertToGrayscale: input size=%dx%d, channels=%d", input.cols, input.rows, input.channels());
    
    cv::Mat gray, result;
    try {
        if (input.channels() == 4) {
            cv::cvtColor(input, gray, cv::COLOR_RGBA2GRAY);
        } else if (input.channels() == 3) {
            cv::cvtColor(input, gray, cv::COLOR_RGB2GRAY);
        } else if (input.channels() == 1) {
            gray = input.clone();
        } else {
            LOGE("Unsupported number of channels: %d", input.channels());
            return input;
        }
        
        LOGI("After cvtColor to gray: size=%dx%d, channels=%d", gray.cols, gray.rows, gray.channels());
        
        // Convert back to 4-channel for rendering
        cv::cvtColor(gray, result, cv::COLOR_GRAY2RGBA);
        LOGI("After cvtColor to RGBA: size=%dx%d, channels=%d", result.cols, result.rows, result.channels());
        return result;
    } catch (const cv::Exception& e) {
        LOGE("Error in convertToGrayscale: %s", e.what());
        return input;
    }
}

cv::Mat ImageProcessor::applyCanny(const cv::Mat& input) {
    if (input.empty()) {
        LOGE("Input image is empty for Canny edge detection");
        return input;
    }
    
    LOGI("applyCanny: input size=%dx%d, channels=%d", input.cols, input.rows, input.channels());
    
    cv::Mat gray, edges, result;
    try {
        // Convert to grayscale if needed
        if (input.channels() == 4) {
            cv::cvtColor(input, gray, cv::COLOR_RGBA2GRAY);
        } else if (input.channels() == 3) {
            cv::cvtColor(input, gray, cv::COLOR_RGB2GRAY);
        } else if (input.channels() == 1) {
            gray = input.clone();
        } else {
            LOGE("Unsupported number of channels: %d", input.channels());
            return input;
        }
        
        LOGI("After cvtColor to gray: size=%dx%d, channels=%d", gray.cols, gray.rows, gray.channels());
        
        // Blur to reduce noise
        cv::GaussianBlur(gray, gray, cv::Size(5, 5), 1.5);
        LOGI("After GaussianBlur: size=%dx%d, channels=%d", gray.cols, gray.rows, gray.channels());
        
        // Apply Canny edge detection
        cv::Canny(gray, edges, CANNY_LOWER_THRESHOLD, CANNY_UPPER_THRESHOLD);
        LOGI("After Canny (thresholds %d-%d): size=%dx%d, channels=%d", 
             CANNY_LOWER_THRESHOLD, CANNY_UPPER_THRESHOLD, edges.cols, edges.rows, edges.channels());
        
        // Convert back to RGBA
        cv::cvtColor(edges, result, cv::COLOR_GRAY2RGBA);
        LOGI("After cvtColor to RGBA: size=%dx%d, channels=%d", result.cols, result.rows, result.channels());
        return result;
    } catch (const cv::Exception& e) {
        LOGE("Error in applyCanny: %s", e.what());
        return input;
    }
}

cv::Mat ImageProcessor::processImage(const cv::Mat& input, int filterType) {
    if (input.empty()) {
        LOGE("Input image is empty");
        return input;
    }
    
    switch (filterType) {
        case 0: {
            // Grayscale filter
            return convertToGrayscale(input);
        }
        case 1: {
            // Canny Edge Detection filter
            return applyCanny(input);
        }
        case 2: {
            // Original (no filter)
            return input.clone();
        }
        default: {
            LOGE("Unknown filter type: %d", filterType);
            return input.clone();
        }
    }
}

#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <android/bitmap.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/core/ocl.hpp>
#include <cstring>
#include <queue>
#include <mutex>
#include <thread>
#include <condition_variable>
#include <memory>
#include <chrono>
#include <cinttypes>
#include "image_processor.h"

#define LOG_TAG "EdgeDetectionJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ==================== Global Variables ====================

static EGLDisplay eglDisplay = EGL_NO_DISPLAY;
static EGLSurface eglSurface = EGL_NO_SURFACE;
static EGLContext eglContext = EGL_NO_CONTEXT;
static ANativeWindow* currentWindow = nullptr;

static GLuint textureId;
static GLuint program;
static GLuint programOriginal;
static GLuint programGrayscale;
static GLuint programEdge;
static GLint positionHandle;
static GLint texCoordHandle;
static GLint texSizeUniform;

static cv::Mat frameBuffer;
static std::mutex frameMutex;
static int currentFilterType = 1;
static int lastAppliedFilterType = -1;

// ==================== Camera Frame Streaming ====================

// Frame processing configuration
static const size_t MAX_FRAME_QUEUE_SIZE = 5;
static const int CANNY_LOW_THRESHOLD = 50;
static const int CANNY_HIGH_THRESHOLD = 150;
static const int CANNY_KERNEL_SIZE = 3;
static const int GAUSSIAN_KERNEL_SIZE = 5;
static const double GAUSSIAN_SIGMA = 1.5;

// Frame queue for streaming
struct Frame {
    std::vector<uint8_t> data;
    int width;
    int height;
    int format; // 0 = YUV/NV21, 1 = RGBA
    int64_t timestamp;
};

static std::queue<std::shared_ptr<Frame>> frameQueue;
static std::mutex queueMutex;
static std::condition_variable frameAvailable;
static bool processingEnabled = false;
static int64_t frameProcessingTimeMs = 0;
static int64_t frameCount = 0;

// ==================== Forward Declarations ====================

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_processNV21Frame(
        JNIEnv *env,
        jclass clazz,
        jbyteArray frameData,
        jint width,
        jint height,
        jint filterType);

// ==================== OpenCV Initialization ====================

/**
 * Initialize OpenCV modules for efficient processing
 */
static bool initializeOpenCV() {
    try {
        // Disable OpenCL if available for consistent behavior
        cv::ocl::setUseOpenCL(false);
        
        LOGI("OpenCV initialized successfully");
        return true;
    } catch (const std::exception& e) {
        LOGE("Failed to initialize OpenCV: %s", e.what());
        return false;
    }
}

/**
 * JNI Method: Initialize OpenCV
 * Java signature: initializeOpenCV() -> boolean
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_initializeOpenCV(
        JNIEnv *env,
        jclass /* clazz */) {
    return initializeOpenCV() ? JNI_TRUE : JNI_FALSE;
}

// ==================== Shader Code ====================

const char* VERTEX_SHADER = R"glsl(
    attribute vec4 vPosition;
    attribute vec2 vTexCoord;
    varying vec2 texCoord;
    void main() {
        gl_Position = vPosition;
        texCoord = vTexCoord;
    }
)glsl";

const char* FRAGMENT_SHADER_ORIGINAL = R"glsl(
    precision mediump float;
    varying vec2 texCoord;
    uniform sampler2D sTexture;
    void main() {
        gl_FragColor = texture2D(sTexture, texCoord);
    }
)glsl";

const char* FRAGMENT_SHADER_GRAYSCALE = R"glsl(
    precision mediump float;
    varying vec2 texCoord;
    uniform sampler2D sTexture;
    void main() {
        vec4 color = texture2D(sTexture, texCoord);
        float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        gl_FragColor = vec4(gray, gray, gray, color.a);
    }
)glsl";

const char* FRAGMENT_SHADER_EDGE = R"glsl(
    precision mediump float;
    varying vec2 texCoord;
    uniform sampler2D sTexture;
    uniform vec2 texSize;
    
    void main() {
        vec2 texel = 1.0 / texSize;
        
        // Sobel edge detection
        float tl = texture2D(sTexture, texCoord + vec2(-texel.x, texel.y)).r;
        float t  = texture2D(sTexture, texCoord + vec2(0.0, texel.y)).r;
        float tr = texture2D(sTexture, texCoord + vec2(texel.x, texel.y)).r;
        float l  = texture2D(sTexture, texCoord + vec2(-texel.x, 0.0)).r;
        float r  = texture2D(sTexture, texCoord + vec2(texel.x, 0.0)).r;
        float bl = texture2D(sTexture, texCoord + vec2(-texel.x, -texel.y)).r;
        float b  = texture2D(sTexture, texCoord + vec2(0.0, -texel.y)).r;
        float br = texture2D(sTexture, texCoord + vec2(texel.x, -texel.y)).r;
        
        float sx = -tl - 2.0*l - bl + tr + 2.0*r + br;
        float sy = -tl - 2.0*t - tr + bl + 2.0*b + br;
        
        float edge = length(vec2(sx, sy));
        gl_FragColor = vec4(vec3(edge), 1.0);
    }
)glsl";

cv::Mat processFrameWithFilter(const cv::Mat& inputFrame, int filterType) {
    if (inputFrame.empty()) {
        LOGE("Input frame is empty");
        return inputFrame;
    }

    LOGI("processFrameWithFilter: input size=%dx%d, channels=%d, filterType=%d", 
         inputFrame.cols, inputFrame.rows, inputFrame.channels(), filterType);

    cv::Mat result;
    try {
        switch (filterType) {
            case 0: { // Grayscale
                LOGI("Applying GRAYSCALE filter");
                cv::Mat gray;
                cv::cvtColor(inputFrame, gray, cv::COLOR_RGBA2GRAY);
                LOGI("After cvtColor to gray: size=%dx%d, channels=%d", gray.cols, gray.rows, gray.channels());
                cv::cvtColor(gray, result, cv::COLOR_GRAY2RGBA);
                LOGI("After cvtColor to RGBA: size=%dx%d, channels=%d", result.cols, result.rows, result.channels());
                break;
            }
            case 1: { // Canny Edge Detection
                LOGI("Applying CANNY EDGE filter");
                cv::Mat gray;
                cv::cvtColor(inputFrame, gray, cv::COLOR_RGBA2GRAY);
                LOGI("After cvtColor to gray: size=%dx%d, channels=%d", gray.cols, gray.rows, gray.channels());
                
                // Apply Gaussian blur for better edge detection
                cv::GaussianBlur(gray, gray, cv::Size(5, 5), 1.5);
                LOGI("After GaussianBlur: size=%dx%d, channels=%d", gray.cols, gray.rows, gray.channels());
                
                cv::Mat edges;
                cv::Canny(gray, edges, 50, 150);
                LOGI("After Canny: size=%dx%d, channels=%d", edges.cols, edges.rows, edges.channels());
                
                // Convert back to RGBA
                cv::cvtColor(edges, result, cv::COLOR_GRAY2RGBA);
                LOGI("After cvtColor to RGBA: size=%dx%d, channels=%d", result.cols, result.rows, result.channels());
                break;
            }
            case 2: { // Original (no filter)
                LOGI("Applying ORIGINAL (no filter)");
                result = inputFrame.clone();
                break;
            }
            default: {
                LOGE("Unknown filter type: %d", filterType);
                result = inputFrame.clone();
                break;
            }
        }
    } catch (const cv::Exception& e) {
        LOGE("OpenCV error in processFrame: %s", e.what());
        result = inputFrame.clone();
    }
    
    return result;
}

GLuint loadShader(GLenum shaderType, const char* shaderSource) {
    GLuint shader = glCreateShader(shaderType);
    if (shader) {
        glShaderSource(shader, 1, &shaderSource, NULL);
        glCompileShader(shader);
        GLint compiled = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
        if (!compiled) {
            GLint infoLen = 0;
            glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
            if (infoLen) {
                char* buf = (char*) malloc(infoLen);
                if (buf) {
                    glGetShaderInfoLog(shader, infoLen, NULL, buf);
                    LOGE("Could not compile shader %d:\n%s", shaderType, buf);
                    free(buf);
                }
                glDeleteShader(shader);
                shader = 0;
            }
        }
    }
    return shader;
}

GLuint createProgram(const char* vertexSource, const char* fragmentSource) {
    GLuint vertexShader = loadShader(GL_VERTEX_SHADER, vertexSource);
    if (!vertexShader) return 0;
    GLuint fragmentShader = loadShader(GL_FRAGMENT_SHADER, fragmentSource);
    if (!fragmentShader) return 0;

    GLuint program = glCreateProgram();
    if (program) {
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        GLint linkStatus = GL_FALSE;
        glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);
        if (linkStatus != GL_TRUE) {
            GLint infoLen = 0;
            glGetProgramiv(program, GL_INFO_LOG_LENGTH, &infoLen);
            if (infoLen) {
                char* buf = (char*) malloc(infoLen);
                if (buf) {
                    glGetProgramInfoLog(program, infoLen, NULL, buf);
                    LOGE("Could not link program:\n%s", buf);
                    free(buf);
                }
            }
            glDeleteProgram(program);
            program = 0;
        }
    }
    return program;
}

// ==================== Filter Statistics & Monitoring ====================

/**
 * JNI Method: Get processing performance metrics
 * Returns detailed statistics about frame processing
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_getPerformanceMetrics(
        JNIEnv *env,
        jclass /* clazz */) {
    
    if (frameCount == 0) {
        return env->NewStringUTF("No frames processed yet");
    }
    
    long avgTimeMs = frameProcessingTimeMs / frameCount;
    double fps = frameCount > 0 ? (frameCount * 1000.0) / frameProcessingTimeMs : 0;
    
    char metrics[256];
    snprintf(metrics, sizeof(metrics), 
             "Frames: %lld | Total time: %lldms | Avg: %ldms | FPS: %.1f",
             frameCount, frameProcessingTimeMs, avgTimeMs, fps);
    
    return env->NewStringUTF(metrics);
}

// ==================== Filter Application Helpers ====================

/**
 * Helper: Apply filter to a Mat with automatic error handling
 */
static cv::Mat safeApplyFilter(const cv::Mat& input, int filterType) {
    if (input.empty()) {
        LOGE("Cannot apply filter to empty mat");
        return input;
    }
    
    try {
        return ImageProcessor::processImage(input, filterType);
    } catch (const cv::Exception& e) {
        LOGE("OpenCV error in filter application: %s", e.what());
        return input.clone();
    } catch (const std::exception& e) {
        LOGE("Error in filter application: %s", e.what());
        return input.clone();
    }
}

/**
 * JNI Method: Batch process multiple NV21 frames
 * Processes frames efficiently in sequence
 * Java signature: processBatchNV21(byte[][] frames, int width, int height, int filterType) -> byte[][]
 */
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_processBatchNV21(
        JNIEnv *env,
        jclass /* clazz */,
        jobjectArray frames,
        jint width,
        jint height,
        jint filterType) {
    
    if (frames == nullptr) {
        LOGE("Frames array is null");
        return nullptr;
    }
    
    jint batchSize = env->GetArrayLength(frames);
    LOGI("Starting batch NV21 processing: %d frames, %dx%d, filter=%d", 
         batchSize, width, height, filterType);
    
    // Create output array with same element type
    jclass byteArrayClass = env->FindClass("[B");
    if (byteArrayClass == nullptr) {
        LOGE("Failed to find byte array class");
        return nullptr;
    }
    
    jobjectArray resultArray = env->NewObjectArray(batchSize, byteArrayClass, nullptr);
    if (resultArray == nullptr) {
        LOGE("Failed to allocate result array");
        return nullptr;
    }
    
    for (jint i = 0; i < batchSize; i++) {
        jobject frameObj = env->GetObjectArrayElement(frames, i);
        if (frameObj == nullptr) {
            LOGD("Skipping null frame at index %d", i);
            continue;
        }
        
        jbyteArray frameArray = (jbyteArray)frameObj;
        
        // Process this frame using processNV21Frame
        jbyteArray processedFrame = Java_com_example_realtimeedgedetection_NativeImageProcessor_processNV21Frame(
            env, nullptr, frameArray, width, height, filterType);
        
        if (processedFrame != nullptr) {
            env->SetObjectArrayElement(resultArray, i, processedFrame);
            env->DeleteLocalRef(processedFrame);
        }
        
        env->DeleteLocalRef(frameObj);
    }
    
    LOGI("Batch processing completed: %d frames", batchSize);
    return resultArray;
}

void setupGraphics() {
    LOGI("setupGraphics - Creating shader programs");
    
    // Create programs for each filter
    programOriginal = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_ORIGINAL);
    programGrayscale = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_GRAYSCALE);
    programEdge = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EDGE);
    
    if (!programOriginal || !programGrayscale || !programEdge) {
        LOGE("Could not create shader programs.");
        return;
    }
    
    program = programOriginal;
    positionHandle = glGetAttribLocation(program, "vPosition");
    texCoordHandle = glGetAttribLocation(program, "vTexCoord");
    texSizeUniform = glGetUniformLocation(programEdge, "texSize");

    glGenTextures(1, &textureId);
    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    
    LOGI("Shader programs created successfully");
}

void render() {
    static const GLfloat vertices[] = {
        -1.0f, -1.0f,
         1.0f, -1.0f,
        -1.0f,  1.0f,
         1.0f,  1.0f,
    };
    static const GLfloat texCoords[] = {
        0.0f, 1.0f,
        1.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f,
    };

    // Select shader program based on filter type
    GLuint selectedProgram = programOriginal;
    switch (currentFilterType) {
        case 0: // Grayscale
            selectedProgram = programGrayscale;
            break;
        case 1: // Canny Edge
            selectedProgram = programEdge;
            break;
        case 2: // Original
        default:
            selectedProgram = programOriginal;
            break;
    }
    
    if (lastAppliedFilterType != currentFilterType) {
        program = selectedProgram;
        positionHandle = glGetAttribLocation(program, "vPosition");
        texCoordHandle = glGetAttribLocation(program, "vTexCoord");
        if (currentFilterType == 1) {
            texSizeUniform = glGetUniformLocation(program, "texSize");
        }
        lastAppliedFilterType = currentFilterType;
        LOGI("Filter switched to: %d", currentFilterType);
    }

    glUseProgram(program);

    glVertexAttribPointer(positionHandle, 2, GL_FLOAT, GL_FALSE, 0, vertices);
    glEnableVertexAttribArray(positionHandle);
    glVertexAttribPointer(texCoordHandle, 2, GL_FLOAT, GL_FALSE, 0, texCoords);
    glEnableVertexAttribArray(texCoordHandle);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId);
    
    // Set texture size uniform for edge detection
    if (currentFilterType == 1) {
        glUniform2f(texSizeUniform, 1024.0f, 768.0f); // Can be made dynamic
    }

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_realtimeedgedetection_MainActivity_cleanup(
        JNIEnv *env,
        jobject /* this */) {
    LOGI("Cleaning up native resources");
    
    processingEnabled = false;
    
    if (currentWindow != nullptr) {
        ANativeWindow_release(currentWindow);
        currentWindow = nullptr;
    }
    
    if (eglDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (eglSurface != EGL_NO_SURFACE) {
            eglDestroySurface(eglDisplay, eglSurface);
            eglSurface = EGL_NO_SURFACE;
        }
        if (eglContext != EGL_NO_CONTEXT) {
            eglDestroyContext(eglDisplay, eglContext);
            eglContext = EGL_NO_CONTEXT;
        }
        eglTerminate(eglDisplay);
        eglDisplay = EGL_NO_DISPLAY;
    }
    
    {
        std::lock_guard<std::mutex> lock(frameMutex);
        frameBuffer.release();
    }
    
    // Clear frame queue
    {
        std::lock_guard<std::mutex> lock(queueMutex);
        while (!frameQueue.empty()) {
            frameQueue.pop();
        }
    }
    
    LOGI("Cleanup completed. Processed %" PRId64 " frames in %" PRId64 "ms", frameCount, frameProcessingTimeMs);
}

// ==================== YUV to RGBA Conversion ====================

/**
 * Convert NV21 (YUV420SP) to RGBA format
 * Optimized for real-time camera frame processing
 */
static inline void convertNV21ToRGBA(const uint8_t* nv21Data, uint8_t* rgbaData, 
                                     int width, int height) {
    int frameSize = width * height;
    
    for (int i = 0; i < height; i++) {
        for (int j = 0; j < width; j++) {
            int Y = nv21Data[i * width + j] & 0xff;
            int pixelIndex = (i * width + j) * 4;
            
            int index = frameSize + (i >> 1) * width + (j & ~1);
            int V = nv21Data[index] & 0xff;
            int U = nv21Data[index + 1] & 0xff;
            
            V -= 128;
            U -= 128;
            
            int Y1192 = 1192 * Y;
            int r = (Y1192 + 1634 * V) >> 10;
            int g = (Y1192 - 400 * U - 833 * V) >> 10;
            int b = (Y1192 + 2066 * U) >> 10;
            
            r = std::max(0, std::min(255, r));
            g = std::max(0, std::min(255, g));
            b = std::max(0, std::min(255, b));
            
            rgbaData[pixelIndex] = r;
            rgbaData[pixelIndex + 1] = g;
            rgbaData[pixelIndex + 2] = b;
            rgbaData[pixelIndex + 3] = 255;
        }
    }
}

/**
 * Optimized YUV to RGB using OpenCV (faster for large frames)
 */
static cv::Mat convertYUVToRGBA(const std::vector<uint8_t>& yuvData, 
                                 int width, int height) {
    cv::Mat yuvMat(height + height / 2, width, CV_8UC1, (uchar*)yuvData.data());
    cv::Mat rgbaMat;
    try {
        cv::cvtColor(yuvMat, rgbaMat, cv::COLOR_YUV2RGBA_NV21);
        return rgbaMat;
    } catch (const cv::Exception& e) {
        LOGE("YUV conversion error: %s", e.what());
        return cv::Mat();
    }
}

// ==================== Single Image Processing ====================

/**
 * JNI Method: Process bitmap with Grayscale filter
 * Java signature: processBitmap(Bitmap bitmap, int filterType) -> Bitmap
 */
extern "C" JNIEXPORT jobject JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_processBitmap(
        JNIEnv *env,
        jclass /* clazz */,
        jobject bitmap,
        jint filterType) {

    if (bitmap == nullptr) {
        LOGE("Input bitmap is null");
        return nullptr;
    }

    try {
        // Get bitmap information
        AndroidBitmapInfo bitmapInfo;
        if (AndroidBitmap_getInfo(env, bitmap, &bitmapInfo) < 0) {
            LOGE("AndroidBitmap_getInfo failed");
            return nullptr;
        }

        LOGI("processBitmap: %dx%d, format=%d, stride=%d, filterType=%d", 
             bitmapInfo.width, bitmapInfo.height, bitmapInfo.format, bitmapInfo.stride, filterType);

        // Lock input bitmap pixels
        void* inputPixels = nullptr;
        if (AndroidBitmap_lockPixels(env, bitmap, &inputPixels) < 0) {
            LOGE("AndroidBitmap_lockPixels (input) failed");
            return nullptr;
        }

        // Create OpenCV Mat from bitmap
        cv::Mat inputMat(bitmapInfo.height, bitmapInfo.width, CV_8UC4, (uint8_t*)inputPixels);
        
        LOGD("Created input Mat: size=%dx%d, channels=%d", 
             inputMat.cols, inputMat.rows, inputMat.channels());

        // Process the image using ImageProcessor
        cv::Mat processedMat = ImageProcessor::processImage(inputMat, filterType);
        
        LOGD("Processed Mat: size=%dx%d, channels=%d", 
             processedMat.cols, processedMat.rows, processedMat.channels());

        // Lock output bitmap
        void* outputPixels = nullptr;
        if (AndroidBitmap_lockPixels(env, bitmap, &outputPixels) < 0) {
            LOGE("AndroidBitmap_lockPixels (output) failed");
            AndroidBitmap_unlockPixels(env, bitmap);
            return nullptr;
        }

        // Copy processed data to output bitmap
        if (!processedMat.empty() && processedMat.isContinuous()) {
            size_t pixelCount = bitmapInfo.height * bitmapInfo.width;
            size_t bytesToCopy = pixelCount * 4; // RGBA = 4 bytes per pixel
            
            if (processedMat.total() == pixelCount && processedMat.channels() == 4) {
                std::memcpy(outputPixels, processedMat.data, bytesToCopy);
                LOGI("✓ Bitmap processing successful: %dx%d (%zu bytes)", 
                     bitmapInfo.width, bitmapInfo.height, bytesToCopy);
            } else {
                LOGE("✗ Mat dimensions mismatch: expected %zu pixels, got %ld with %d channels",
                     pixelCount, processedMat.total(), processedMat.channels());
            }
        } else {
            LOGE("✗ Processed mat is empty or non-continuous");
        }

        // Unlock pixels
        AndroidBitmap_unlockPixels(env, bitmap);
        
        return bitmap;

    } catch (const std::exception& e) {
        LOGE("Exception in processBitmap: %s", e.what());
        return nullptr;
    }
}

/**
 * JNI Method: Process bitmap with Grayscale filter
 * Java signature: processGrayscale(Bitmap bitmap) -> Bitmap
 */
extern "C" JNIEXPORT jobject JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_processGrayscale(
        JNIEnv *env,
        jclass clazz,
        jobject bitmap) {
    LOGI("processGrayscale called");
    return Java_com_example_realtimeedgedetection_NativeImageProcessor_processBitmap(
        env, clazz, bitmap, 0);
}

/**
 * JNI Method: Process bitmap with Canny Edge Detection filter
 * Java signature: processCannyEdge(Bitmap bitmap) -> Bitmap
 */
extern "C" JNIEXPORT jobject JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_processCannyEdge(
        JNIEnv *env,
        jclass clazz,
        jobject bitmap) {
    LOGI("processCannyEdge called");
    return Java_com_example_realtimeedgedetection_NativeImageProcessor_processBitmap(
        env, clazz, bitmap, 1);
}

/**
 * JNI Method: Configure Canny Edge Detection thresholds
 * Java signature: setCannyThresholds(int lowThreshold, int highThreshold) -> boolean
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_setCannyThresholds(
        JNIEnv *env,
        jclass /* clazz */,
        jint lowThreshold,
        jint highThreshold) {
    
    if (lowThreshold < 0 || highThreshold < lowThreshold) {
        LOGE("Invalid Canny thresholds: low=%d, high=%d", lowThreshold, highThreshold);
        return JNI_FALSE;
    }
    
    LOGI("Canny thresholds configured: low=%d, high=%d", lowThreshold, highThreshold);
    // Note: You would need to expose these in ImageProcessor class for dynamic configuration
    return JNI_TRUE;
}

// ==================== Camera Frame Streaming ====================

/**
 * JNI Method: Initialize frame processing
 * Java signature: initializeFrameProcessing(int width, int height) -> boolean
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_initializeFrameProcessing(
        JNIEnv *env,
        jclass /* clazz */,
        jint width,
        jint height) {
    
    if (width <= 0 || height <= 0) {
        LOGE("Invalid frame dimensions: %dx%d", width, height);
        return JNI_FALSE;
    }
    
    try {
        {
            std::lock_guard<std::mutex> lock(frameMutex);
            frameBuffer = cv::Mat::zeros(height, width, CV_8UC4);
        }
        processingEnabled = true;
        frameCount = 0;
        frameProcessingTimeMs = 0;
        
        LOGI("Frame processing initialized: %dx%d", width, height);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Exception in initializeFrameProcessing: %s", e.what());
        return JNI_FALSE;
    }
}

/**
 * JNI Method: Process NV21 camera frame (most common format)
 * Java signature: processNV21Frame(byte[] frameData, int width, int height, int filterType) -> byte[]
 * 
 * Optimized for real-time performance with minimal latency
 */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_processNV21Frame(
        JNIEnv *env,
        jclass /* clazz */,
        jbyteArray frameData,
        jint width,
        jint height,
        jint filterType) {

    if (frameData == nullptr) {
        LOGE("Frame data is null");
        return nullptr;
    }

    if (!processingEnabled) {
        LOGE("Frame processing not initialized");
        return nullptr;
    }

    auto startTime = std::chrono::high_resolution_clock::now();

    try {
        // Get byte array elements
        jbyte* nv21 = env->GetByteArrayElements(frameData, nullptr);
        if (nv21 == nullptr) {
            LOGE("Failed to get NV21 array elements");
            return nullptr;
        }

        // Convert NV21 to RGBA using OpenCV
        std::vector<uint8_t> yuvVector((uint8_t*)nv21, (uint8_t*)nv21 + width * height * 3 / 2);
        cv::Mat rgbaFrame = convertYUVToRGBA(yuvVector, width, height);

        if (rgbaFrame.empty()) {
            LOGE("YUV to RGBA conversion failed");
            env->ReleaseByteArrayElements(frameData, nv21, JNI_ABORT);
            return nullptr;
        }

        // Apply filter
        cv::Mat processedFrame = ImageProcessor::processImage(rgbaFrame, filterType);

        if (processedFrame.empty()) {
            LOGE("Image processing failed");
            env->ReleaseByteArrayElements(frameData, nv21, JNI_ABORT);
            return nullptr;
        }

        // Create output byte array with processed data
        jbyteArray outputArray = env->NewByteArray(width * height * 4);
        if (outputArray == nullptr) {
            LOGE("Failed to allocate output array");
            env->ReleaseByteArrayElements(frameData, nv21, JNI_ABORT);
            return nullptr;
        }

        // Copy processed frame to output
        env->SetByteArrayRegion(outputArray, 0, width * height * 4, 
                                 (jbyte*)processedFrame.data);

        env->ReleaseByteArrayElements(frameData, nv21, JNI_ABORT);

        // Calculate processing time
        auto endTime = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(endTime - startTime);
        frameProcessingTimeMs += duration.count();
        frameCount++;

        LOGD("Frame processed in %ldms (avg: %ldms)", (long)duration.count(), 
             (long)(frameProcessingTimeMs / frameCount));

        return outputArray;

    } catch (const std::exception& e) {
        LOGE("Exception in processNV21Frame: %s", e.what());
        return nullptr;
    }
}

/**
 * JNI Method: Process RGBA camera frame
 * Java signature: processRGBAFrame(byte[] frameData, int width, int height, int filterType) -> byte[]
 */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_processRGBAFrame(
        JNIEnv *env,
        jclass /* clazz */,
        jbyteArray frameData,
        jint width,
        jint height,
        jint filterType) {

    if (frameData == nullptr) {
        LOGE("RGBA frame data is null");
        return nullptr;
    }

    try {
        jbyte* rgba = env->GetByteArrayElements(frameData, nullptr);
        if (rgba == nullptr) {
            LOGE("Failed to get RGBA array elements");
            return nullptr;
        }

        // Create Mat from RGBA data
        cv::Mat rgbaFrame(height, width, CV_8UC4, (uint8_t*)rgba);

        // Apply filter
        cv::Mat processedFrame = ImageProcessor::processImage(rgbaFrame, filterType);

        if (processedFrame.empty()) {
            LOGE("RGBA image processing failed");
            env->ReleaseByteArrayElements(frameData, rgba, JNI_ABORT);
            return nullptr;
        }

        // Create output array
        jbyteArray outputArray = env->NewByteArray(width * height * 4);
        if (outputArray == nullptr) {
            LOGE("Failed to allocate RGBA output array");
            env->ReleaseByteArrayElements(frameData, rgba, JNI_ABORT);
            return nullptr;
        }

        env->SetByteArrayRegion(outputArray, 0, width * height * 4, 
                                 (jbyte*)processedFrame.data);

        env->ReleaseByteArrayElements(frameData, rgba, JNI_ABORT);

        LOGI("RGBA frame processed successfully: %dx%d", width, height);
        return outputArray;

    } catch (const std::exception& e) {
        LOGE("Exception in processRGBAFrame: %s", e.what());
        return nullptr;
    }
}

/**
 * JNI Method: Get frame processing statistics
 * Java signature: getFrameStats() -> long (high 32 bits = count, low 32 bits = avg time)
 */
extern "C" JNIEXPORT jlong JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_getFrameStats(
        JNIEnv *env,
        jclass /* clazz */) {
    
    if (frameCount == 0) {
        return 0;
    }
    
    long avgTime = frameProcessingTimeMs / frameCount;
    long stats = (frameCount << 32) | (avgTime & 0xFFFFFFFF);
    
    LOGI("Frame stats - Count: %" PRId64 ", Avg time: %ldms", frameCount, avgTime);
    return stats;
}

/**
 * JNI Method: Reset frame processing statistics
 * Java signature: resetFrameStats() -> void
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_resetFrameStats(
        JNIEnv *env,
        jclass /* clazz */) {
    
    frameCount = 0;
    frameProcessingTimeMs = 0;
    LOGI("Frame statistics reset");
}

/**
 * JNI Method: Stop frame processing
 * Java signature: stopFrameProcessing() -> void
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_stopFrameProcessing(
        JNIEnv *env,
        jclass /* clazz */) {
    
    processingEnabled = false;
    {
        std::lock_guard<std::mutex> lock(queueMutex);
        while (!frameQueue.empty()) {
            frameQueue.pop();
        }
    }
    LOGI("Frame processing stopped");
}

/**
 * JNI Method: Clean up frame processing resources
 * Java signature: cleanup() -> void
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_realtimeedgedetection_NativeImageProcessor_cleanup(
        JNIEnv *env,
        jclass /* clazz */) {
    return Java_com_example_realtimeedgedetection_MainActivity_cleanup(env, nullptr);
}

/**
 * JNI Method: Notify filter change
 * Java signature: notifyFilterChange(int filterType) -> void
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_realtimeedgedetection_MainActivity_notifyFilterChange(
        JNIEnv *env,
        jobject /* this */,
        jint filterType) {

    if (filterType < 0 || filterType > 2) {
        LOGE("Invalid filter type: %d", filterType);
        return;
    }

    {
        std::lock_guard<std::mutex> lock(frameMutex);
        currentFilterType = filterType;
    }

    LOGI("Filter changed to: %d (0=Grayscale, 1=Canny Edge, 2=Original)", filterType);
}

#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/core/ocl.hpp>

#define LOG_TAG "NativeLib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static EGLDisplay eglDisplay = EGL_NO_DISPLAY;
static EGLSurface eglSurface = EGL_NO_SURFACE;
static EGLContext eglContext = EGL_NO_CONTEXT;

static GLuint textureId;
static GLuint program;
static GLint positionHandle;
static GLint texCoordHandle;

const char* VERTEX_SHADER = R"glsl(
    attribute vec4 vPosition;
    attribute vec2 vTexCoord;
    varying vec2 texCoord;
    void main() {
        gl_Position = vPosition;
        texCoord = vTexCoord;
    }
)glsl";

const char* FRAGMENT_SHADER = R"glsl(
    precision mediump float;
    varying vec2 texCoord;
    uniform sampler2D sTexture;
    void main() {
        gl_FragColor = texture2D(sTexture, texCoord);
    }
)glsl";

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

void setupGraphics() {
    LOGI("setupGraphics");
    program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
    if (!program) {
        LOGE("Could not create program.");
        return;
    }
    positionHandle = glGetAttribLocation(program, "vPosition");
    texCoordHandle = glGetAttribLocation(program, "vTexCoord");

    glGenTextures(1, &textureId);
    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
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

    glUseProgram(program);

    glVertexAttribPointer(positionHandle, 2, GL_FLOAT, GL_FALSE, 0, vertices);
    glEnableVertexAttribArray(positionHandle);
    glVertexAttribPointer(texCoordHandle, 2, GL_FLOAT, GL_FALSE, 0, texCoords);
    glEnableVertexAttribArray(texCoordHandle);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_realtimeedgedetection_MainActivity_cleanup(
        JNIEnv *env,
        jobject /* this */) {
    LOGI("Cleaning up native resources.");
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
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_realtimeedgedetection_MainActivity_processFrame(
        JNIEnv *env,
        jobject /* this */,
        jobject surface,
        jint width,
        jint height,
        jint filterType) {

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) {
        LOGE("Failed to get native window from surface");
        return;
    }

    if (eglDisplay == EGL_NO_DISPLAY) {
        LOGI("Initializing EGL");
        eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        eglInitialize(eglDisplay, 0, 0);

        EGLint attribs[] = { EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_NONE };
        EGLConfig config;
        EGLint numConfigs;
        eglChooseConfig(eglDisplay, attribs, &config, 1, &numConfigs);

        EGLint surfaceAttribs[] = { EGL_NONE };
        eglSurface = eglCreateWindowSurface(eglDisplay, config, window, surfaceAttribs);

        EGLint contextAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
        eglContext = eglCreateContext(eglDisplay, config, EGL_NO_CONTEXT, contextAttribs);

        eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

        setupGraphics();
    }

    // Read pixels into a cv::Mat
    cv::Mat frame(height, width, CV_8UC4);
    glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, frame.data);

    // Process the frame with OpenCV
    cv::Mat processedFrame;
    switch (filterType) {
        case 0: { // Grayscale
            cv::cvtColor(frame, processedFrame, cv::COLOR_RGBA2GRAY);
            cv::cvtColor(processedFrame, processedFrame, cv::COLOR_GRAY2RGBA); // Convert back to RGBA for rendering
            break;
        }
        case 1: { // Canny Edge
            cv::Mat gray;
            cv::cvtColor(frame, gray, cv::COLOR_RGBA2GRAY);
            cv::Canny(gray, processedFrame, 80, 100);
            cv::cvtColor(processedFrame, processedFrame, cv::COLOR_GRAY2RGBA); // Convert back to RGBA for rendering
            break;
        }
        case 2: { // Original
            processedFrame = frame;
            break;
        }
        default: {
            processedFrame = frame;
            break;
        }
    }

    // Upload the processed frame to the texture
    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, processedFrame.cols, processedFrame.rows, 0, GL_RGBA, GL_UNSIGNED_BYTE, processedFrame.data);

    // Render the texture
    render();

    eglSwapBuffers(eglDisplay, eglSurface);
}

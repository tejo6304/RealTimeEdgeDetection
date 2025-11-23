package com.example.realtimeedgedetection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class WebServerManager(private val context: Context) {
    private var serverThread: Thread? = null
    private val PORT = 8080
    private val TAG = "WebServerManager"
    private val isRunning = AtomicBoolean(false)
    
    @Volatile
    private var currentFrameBitmap: Bitmap? = null
    @Volatile
    private var currentFrameBytes: ByteArray? = null
    
    fun startServer() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Server already running")
            return
        }
        
        serverThread = Thread {
            runServer()
        }.apply {
            name = "WebServer"
            isDaemon = true
            start()
        }
        Log.d(TAG, "Web server started on port $PORT")
    }
    
    fun stopServer() {
        isRunning.set(false)
        serverThread?.join(2000)
        Log.d(TAG, "Web server stopped")
    }
    
    fun updateFrame(bitmap: Bitmap) {
        currentFrameBitmap = bitmap
        currentFrameBytes = bitmapToJpegBytes(bitmap)
    }
    
    private fun runServer() {
        try {
            val serverSocket = ServerSocket(PORT)
            Log.d(TAG, "Server listening on port $PORT")
            
            while (isRunning.get()) {
                try {
                    val clientSocket = serverSocket.acceptWithTimeout(1000)
                    if (clientSocket != null) {
                        Thread {
                            handleClient(clientSocket)
                        }.start()
                    }
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Log.e(TAG, "Error accepting connection", e)
                    }
                }
            }
            
            serverSocket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Server error", e)
        }
    }
    
    private fun ServerSocket.acceptWithTimeout(timeout: Int): Socket? {
        return try {
            soTimeout = timeout
            accept()
        } catch (e: Exception) {
            null
        }
    }
    
    private fun handleClient(clientSocket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
            val writer = OutputStreamWriter(clientSocket.outputStream)
            
            // Read request line
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            
            val method = parts[0]
            val path = parts[1]
            
            // Skip headers
            var line: String? = reader.readLine()
            while (!line.isNullOrEmpty()) {
                line = reader.readLine()
            }
            
            when {
                path == "/" -> {
                    sendHtmlResponse(writer)
                }
                path == "/api/frame" -> {
                    sendFrameResponse(clientSocket)
                }
                path.startsWith("/api/control") -> {
                    sendJsonResponse(writer, """{"status": "ok"}""")
                }
                else -> {
                    send404Response(writer)
                }
            }
            
            writer.close()
            clientSocket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
            try {
                clientSocket.close()
            } catch (ignored: Exception) {}
        }
    }
    
    private fun sendHtmlResponse(writer: OutputStreamWriter) {
        val html = getHtmlContent()
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${html.length}\r\n" +
                "Connection: close\r\n" +
                "\r\n"
        
        writer.write(response)
        writer.write(html)
        writer.flush()
    }
    
    private fun sendFrameResponse(clientSocket: Socket) {
        try {
            val frameData = currentFrameBytes
            if (frameData != null) {
                val response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: image/jpeg\r\n" +
                        "Content-Length: ${frameData.size}\r\n" +
                        "Cache-Control: no-cache\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
                
                val out = clientSocket.outputStream
                out.write(response.toByteArray())
                out.write(frameData)
                out.flush()
            } else {
                val response = "HTTP/1.1 204 No Content\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
                clientSocket.outputStream.write(response.toByteArray())
                clientSocket.outputStream.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending frame", e)
        }
    }
    
    private fun sendJsonResponse(writer: OutputStreamWriter, json: String) {
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${json.length}\r\n" +
                "Connection: close\r\n" +
                "\r\n"
        
        writer.write(response)
        writer.write(json)
        writer.flush()
    }
    
    private fun send404Response(writer: OutputStreamWriter) {
        val message = "Not Found"
        val response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${message.length}\r\n" +
                "Connection: close\r\n" +
                "\r\n" +
                message
        
        writer.write(response)
        writer.flush()
    }
    
    private fun getHtmlContent(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Real-Time Edge Detection</title>
    <style>
        body { font-family: Arial, sans-serif; background: #1a1a1a; color: white; margin: 0; padding: 20px; }
        .container { max-width: 900px; margin: 0 auto; background: #2a2a2a; padding: 20px; border-radius: 10px; }
        h1 { text-align: center; color: #4CAF50; }
        .video-container { background: black; border-radius: 8px; overflow: hidden; margin: 20px 0; }
        canvas { width: 100%; height: auto; display: block; }
        .controls { display: flex; gap: 10px; margin: 20px 0; justify-content: center; flex-wrap: wrap; }
        button { padding: 10px 20px; background: #4CAF50; color: white; border: none; border-radius: 5px; cursor: pointer; }
        button:hover { background: #45a049; }
        button:disabled { background: #cccccc; cursor: not-allowed; }
        .info { background: #333; padding: 15px; border-radius: 5px; margin: 10px 0; }
        .info-row { display: flex; justify-content: space-between; padding: 5px 0; }
    </style>
</head>
<body>
    <div class="container">
        <h1>📷 Real-Time Edge Detection Viewer</h1>
        
        <div class="video-container">
            <canvas id="videoCanvas"></canvas>
        </div>
        
        <div class="info">
            <div class="info-row">
                <span>FPS:</span>
                <span id="fps">0</span>
            </div>
            <div class="info-row">
                <span>Status:</span>
                <span id="status">Connecting...</span>
            </div>
        </div>
        
        <div class="controls">
            <button id="startBtn" onclick="viewer?.startStream()">▶ Start</button>
            <button id="stopBtn" onclick="viewer?.stopStream()" disabled>⏹ Stop</button>
            <button id="screenshotBtn" onclick="viewer?.takeScreenshot()">📸 Screenshot</button>
        </div>
    </div>
    
    <script>
        const canvas = document.getElementById('videoCanvas');
        const ctx = canvas.getContext('2d');
        
        class SimpleViewer {
            constructor(canvasEl) {
                this.canvas = canvasEl;
                this.ctx = canvasEl.getContext('2d');
                this.isStreaming = false;
                this.frameCount = 0;
                this.lastFpsTime = Date.now();
                this.resizeCanvas();
                window.addEventListener('resize', () => this.resizeCanvas());
            }
            
            resizeCanvas() {
                const parent = this.canvas.parentElement;
                this.canvas.width = parent.clientWidth;
                this.canvas.height = parent.clientHeight * 0.75;
            }
            
            async startStream() {
                if (this.isStreaming) return;
                this.isStreaming = true;
                document.getElementById('startBtn').disabled = true;
                document.getElementById('stopBtn').disabled = false;
                document.getElementById('status').textContent = 'Streaming...';
                this.streamLoop();
            }
            
            stopStream() {
                this.isStreaming = false;
                document.getElementById('startBtn').disabled = false;
                document.getElementById('stopBtn').disabled = true;
                document.getElementById('status').textContent = 'Stopped';
                this.ctx.fillStyle = '#000';
                this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
            }
            
            async streamLoop() {
                while (this.isStreaming) {
                    try {
                        const response = await fetch('/api/frame', { cache: 'no-store' });
                        if (response.ok && response.status !== 204) {
                            const blob = await response.blob();
                            const url = URL.createObjectURL(blob);
                            const img = new Image();
                            img.onload = () => {
                                this.ctx.drawImage(img, 0, 0, this.canvas.width, this.canvas.height);
                                this.frameCount++;
                                URL.revokeObjectURL(url);
                                this.updateFps();
                            };
                            img.src = url;
                        }
                    } catch(e) {
                        console.error('Fetch error:', e);
                    }
                    await new Promise(r => setTimeout(r, 33));
                }
            }
            
            updateFps() {
                const now = Date.now();
                if (now - this.lastFpsTime >= 1000) {
                    document.getElementById('fps').textContent = this.frameCount;
                    this.frameCount = 0;
                    this.lastFpsTime = now;
                }
            }
            
            async takeScreenshot() {
                this.canvas.toBlob(blob => {
                    const url = URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = 'screenshot-' + Date.now() + '.png';
                    a.click();
                    URL.revokeObjectURL(url);
                });
            }
        }
        
        let viewer;
        window.addEventListener('load', () => {
            viewer = new SimpleViewer(canvas);
            viewer.startStream();
        });
    </script>
</body>
</html>
        """.trimIndent()
    }
    
    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return stream.toByteArray()
    }
}


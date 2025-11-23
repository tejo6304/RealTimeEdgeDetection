/**
 * Real-Time Edge Detection Web Viewer
 * TypeScript client for remote frame viewing and device control
 */

interface FrameMetadata {
    width: number;
    height: number;
    fps: number;
    format: string;
    timestamp: number;
}

interface DeviceStats {
    fps: number;
    resolution: string;
    connected: boolean;
    uptime: number;
}

class EdgeDetectionViewer {
    private canvas: HTMLCanvasElement;
    private ctx: CanvasRenderingContext2D;
    private serverUrl: string;
    private isStreaming: boolean = false;
    private frameCount: number = 0;
    private lastFpsTime: number = Date.now();
    private currentFps: number = 0;
    private frameBuffer: Uint8Array[] = [];
    private maxBufferSize: number = 5;
    
    // Performance monitoring
    private metrics = {
        framesFetched: 0,
        frameProcessed: 0,
        averageLatency: 0,
        peakFps: 0,
        totalFrames: 0
    };

    constructor(canvasId: string, serverUrl: string = 'http://localhost:8080') {
        const canvasEl = document.getElementById(canvasId) as HTMLCanvasElement;
        if (!canvasEl) {
            throw new Error(`Canvas element with id "${canvasId}" not found`);
        }

        this.canvas = canvasEl;
        this.ctx = this.canvas.getContext('2d')!;
        this.serverUrl = serverUrl;

        this.setupEventListeners();
        this.resizeCanvas();
        window.addEventListener('resize', () => this.resizeCanvas());
    }

    private setupEventListeners(): void {
        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            switch (e.key) {
                case ' ':
                    this.toggleStream();
                    break;
                case 's':
                    this.takeScreenshot();
                    break;
                case 'c':
                    this.clearCanvas();
                    break;
                case 'r':
                    this.reset();
                    break;
            }
        });
    }

    private resizeCanvas(): void {
        const container = this.canvas.parentElement;
        if (container) {
            this.canvas.width = container.clientWidth;
            this.canvas.height = container.clientHeight;
        }
    }

    public async startStream(): Promise<void> {
        if (this.isStreaming) {
            console.warn('Stream already running');
            return;
        }

        this.isStreaming = true;
        this.metrics.framesFetched = 0;
        this.metrics.frameProcessed = 0;

        try {
            await this.streamLoop();
        } catch (error) {
            console.error('Stream error:', error);
            this.isStreaming = false;
        }
    }

    public stopStream(): void {
        this.isStreaming = false;
    }

    public toggleStream(): void {
        if (this.isStreaming) {
            this.stopStream();
        } else {
            this.startStream();
        }
    }

    private async streamLoop(): Promise<void> {
        while (this.isStreaming) {
            try {
                const startTime = performance.now();
                await this.fetchAndRenderFrame();
                const latency = performance.now() - startTime;

                // Update metrics
                this.updateMetrics(latency);

                // Request next frame
                await new Promise(resolve => requestAnimationFrame(resolve));
            } catch (error) {
                console.error('Frame fetch error:', error);
                
                // Retry after delay
                await this.sleep(500);
            }
        }
    }

    private async fetchAndRenderFrame(): Promise<void> {
        const frameBlob = await this.fetchFrame();
        if (!frameBlob) {
            return;
        }

        const bitmap = await createImageBitmap(frameBlob);
        this.renderFrame(bitmap);
        this.metrics.frameProcessed++;
    }

    private async fetchFrame(): Promise<Blob | null> {
        try {
            const response = await fetch(`${this.serverUrl}/api/frame`, {
                cache: 'no-store',
                headers: {
                    'Pragma': 'no-cache',
                    'Cache-Control': 'no-cache'
                }
            });

            if (response.ok && response.status !== 204) {
                this.metrics.framesFetched++;
                return await response.blob();
            } else if (response.status === 204) {
                // No frame available yet
                return null;
            } else {
                throw new Error(`HTTP ${response.status}`);
            }
        } catch (error) {
            console.error('Fetch frame error:', error);
            return null;
        }
    }

    private renderFrame(bitmap: ImageBitmap): void {
        try {
            this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
            this.ctx.drawImage(bitmap, 0, 0, this.canvas.width, this.canvas.height);
            this.frameCount++;
        } catch (error) {
            console.error('Render error:', error);
        } finally {
            bitmap.close();
        }
    }

    private updateMetrics(latency: number): void {
        this.metrics.totalFrames++;
        this.metrics.averageLatency = 
            (this.metrics.averageLatency * (this.metrics.totalFrames - 1) + latency) / 
            this.metrics.totalFrames;

        const now = Date.now();
        const elapsed = now - this.lastFpsTime;

        if (elapsed >= 1000) {
            this.currentFps = (this.frameCount * 1000) / elapsed;
            if (this.currentFps > this.metrics.peakFps) {
                this.metrics.peakFps = this.currentFps;
            }
            this.frameCount = 0;
            this.lastFpsTime = now;
        }
    }

    public async takeScreenshot(): Promise<void> {
        return new Promise((resolve) => {
            this.canvas.toBlob((blob) => {
                if (!blob) return;

                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `screenshot-${Date.now()}.png`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);
                resolve();
            });
        });
    }

    public clearCanvas(): void {
        this.ctx.fillStyle = '#000';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
    }

    public reset(): void {
        this.stopStream();
        this.clearCanvas();
        this.metrics = {
            framesFetched: 0,
            frameProcessed: 0,
            averageLatency: 0,
            peakFps: 0,
            totalFrames: 0
        };
        this.frameCount = 0;
        this.lastFpsTime = Date.now();
    }

    public getMetrics() {
        return {
            ...this.metrics,
            currentFps: this.currentFps,
            isStreaming: this.isStreaming
        };
    }

    public getStats(): DeviceStats {
        return {
            fps: this.currentFps,
            resolution: `${this.canvas.width}x${this.canvas.height}`,
            connected: this.isStreaming,
            uptime: this.metrics.totalFrames
        };
    }

    private sleep(ms: number): Promise<void> {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}

// Export for use in browser
if (typeof window !== 'undefined') {
    (window as any).EdgeDetectionViewer = EdgeDetectionViewer;
}

export { EdgeDetectionViewer, FrameMetadata, DeviceStats };

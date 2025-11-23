/**
 * Edge Detection Frame Viewer with Live Camera Support
 * Displays processed camera frames with real-time statistics overlay
 * Uses OpenCV.js for Canny edge detection and grayscale conversion
 */

// Global variable for OpenCV readiness
declare var cv: any;

interface FrameStats {
  fps: number;
  resolution: string;
  filter: string;
  timestamp: string;
  processingTime: number;
}

class FrameViewer {
  private canvasElement: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private statsElement: HTMLDivElement;
  private overlayCanvas: HTMLCanvasElement;
  private overlayCtx: CanvasRenderingContext2D;
  private currentFilter: string = 'None';
  private originalImageData: ImageData | null = null;
  
  // Camera-related properties
  private videoStream: HTMLVideoElement;
  private cameraActive: boolean = false;
  private animationId: number | null = null;
  private frameCount: number = 0;
  private lastFrameTime: number = Date.now();
  private fps: number = 0;
  private outputCanvas: HTMLCanvasElement;
  private outputCtx: CanvasRenderingContext2D;

  constructor() {
    this.canvasElement = this.getElement<HTMLCanvasElement>('frame-canvas');
    this.ctx = this.getContext(this.canvasElement);
    this.statsElement = this.getElement<HTMLDivElement>('stats-overlay');
    this.overlayCanvas = this.getElement<HTMLCanvasElement>('overlay-canvas');
    this.overlayCtx = this.getContext(this.overlayCanvas);
    this.videoStream = this.getElement<HTMLVideoElement>('video-stream');
    this.outputCanvas = this.getElement<HTMLCanvasElement>('output-canvas');
    this.outputCtx = this.getContext(this.outputCanvas);
    
    this.initializeCanvas();
  }

  /**
   * Get DOM element with type checking
   */
  private getElement<T extends HTMLElement>(id: string): T {
    const element = document.getElementById(id) as T;
    if (!element) {
      throw new Error(`Element with id '${id}' not found`);
    }
    return element;
  }

  /**
   * Get 2D canvas context
   */
  private getContext(canvas: HTMLCanvasElement): CanvasRenderingContext2D {
    const context = canvas.getContext('2d');
    if (!context) {
      throw new Error('Failed to get canvas 2D context');
    }
    return context;
  }

  /**
   * Initialize canvas dimensions
   */
  private initializeCanvas(): void {
    const width = 1920;
    const height = 1080;

    this.canvasElement.width = width;
    this.canvasElement.height = height;

    this.overlayCanvas.width = width;
    this.overlayCanvas.height = height;

    this.outputCanvas.width = width;
    this.outputCanvas.height = height;

    console.log('Canvas initialized:', { width, height });
  }

  /**
   * Check if OpenCV.js is loaded
   */
  private isOpenCvReady(): boolean {
    return typeof cv !== 'undefined' && cv.Mat !== undefined;
  }

  /**
   * Start camera stream
   */
  public async startCamera(): Promise<void> {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment', width: { ideal: 1920 }, height: { ideal: 1080 } }
      });

      this.videoStream.srcObject = stream;
      
      this.videoStream.onloadedmetadata = () => {
        this.videoStream.play();
        this.cameraActive = true;
        console.log('Camera started successfully');
        this.processFramesContinuous();
      };

      this.videoStream.onerror = (error) => {
        console.error('Video stream error:', error);
        this.showError('Failed to access camera stream');
      };
    } catch (error) {
      console.error('Camera access error:', error);
      this.showError('Failed to access camera. Please check permissions.');
    }
  }

  /**
   * Stop camera stream
   */
  public stopCamera(): void {
    if (this.videoStream.srcObject instanceof MediaStream) {
      this.videoStream.srcObject.getTracks().forEach(track => track.stop());
    }
    this.cameraActive = false;
    if (this.animationId !== null) {
      cancelAnimationFrame(this.animationId);
    }
    console.log('Camera stopped');
  }

  /**
   * Process frames continuously from camera
   */
  private processFramesContinuous(): void {
    if (!this.cameraActive || this.videoStream.paused) {
      return;
    }

    this.frameCount++;
    const now = Date.now();
    const elapsed = now - this.lastFrameTime;

    if (elapsed >= 1000) {
      this.fps = (this.frameCount * 1000) / elapsed;
      this.frameCount = 0;
      this.lastFrameTime = now;
    }

    try {
      // Draw video frame to canvas
      this.ctx.drawImage(
        this.videoStream,
        0,
        0,
        this.canvasElement.width,
        this.canvasElement.height
      );

      // Store image data
      this.originalImageData = this.ctx.getImageData(
        0,
        0,
        this.canvasElement.width,
        this.canvasElement.height
      );

      // Create stats
      const stats: FrameStats = {
        fps: this.fps,
        resolution: `${this.canvasElement.width}x${this.canvasElement.height}`,
        filter: this.currentFilter,
        timestamp: new Date().toLocaleTimeString(),
        processingTime: 16.7
      };

      // Draw stats overlay
      this.drawStatsOverlay(stats);
      this.updateStatsPanel(stats);
    } catch (error) {
      console.error('Frame processing error:', error);
    }

    this.animationId = requestAnimationFrame(() => this.processFramesContinuous());
  }

  /**
   * Capture frame from camera or canvas
   */
  public captureFrame(): void {
    if (!this.originalImageData) {
      this.showError('No frame to capture');
      return;
    }

    const link = document.createElement('a');
    link.href = this.canvasElement.toDataURL('image/png');
    link.download = `captured-${Date.now()}.png`;
    link.click();
    console.log('Frame captured');
  }

  /**
   * Load image from file input or URL
   */
  public loadImage(imageSource: string | Blob): void {
    const reader = new FileReader();

    reader.onload = (event: ProgressEvent<FileReader>) => {
      const img = new Image();
      
      img.onload = () => {
        this.drawImage(img);
      };

      img.onerror = () => {
        console.error('Failed to load image');
        this.showError('Failed to load image. Please check the file.');
      };

      if (typeof imageSource === 'string') {
        img.src = imageSource;
      } else if (event.target?.result) {
        img.src = event.target.result as string;
      }
    };

    if (imageSource instanceof Blob) {
      reader.readAsDataURL(imageSource);
    }
  }

  /**
   * Draw image on canvas
   */
  private drawImage(img: HTMLImageElement): void {
    this.ctx.clearRect(0, 0, this.canvasElement.width, this.canvasElement.height);
    this.ctx.drawImage(
      img,
      0,
      0,
      this.canvasElement.width,
      this.canvasElement.height
    );

    // Store original image data for filter operations
    this.originalImageData = this.ctx.getImageData(
      0,
      0,
      this.canvasElement.width,
      this.canvasElement.height
    );
    this.currentFilter = 'None';

    console.log('Image drawn on canvas');
  }

  /**
   * Draw stats overlay on canvas
   */
  public drawStatsOverlay(stats: FrameStats): void {
    const padding = 20;
    const lineHeight = 32;
    const boxWidth = 400;
    const boxHeight = 180;

    // Draw semi-transparent background
    this.overlayCtx.fillStyle = 'rgba(0, 0, 0, 0.7)';
    this.overlayCtx.fillRect(padding, padding, boxWidth, boxHeight);

    // Draw border
    this.overlayCtx.strokeStyle = '#00FF00';
    this.overlayCtx.lineWidth = 2;
    this.overlayCtx.strokeRect(padding, padding, boxWidth, boxHeight);

    // Set text properties
    this.overlayCtx.fillStyle = '#00FF00';
    this.overlayCtx.font = 'bold 18px Courier New';
    this.overlayCtx.textBaseline = 'top';

    // Draw title
    this.overlayCtx.font = 'bold 20px Courier New';
    this.overlayCtx.fillText('Frame Stats', padding + 10, padding + 10);

    // Draw stats
    this.overlayCtx.font = 'bold 16px Courier New';
    let yOffset = padding + 45;

    const statsData = [
      `FPS: ${stats.fps.toFixed(1)}`,
      `Resolution: ${stats.resolution}`,
      `Filter: ${this.currentFilter}`,
      `Processing: ${stats.processingTime.toFixed(2)}ms`,
      `Timestamp: ${stats.timestamp}`
    ];

    statsData.forEach((stat) => {
      this.overlayCtx.fillText(stat, padding + 15, yOffset);
      yOffset += lineHeight;
    });

    console.log('Stats overlay drawn', stats);
  }

  /**
   * Display frame with sample stats
   */
  public displaySampleFrame(imagePath: string): void {
    const img = new Image();

    img.onload = () => {
      this.drawImage(img);

      // Create sample stats
      const sampleStats: FrameStats = {
        fps: 25.3,
        resolution: '1920x1080',
        filter: 'Canny Edge',
        timestamp: new Date().toISOString().split('T')[1].split('.')[0],
        processingTime: 38.5
      };

      this.drawStatsOverlay(sampleStats);
      this.updateStatsPanel(sampleStats);
    };

    img.onerror = () => {
      this.showError('Failed to load sample image');
    };

    img.src = imagePath;
  }

  /**
   * Update stats panel
   */
  private updateStatsPanel(stats: FrameStats): void {
    const statsHTML = `
      <div class="stat-row">
        <span class="stat-label">FPS:</span>
        <span class="stat-value">${stats.fps.toFixed(1)}</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">Resolution:</span>
        <span class="stat-value">${stats.resolution}</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">Filter:</span>
        <span class="stat-value">${this.currentFilter}</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">Processing Time:</span>
        <span class="stat-value">${stats.processingTime.toFixed(2)}ms</span>
      </div>
      <div class="stat-row">
        <span class="stat-label">Timestamp:</span>
        <span class="stat-value">${stats.timestamp}</span>
      </div>
    `;

    this.statsElement.innerHTML = statsHTML;
  }

  /**
   * Refresh stats panel with current filter
   */
  private refreshStatsPanel(): void {
    const statRows = this.statsElement.querySelectorAll('.stat-row');
    if (statRows.length > 0) {
      const filterRow = Array.from(statRows).find((row) =>
        row.textContent?.includes('Filter:')
      );
      if (filterRow) {
        const valueElement = filterRow.querySelector('.stat-value');
        if (valueElement) {
          valueElement.textContent = this.currentFilter;
        }
      }
    }
  }

  /**
   * Show error message
   */
  private showError(message: string): void {
    const errorElement = document.getElementById('error-message');
    if (errorElement) {
      errorElement.textContent = message;
      errorElement.style.display = 'block';
      setTimeout(() => {
        errorElement.style.display = 'none';
      }, 5000);
    }
    console.error(message);
  }

  /**
   * Handle file input change
   */
  public handleFileInput(file: File): void {
    if (!file.type.startsWith('image/')) {
      this.showError('Please select a valid image file');
      return;
    }

    const reader = new FileReader();

    reader.onload = (event: ProgressEvent<FileReader>) => {
      if (event.target?.result) {
        const img = new Image();

        img.onload = () => {
          this.drawImage(img);

          // Create stats based on loaded image
          const stats: FrameStats = {
            fps: 24.8,
            resolution: `${img.width}x${img.height}`,
            filter: 'Canny Edge',
            timestamp: new Date().toLocaleTimeString(),
            processingTime: 35.2
          };

          this.drawStatsOverlay(stats);
          this.updateStatsPanel(stats);
        };

        img.src = event.target.result as string;
      }
    };

    reader.readAsDataURL(file);
  }

  /**
   * Apply grayscale filter using OpenCV
   */
  public applyGrayscaleFilter(): void {
    if (!this.originalImageData) return;

    if (!this.isOpenCvReady()) {
      // Fallback to canvas-based grayscale
      this.applyGrayscaleFilterCanvas();
      return;
    }

    try {
      const startTime = performance.now();

      // Convert canvas to Mat
      const src = cv.imread(this.canvasElement);
      const dst = new cv.Mat();

      // Convert to grayscale
      cv.cvtColor(src, dst, cv.COLOR_RGBA2GRAY);

      // Display result
      cv.imshow(this.canvasElement, dst);

      // Cleanup
      src.delete();
      dst.delete();

      const processingTime = performance.now() - startTime;
      console.log(`Grayscale applied (${processingTime.toFixed(2)}ms)`);

      this.currentFilter = 'Grayscale';
      this.refreshStatsPanel();
    } catch (error) {
      console.error('OpenCV grayscale error:', error);
      this.applyGrayscaleFilterCanvas();
    }
  }

  /**
   * Apply grayscale filter using canvas (fallback)
   */
  private applyGrayscaleFilterCanvas(): void {
    if (!this.originalImageData) return;

    const imageData = new ImageData(
      new Uint8ClampedArray(this.originalImageData.data),
      this.originalImageData.width,
      this.originalImageData.height
    );

    const data = imageData.data;
    for (let i = 0; i < data.length; i += 4) {
      const gray = data[i] * 0.299 + data[i + 1] * 0.587 + data[i + 2] * 0.114;
      data[i] = gray;
      data[i + 1] = gray;
      data[i + 2] = gray;
    }

    this.ctx.putImageData(imageData, 0, 0);
    this.currentFilter = 'Grayscale';
    this.refreshStatsPanel();
  }

  /**
   * Apply Canny edge detection filter using OpenCV
   */
  public applyCannyEdgeFilter(): void {
    if (!this.originalImageData) return;

    if (!this.isOpenCvReady()) {
      // Fallback to canvas-based edge detection
      this.applyCannyEdgeFilterCanvas();
      return;
    }

    try {
      const startTime = performance.now();

      // Convert canvas to Mat
      const src = cv.imread(this.canvasElement);
      const gray = new cv.Mat();
      const edges = new cv.Mat();
      const dst = new cv.Mat();

      // Convert to grayscale
      cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);

      // Apply Canny edge detection
      cv.Canny(gray, edges, 50, 150);

      // Convert back to RGBA for display
      cv.cvtColor(edges, dst, cv.COLOR_GRAY2RGBA);

      // Display result
      cv.imshow(this.canvasElement, dst);

      // Cleanup
      src.delete();
      gray.delete();
      edges.delete();
      dst.delete();

      const processingTime = performance.now() - startTime;
      console.log(`Canny edge detection applied (${processingTime.toFixed(2)}ms)`);

      this.currentFilter = 'Canny Edge';
      this.refreshStatsPanel();
    } catch (error) {
      console.error('OpenCV Canny error:', error);
      this.applyCannyEdgeFilterCanvas();
    }
  }

  /**
   * Apply Canny edge detection using canvas (fallback)
   */
  private applyCannyEdgeFilterCanvas(): void {
    if (!this.originalImageData) return;

    const imageData = new ImageData(
      new Uint8ClampedArray(this.originalImageData.data),
      this.originalImageData.width,
      this.originalImageData.height
    );

    const data = imageData.data;
    const width = imageData.width;
    const height = imageData.height;

    // Convert to grayscale
    for (let i = 0; i < data.length; i += 4) {
      const gray = data[i] * 0.299 + data[i + 1] * 0.587 + data[i + 2] * 0.114;
      data[i] = gray;
      data[i + 1] = gray;
      data[i + 2] = gray;
    }

    // Sobel operator for edge detection
    const edges = new Uint8ClampedArray(data.length);
    const threshold = 50;

    for (let y = 1; y < height - 1; y++) {
      for (let x = 1; x < width - 1; x++) {
        const idx = (y * width + x) * 4;

        // Sobel X
        const gx =
          -1 * this.getPixel(data, x - 1, y - 1, width) +
          1 * this.getPixel(data, x + 1, y - 1, width) +
          -2 * this.getPixel(data, x - 1, y, width) +
          2 * this.getPixel(data, x + 1, y, width) +
          -1 * this.getPixel(data, x - 1, y + 1, width) +
          1 * this.getPixel(data, x + 1, y + 1, width);

        // Sobel Y
        const gy =
          -1 * this.getPixel(data, x - 1, y - 1, width) +
          -2 * this.getPixel(data, x, y - 1, width) +
          -1 * this.getPixel(data, x + 1, y - 1, width) +
          1 * this.getPixel(data, x - 1, y + 1, width) +
          2 * this.getPixel(data, x, y + 1, width) +
          1 * this.getPixel(data, x + 1, y + 1, width);

        const magnitude = Math.sqrt(gx * gx + gy * gy);
        const edge = magnitude > threshold ? 255 : 0;

        edges[idx] = edge;
        edges[idx + 1] = edge;
        edges[idx + 2] = edge;
        edges[idx + 3] = 255;
      }
    }

    // Apply edges to image data
    for (let i = 0; i < data.length; i++) {
      data[i] = edges[i];
    }

    this.ctx.putImageData(imageData, 0, 0);
    this.currentFilter = 'Canny Edge';
    this.refreshStatsPanel();
  }

  /**
   * Helper to get pixel value
   */
  private getPixel(
    data: Uint8ClampedArray,
    x: number,
    y: number,
    width: number
  ): number {
    return data[(y * width + x) * 4];
  }

  /**
   * Reset to original image
   */
  private resetToOriginal(): void {
    if (!this.originalImageData) return;
    this.ctx.putImageData(this.originalImageData, 0, 0);
    this.currentFilter = 'None';
  }

  /**
   * Clear canvas and stats
   */
  public clearFrame(): void {
    this.ctx.clearRect(0, 0, this.canvasElement.width, this.canvasElement.height);
    this.overlayCtx.clearRect(0, 0, this.overlayCanvas.width, this.overlayCanvas.height);
    this.statsElement.innerHTML = '';
    this.originalImageData = null;
    this.currentFilter = 'None';
    console.log('Frame cleared');
  }

  /**
   * Save canvas as image
   */
  public saveFrame(): void {
    const link = document.createElement('a');
    link.href = this.canvasElement.toDataURL('image/png');
    link.download = `frame-${Date.now()}.png`;
    link.click();
    console.log('Frame saved');
  }
}

/**
 * Wait for OpenCV.js to be ready
 */
function waitForOpenCv(callback: () => void): void {
  if (typeof cv !== 'undefined' && cv.Mat !== undefined) {
    callback();
  } else {
    setTimeout(() => waitForOpenCv(callback), 100);
  }
}

/**
 * Initialize app when DOM is ready
 */
document.addEventListener('DOMContentLoaded', () => {
  try {
    const viewer = new FrameViewer();

    // Wait for OpenCV to be ready
    waitForOpenCv(() => {
      console.log('OpenCV.js is ready');
    });

    // Camera buttons
    const startCameraBtn = document.getElementById('start-camera-btn') as HTMLButtonElement;
    if (startCameraBtn) {
      startCameraBtn.addEventListener('click', async () => {
        await viewer.startCamera();
        startCameraBtn.disabled = true;
        const stopBtn = document.getElementById('stop-camera-btn') as HTMLButtonElement;
        if (stopBtn) stopBtn.disabled = false;
        const captureBtn = document.getElementById('capture-btn') as HTMLButtonElement;
        if (captureBtn) captureBtn.disabled = false;
      });
    }

    const stopCameraBtn = document.getElementById('stop-camera-btn') as HTMLButtonElement;
    if (stopCameraBtn) {
      stopCameraBtn.addEventListener('click', () => {
        viewer.stopCamera();
        stopCameraBtn.disabled = true;
        if (startCameraBtn) startCameraBtn.disabled = false;
        const captureBtn = document.getElementById('capture-btn') as HTMLButtonElement;
        if (captureBtn) captureBtn.disabled = true;
      });
    }

    const captureBtn = document.getElementById('capture-btn');
    if (captureBtn) {
      captureBtn.addEventListener('click', () => {
        viewer.captureFrame();
      });
    }

    // File input handler
    const fileInput = document.getElementById('file-input') as HTMLInputElement;
    if (fileInput) {
      fileInput.addEventListener('change', (event: Event) => {
        const target = event.target as HTMLInputElement;
        if (target.files?.[0]) {
          viewer.handleFileInput(target.files[0]);
        }
      });
    }

    // Load sample button
    const loadSampleBtn = document.getElementById('load-sample-btn');
    if (loadSampleBtn) {
      loadSampleBtn.addEventListener('click', () => {
        viewer.displaySampleFrame('./sample-frame.jpg');
      });
    }

    // Clear button
    const clearBtn = document.getElementById('clear-btn');
    if (clearBtn) {
      clearBtn.addEventListener('click', () => {
        viewer.clearFrame();
      });
    }

    // Save button
    const saveBtn = document.getElementById('save-btn');
    if (saveBtn) {
      saveBtn.addEventListener('click', () => {
        viewer.saveFrame();
      });
    }

    // Grayscale filter button
    const grayscaleBtn = document.getElementById('grayscale-btn');
    if (grayscaleBtn) {
      grayscaleBtn.addEventListener('click', () => {
        viewer.applyGrayscaleFilter();
      });
    }

    // Canny edge filter button
    const cannyBtn = document.getElementById('canny-btn');
    if (cannyBtn) {
      cannyBtn.addEventListener('click', () => {
        viewer.applyCannyEdgeFilter();
      });
    }

    console.log('Edge Detection Frame Viewer initialized');
  } catch (error) {
    console.error('Failed to initialize viewer:', error);
  }
});

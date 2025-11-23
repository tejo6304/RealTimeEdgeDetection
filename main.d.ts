/**
 * Edge Detection Frame Viewer with Live Camera Support
 * Displays processed camera frames with real-time statistics overlay
 * Uses OpenCV.js for Canny edge detection and grayscale conversion
 */
declare var cv: any;
interface FrameStats {
    fps: number;
    resolution: string;
    filter: string;
    timestamp: string;
    processingTime: number;
}
declare class FrameViewer {
    private canvasElement;
    private ctx;
    private statsElement;
    private overlayCanvas;
    private overlayCtx;
    private currentFilter;
    private originalImageData;
    private videoStream;
    private cameraActive;
    private animationId;
    private frameCount;
    private lastFrameTime;
    private fps;
    private outputCanvas;
    private outputCtx;
    constructor();
    /**
     * Get DOM element with type checking
     */
    private getElement;
    /**
     * Get 2D canvas context
     */
    private getContext;
    /**
     * Initialize canvas dimensions
     */
    private initializeCanvas;
    /**
     * Check if OpenCV.js is loaded
     */
    private isOpenCvReady;
    /**
     * Start camera stream
     */
    startCamera(): Promise<void>;
    /**
     * Stop camera stream
     */
    stopCamera(): void;
    /**
     * Process frames continuously from camera
     */
    private processFramesContinuous;
    /**
     * Capture frame from camera or canvas
     */
    captureFrame(): void;
    /**
     * Load image from file input or URL
     */
    loadImage(imageSource: string | Blob): void;
    /**
     * Draw image on canvas
     */
    private drawImage;
    /**
     * Draw stats overlay on canvas
     */
    drawStatsOverlay(stats: FrameStats): void;
    /**
     * Display frame with sample stats
     */
    displaySampleFrame(imagePath: string): void;
    /**
     * Update stats panel
     */
    private updateStatsPanel;
    /**
     * Refresh stats panel with current filter
     */
    private refreshStatsPanel;
    /**
     * Show error message
     */
    private showError;
    /**
     * Handle file input change
     */
    handleFileInput(file: File): void;
    /**
     * Apply grayscale filter using OpenCV
     */
    applyGrayscaleFilter(): void;
    /**
     * Apply grayscale filter using canvas (fallback)
     */
    private applyGrayscaleFilterCanvas;
    /**
     * Apply Canny edge detection filter using OpenCV
     */
    applyCannyEdgeFilter(): void;
    /**
     * Apply Canny edge detection using canvas (fallback)
     */
    private applyCannyEdgeFilterCanvas;
    /**
     * Helper to get pixel value
     */
    private getPixel;
    /**
     * Reset to original image
     */
    private resetToOriginal;
    /**
     * Clear canvas and stats
     */
    clearFrame(): void;
    /**
     * Save canvas as image
     */
    saveFrame(): void;
}
/**
 * Wait for OpenCV.js to be ready
 */
declare function waitForOpenCv(callback: () => void): void;
//# sourceMappingURL=main.d.ts.map
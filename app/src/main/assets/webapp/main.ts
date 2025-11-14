interface AndroidBridge {
    getProcessedFrame(callback: (frame: string) => void): void;
}

declare var Android: AndroidBridge;

const processedFrame = document.getElementById('processedFrame') as HTMLImageElement;

function updateFrame() {
    if (typeof Android !== 'undefined' && Android.getProcessedFrame) {
        Android.getProcessedFrame((frame: string) => {
            processedFrame.src = "data:image/jpeg;base64," + frame;
        });
    } else {
        // Fallback for when not running in the Android WebView
        // You can replace this with a placeholder image
        processedFrame.src = 'https://via.placeholder.com/640x480.png?text=No+Data+From+Native';
    }
}

// Update the frame every second
setInterval(updateFrame, 1000);

// Initial update
updateFrame();

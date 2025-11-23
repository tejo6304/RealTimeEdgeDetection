class FilteredImageGallery {
    constructor() {
        this.images = [];
        this.galleryContainer = document.getElementById('gallery-container');
        this.refreshBtn = document.getElementById('refresh-btn');
        this.backBtn = document.getElementById('back-btn');
        this.setupEventListeners();
        this.loadImages();
    }
    setupEventListeners() {
        this.refreshBtn.addEventListener('click', () => this.loadImages());
        this.backBtn.addEventListener('click', () => this.goBackToCamera());
    }
    async loadImages() {
        try {
            // Check if Android bridge is available
            if (window.ImageGalleryBridge) {
                const jsonData = window.ImageGalleryBridge.getImages();
                this.images = JSON.parse(jsonData);
                console.log('Loaded images from Android bridge:', this.images);
            }
            else {
                // Fallback: Try to load from localStorage (for testing)
                const stored = localStorage.getItem('capturedImages');
                this.images = stored ? JSON.parse(stored) : [];
                console.warn('Android bridge not available, using localStorage fallback');
            }
            this.renderGallery();
        }
        catch (error) {
            console.error('Error loading images:', error);
            this.showEmptyState();
        }
    }
    renderGallery() {
        if (this.images.length === 0) {
            this.showEmptyState();
            return;
        }
        this.galleryContainer.innerHTML = '';
        this.images.forEach((image) => {
            const card = this.createImageCard(image);
            this.galleryContainer.appendChild(card);
        });
    }
    createImageCard(image) {
        const card = document.createElement('div');
        card.className = 'image-card';
        const imageWrapper = document.createElement('div');
        imageWrapper.className = 'image-wrapper';
        const img = document.createElement('img');
        img.src = image.path;
        img.alt = image.name;
        img.onerror = (error) => {
            console.error('Failed to load image:', image.path, error);
            img.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="100" height="100"%3E%3Crect fill="%23333" width="100" height="100"/%3E%3Ctext x="50" y="50" text-anchor="middle" dy=".3em" fill="%23666" font-size="12"%3EFailed to Load%3C/text%3E%3C/svg%3E';
        };
        img.onload = () => {
            console.log('Image loaded successfully:', image.path);
        };
        imageWrapper.appendChild(img);
        const info = document.createElement('div');
        info.className = 'image-info';
        const filterBadge = document.createElement('div');
        filterBadge.className = 'filter-badge';
        filterBadge.textContent = image.filter || 'Unknown';
        const imageName = document.createElement('div');
        imageName.className = 'image-name';
        imageName.textContent = image.name;
        const stats = document.createElement('div');
        stats.className = 'image-stats';
        const date = new Date(image.timestamp);
        const sizeInfo = image.width && image.height ? `${image.width}×${image.height}` : 'N/A';
        stats.textContent = `${date.toLocaleString()} | ${sizeInfo}`;
        info.appendChild(filterBadge);
        info.appendChild(imageName);
        info.appendChild(stats);
        card.appendChild(imageWrapper);
        card.appendChild(info);
        return card;
    }
    showEmptyState() {
        this.galleryContainer.innerHTML = `
            <div class="empty-state">
                <h2>📸 No Images Yet</h2>
                <p>Capture images from the camera to see them here with applied filters.</p>
            </div>
        `;
    }
    goBackToCamera() {
        // Check if Android bridge is available
        if (window.ImageGalleryBridge) {
            window.ImageGalleryBridge.goBack();
        }
        else {
            // Fallback: Close the window or navigate back
            if (window.history.length > 1) {
                window.history.back();
            }
            else {
                window.close();
            }
        }
    }
}
// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    new FilteredImageGallery();
});

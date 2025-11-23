package com.example.realtimeedgedetection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for handling image storage and retrieval
 */
public class ImageStorageUtils {
    private static final String TAG = "ImageStorageUtils";
    private static final String IMAGES_FOLDER_NAME = "RealTimeEdgeDetection";
    private static final String IMAGE_PREFIX = "edge_detection_";
    private static final String IMAGE_EXTENSION = ".png";

    private Context context;
    private File imagesDirectory;

    public ImageStorageUtils(Context context) {
        this.context = context;
        this.imagesDirectory = getSavedImagesDirectory();
        
        // Create directory if it doesn't exist
        if (!imagesDirectory.exists()) {
            if (imagesDirectory.mkdirs()) {
                Log.d(TAG, "Images directory created: " + imagesDirectory.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to create images directory");
            }
        }
    }

    /**
     * Get or create the saved images directory
     */
    public File getSavedImagesDirectory() {
        File picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDir == null) {
            // Fallback to cache directory if external storage is not available
            picturesDir = context.getCacheDir();
        }
        return new File(picturesDir, IMAGES_FOLDER_NAME);
    }

    /**
     * Save a bitmap to file with timestamp and filter metadata
     */
    public File saveBitmap(Bitmap bitmap) {
        return saveBitmapWithFilter(bitmap, "original");
    }

    /**
     * Save a bitmap to file with timestamp and filter type
     */
    public File saveBitmapWithFilter(Bitmap bitmap, String filterType) {
        if (bitmap == null) {
            Log.e(TAG, "Cannot save null bitmap");
            return null;
        }

        try {
            String filename = generateFilenameWithFilter(filterType);
            File imageFile = new File(imagesDirectory, filename);

            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            Log.d(TAG, "Image saved successfully: " + imageFile.getAbsolutePath());
            return imageFile;

        } catch (Exception e) {
            Log.e(TAG, "Error saving bitmap: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generate filename with current timestamp and filter type
     */
    private String generateFilenameWithFilter(String filterType) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.US);
        String timestamp = sdf.format(new Date());
        return IMAGE_PREFIX + filterType + "_" + timestamp + IMAGE_EXTENSION;
    }

    /**
     * Get all saved images sorted by date (newest first)
     */
    public List<File> getAllSavedImages() {
        List<File> imageFiles = new ArrayList<>();

        if (imagesDirectory.exists() && imagesDirectory.isDirectory()) {
            File[] files = imagesDirectory.listFiles((dir, name) -> 
                name.startsWith(IMAGE_PREFIX) && name.endsWith(IMAGE_EXTENSION)
            );

            if (files != null && files.length > 0) {
                // Sort by date (newest first)
                Arrays.sort(files, new Comparator<File>() {
                    @Override
                    public int compare(File f1, File f2) {
                        return Long.compare(f2.lastModified(), f1.lastModified());
                    }
                });

                imageFiles.addAll(Arrays.asList(files));
                Log.d(TAG, "Found " + imageFiles.size() + " saved images");
            }
        }

        return imageFiles;
    }

    /**
     * Delete an image file
     */
    public boolean deleteImage(File imageFile) {
        if (imageFile != null && imageFile.exists()) {
            boolean deleted = imageFile.delete();
            if (deleted) {
                Log.d(TAG, "Image deleted: " + imageFile.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to delete image: " + imageFile.getAbsolutePath());
            }
            return deleted;
        }
        return false;
    }

    /**
     * Delete all saved images
     */
    public boolean deleteAllImages() {
        List<File> images = getAllSavedImages();
        boolean allDeleted = true;

        for (File image : images) {
            if (!deleteImage(image)) {
                allDeleted = false;
            }
        }

        return allDeleted;
    }

    /**
     * Get the number of saved images
     */
    public int getSavedImageCount() {
        return getAllSavedImages().size();
    }

    /**
     * Get total size of all saved images in bytes
     */
    public long getTotalImageSize() {
        long totalSize = 0;
        List<File> images = getAllSavedImages();

        for (File image : images) {
            totalSize += image.length();
        }

        return totalSize;
    }

    /**
     * Get the most recent image
     */
    public File getMostRecentImage() {
        List<File> images = getAllSavedImages();
        return images.isEmpty() ? null : images.get(0);
    }

    /**
     * Check if gallery has any images
     */
    public boolean hasImages() {
        return getSavedImageCount() > 0;
    }

    /**
     * Clear all images and delete directory
     */
    public boolean clearGallery() {
        boolean allDeleted = deleteAllImages();
        if (imagesDirectory.exists()) {
            if (imagesDirectory.delete()) {
                Log.d(TAG, "Gallery directory deleted");
                return allDeleted;
            }
        }
        return allDeleted;
    }

    /**
     * Get all captured images from external storage (for web viewer)
     */
    public static List<File> getAllCapturedImages(Context context) {
        // Use the same directory as saveBitmapWithFilter
        File picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDir == null) {
            picturesDir = context.getCacheDir();
        }
        File capturedDir = new File(picturesDir, IMAGES_FOLDER_NAME);
        List<File> images = new ArrayList<>();

        if (capturedDir.exists() && capturedDir.isDirectory()) {
            File[] files = capturedDir.listFiles((dir, name) -> 
                name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
            );

            if (files != null && files.length > 0) {
                Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                images.addAll(Arrays.asList(files));
            }
        }

        return images;
    }

    /**
     * Get image metadata from filename
     */
    public static ImageMetadata getImageMetadata(File file) {
        try {
            String filename = file.getName();
            ImageMetadata metadata = new ImageMetadata();
            metadata.fileName = filename;
            
            // Extract filter type from filename if available
            if (filename.contains("grayscale")) {
                metadata.filter = "Grayscale";
            } else if (filename.contains("canny")) {
                metadata.filter = "Canny Edge";
            } else {
                metadata.filter = "Original";
            }
            
            // Load bitmap to get dimensions
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                metadata.width = bitmap.getWidth();
                metadata.height = bitmap.getHeight();
                bitmap.recycle();
            }
            
            return metadata;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Image metadata class
     */
    public static class ImageMetadata {
        public String fileName;
        public String filter;
        public int width;
        public int height;
    }
}

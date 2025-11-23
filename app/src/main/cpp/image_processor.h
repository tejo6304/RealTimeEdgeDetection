#ifndef IMAGE_PROCESSOR_H
#define IMAGE_PROCESSOR_H

#include <opencv2/core.hpp>

class ImageProcessor {
public:
    static cv::Mat convertToGrayscale(const cv::Mat& input);
    static cv::Mat applyCanny(const cv::Mat& input);
    static cv::Mat processImage(const cv::Mat& input, int filterType);
    
private:
    static const int CANNY_LOWER_THRESHOLD;
    static const int CANNY_UPPER_THRESHOLD;
};

#endif // IMAGE_PROCESSOR_H

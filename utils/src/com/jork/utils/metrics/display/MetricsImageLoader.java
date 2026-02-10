package com.jork.utils.metrics.display;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import com.jork.utils.ExceptionUtils;
import com.jork.utils.ScriptLogger;

/**
 * Utility class for loading and resizing PNG images for the metrics display.
 * Supports various scaling modes and caches loaded images for performance.
 */
public class MetricsImageLoader {
    
    /**
     * Scaling modes for image resizing
     */
    public enum ScaleMode {
        STRETCH,     // Stretch to exact dimensions (may distort)
        FIT,         // Scale to fit within bounds, preserve aspect ratio
        TILE,        // Repeat pattern to fill area
        CENTER,      // Center without scaling
        COVER        // Scale to cover entire area (may crop)
    }
    
    // Cache for loaded and resized images to avoid repeated processing
    private static final Map<String, BufferedImage> imageCache = new HashMap<>();
    
    /**
     * Loads and resizes an image from the specified path.
     * Images are cached after first load for performance.
     * 
     * @param path Path to the image file
     * @param targetWidth Target width (use -1 to calculate from aspect ratio)
     * @param targetHeight Target height (use -1 to calculate from aspect ratio)
     * @param mode Scaling mode to use
     * @return Resized BufferedImage, or null if loading fails
     */
    public static BufferedImage loadAndResize(String path, int targetWidth, int targetHeight, ScaleMode mode) {
        return loadAndResize(path, targetWidth, targetHeight, mode, false);
    }

    /**
     * Loads and resizes an image from the specified path, with optional transparent-edge trimming.
     *
     * @param path Path to the image file
     * @param targetWidth Target width (use -1 to calculate from aspect ratio)
     * @param targetHeight Target height (use -1 to calculate from aspect ratio)
     * @param mode Scaling mode to use
     * @param trimTransparentPadding Whether to trim fully transparent padding before resizing
     * @return Resized BufferedImage, or null if loading fails
     */
    public static BufferedImage loadAndResize(
        String path,
        int targetWidth,
        int targetHeight,
        ScaleMode mode,
        boolean trimTransparentPadding
    ) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        
        // Generate cache key
        String cacheKey = path + "_" + targetWidth + "x" + targetHeight + "_" + mode + "_trim=" + trimTransparentPadding;
        
        // Check cache first
        if (imageCache.containsKey(cacheKey)) {
            return imageCache.get(cacheKey);
        }
        
        try {
            // Load original image
            BufferedImage original = loadImage(path);
            if (original == null) {
                return null;
            }

            if (trimTransparentPadding) {
                original = trimTransparentBorders(original);
            }
            
            // Calculate dimensions if needed
            if (targetWidth == -1 && targetHeight > 0) {
                float aspectRatio = (float) original.getWidth() / original.getHeight();
                targetWidth = Math.round(targetHeight * aspectRatio);
            } else if (targetHeight == -1 && targetWidth > 0) {
                float aspectRatio = (float) original.getHeight() / original.getWidth();
                targetHeight = Math.round(targetWidth * aspectRatio);
            }
            
            // Resize based on mode
            BufferedImage resized = resizeImage(original, targetWidth, targetHeight, mode);
            
            // Cache the result
            imageCache.put(cacheKey, resized);
            
            return resized;
            
        } catch (Exception e) {
            ExceptionUtils.rethrowIfTaskInterrupted(e);
            ScriptLogger.warning(null, "Failed to load/resize image: " + path + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Trims fully transparent border pixels around an image.
     * If no opaque pixels are found, returns the original image unchanged.
     */
    private static BufferedImage trimTransparentBorders(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();

        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (original.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha > 0) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }

        // Entire image is transparent (or no trimming needed) - keep original.
        if (maxX < minX || maxY < minY) {
            return original;
        }
        if (minX == 0 && minY == 0 && maxX == width - 1 && maxY == height - 1) {
            return original;
        }

        int trimmedWidth = maxX - minX + 1;
        int trimmedHeight = maxY - minY + 1;
        BufferedImage trimmed = new BufferedImage(trimmedWidth, trimmedHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = trimmed.createGraphics();
        g2d.drawImage(
            original,
            0,
            0,
            trimmedWidth,
            trimmedHeight,
            minX,
            minY,
            maxX + 1,
            maxY + 1,
            null
        );
        g2d.dispose();

        return trimmed;
    }
    
    /**
     * Loads an image from file or resource path
     */
    private static BufferedImage loadImage(String path) throws IOException {
        // Log the attempt
        System.out.println("[MetricsImageLoader] Attempting to load image: " + path);
        
        // First try as absolute file path
        File file = new File(path);
        if (file.exists()) {
            System.out.println("[MetricsImageLoader] Found as file: " + file.getAbsolutePath());
            return ImageIO.read(file);
        }
        
        // Try as resource from classpath (remove leading slash if present)
        String resourcePath = path.startsWith("/") ? path.substring(1) : path;
        
        // Try different resource loading approaches
        InputStream stream = MetricsImageLoader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) {
            stream = MetricsImageLoader.class.getResourceAsStream("/" + resourcePath);
        }
        if (stream == null) {
            stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        }
        
        if (stream != null) {
            System.out.println("[MetricsImageLoader] Found as resource: " + resourcePath);
            BufferedImage image = ImageIO.read(stream);
            stream.close();
            return image;
        }
        
        // Try relative to user home/.osmb/Scripts
        File scriptFile = new File(System.getProperty("user.home"), ".osmb/Scripts/" + path);
        if (scriptFile.exists()) {
            System.out.println("[MetricsImageLoader] Found in Scripts folder: " + scriptFile.getAbsolutePath());
            return ImageIO.read(scriptFile);
        }
        
        System.out.println("[MetricsImageLoader] Image not found in any location");
        throw new IOException("Image not found: " + path);
    }
    
    /**
     * Resizes an image based on the specified scale mode
     */
    private static BufferedImage resizeImage(BufferedImage original, int targetWidth, int targetHeight, ScaleMode mode) {
        switch (mode) {
            case STRETCH:
                return stretchImage(original, targetWidth, targetHeight);
            case FIT:
                return fitImage(original, targetWidth, targetHeight);
            case TILE:
                return tileImage(original, targetWidth, targetHeight);
            case COVER:
                return coverImage(original, targetWidth, targetHeight);
            case CENTER:
            default:
                return centerImage(original, targetWidth, targetHeight);
        }
    }
    
    /**
     * Stretches image to exact dimensions (may distort)
     */
    private static BufferedImage stretchImage(BufferedImage original, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        return resized;
    }
    
    /**
     * Scales image to fit within bounds while preserving aspect ratio
     */
    private static BufferedImage fitImage(BufferedImage original, int targetWidth, int targetHeight) {
        float widthRatio = (float) targetWidth / original.getWidth();
        float heightRatio = (float) targetHeight / original.getHeight();
        float scale = Math.min(widthRatio, heightRatio);
        
        int scaledWidth = Math.round(original.getWidth() * scale);
        int scaledHeight = Math.round(original.getHeight() * scale);
        
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();
        
        // Fill with black background instead of transparent
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, targetWidth, targetHeight);
        
        // Center the scaled image
        int x = (targetWidth - scaledWidth) / 2;
        int y = (targetHeight - scaledHeight) / 2;
        
        // Turn OFF anti-aliasing to prevent white edge artifacts
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        
        g2d.drawImage(original, x, y, scaledWidth, scaledHeight, null);
        g2d.dispose();
        
        return resized;
    }
    
    /**
     * Tiles image to fill the target area
     */
    private static BufferedImage tileImage(BufferedImage original, int targetWidth, int targetHeight) {
        BufferedImage tiled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = tiled.createGraphics();
        
        int imgWidth = original.getWidth();
        int imgHeight = original.getHeight();
        
        for (int x = 0; x < targetWidth; x += imgWidth) {
            for (int y = 0; y < targetHeight; y += imgHeight) {
                g2d.drawImage(original, x, y, null);
            }
        }
        
        g2d.dispose();
        return tiled;
    }
    
    /**
     * Scales image to cover entire area (may crop edges)
     */
    private static BufferedImage coverImage(BufferedImage original, int targetWidth, int targetHeight) {
        float widthRatio = (float) targetWidth / original.getWidth();
        float heightRatio = (float) targetHeight / original.getHeight();
        float scale = Math.max(widthRatio, heightRatio);
        
        int scaledWidth = Math.round(original.getWidth() * scale);
        int scaledHeight = Math.round(original.getHeight() * scale);
        
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();
        
        // Center and crop
        int x = (targetWidth - scaledWidth) / 2;
        int y = (targetHeight - scaledHeight) / 2;
        
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(original, x, y, scaledWidth, scaledHeight, null);
        g2d.dispose();
        
        return resized;
    }
    
    /**
     * Centers image without scaling
     */
    private static BufferedImage centerImage(BufferedImage original, int targetWidth, int targetHeight) {
        BufferedImage centered = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = centered.createGraphics();
        
        // Make background transparent
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, targetWidth, targetHeight);
        g2d.setComposite(AlphaComposite.SrcOver);
        
        // Center the original image
        int x = (targetWidth - original.getWidth()) / 2;
        int y = (targetHeight - original.getHeight()) / 2;
        
        g2d.drawImage(original, x, y, null);
        g2d.dispose();
        
        return centered;
    }
    
    /**
     * Converts BufferedImage to OSMB Image pixel array format
     * @param bufferedImage The BufferedImage to convert
     * @return int array of ARGB pixels
     */
    public static int[] toPixelArray(BufferedImage bufferedImage) {
        if (bufferedImage == null) {
            return null;
        }
        
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        int[] pixels = new int[width * height];
        bufferedImage.getRGB(0, 0, width, height, pixels, 0, width);
        return pixels;
    }
    
    /**
     * Clears the image cache to free memory
     */
    public static void clearCache() {
        imageCache.clear();
    }
    
    /**
     * Gets the current cache size
     */
    public static int getCacheSize() {
        return imageCache.size();
    }
}

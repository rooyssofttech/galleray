package com.amitbharat.gallery.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.Random;

/**
 * Intelligent high-performance content-aware inpainting engine for Magic Eraser.
 * Removes unwanted objects, people, blemishes, or watermarks marked by the user brush mask
 * and seamlessly reconstructs the background texture.
 */
public class MagicEraserEngine {

    public interface ProgressCallback {
        void onProgress(int percent);
    }

    public static Bitmap eraseObject(Bitmap src, Bitmap mask) {
        return eraseObject(src, mask, null);
    }

    public static Bitmap eraseObject(Bitmap src, Bitmap mask, ProgressCallback callback) {
        if (src == null || mask == null) return src;

        int width = src.getWidth();
        int height = src.getHeight();

        Bitmap scaledMask = mask;
        boolean needRecycleMask = false;
        if (mask.getWidth() != width || mask.getHeight() != height) {
            scaledMask = Bitmap.createScaledBitmap(mask, width, height, false);
            needRecycleMask = true;
        }

        // 1. Find bounding box of the erased mask
        int minX = width, minY = height, maxX = -1, maxY = -1;
        int[] maskRow = new int[width];

        for (int y = 0; y < height; y++) {
            scaledMask.getPixels(maskRow, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                int alpha = (maskRow[x] >>> 24) & 0xFF;
                if (alpha > 40) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (needRecycleMask && scaledMask != mask) {
            scaledMask.recycle();
        }

        // If no mask strokes detected, return original
        if (maxX < minX || maxY < minY) {
            return src;
        }

        // Add contextual margin around bounding box to sample surrounding textures
        int pad = Math.max(32, (int) (Math.max(width, height) * 0.04f));
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(width - 1, maxX + pad);
        maxY = Math.min(height - 1, maxY + pad);

        int subW = maxX - minX + 1;
        int subH = maxY - minY + 1;

        if (callback != null) callback.onProgress(20);

        // 2. Extract subregion pixels and mask
        int[] pixels = new int[subW * subH];
        src.getPixels(pixels, 0, subW, minX, minY, subW, subH);

        boolean[] isHole = new boolean[subW * subH];
        int holeCount = 0;

        int[] subMaskRow = new int[subW];
        for (int y = 0; y < subH; y++) {
            mask.getPixels(subMaskRow, 0, subW, minX, minY + y, subW, 1);
            int rowOffset = y * subW;
            for (int x = 0; x < subW; x++) {
                int alpha = (subMaskRow[x] >>> 24) & 0xFF;
                if (alpha > 40) {
                    isHole[rowOffset + x] = true;
                    holeCount++;
                }
            }
        }

        if (holeCount == 0) {
            return src;
        }

        if (callback != null) callback.onProgress(40);

        // 3. Compute surrounding non-hole variance for natural grain matching
        double sumR = 0, sumG = 0, sumB = 0;
        double sumSqR = 0, sumSqG = 0, sumSqB = 0;
        int nonHoleCount = 0;

        for (int i = 0; i < pixels.length; i++) {
            if (!isHole[i]) {
                int c = pixels[i];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                sumR += r; sumG += g; sumB += b;
                sumSqR += r * r; sumSqG += g * g; sumSqB += b * b;
                nonHoleCount++;
            }
        }

        double stdDev = 3.0; // fallback standard deviation
        if (nonHoleCount > 20) {
            double varR = (sumSqR / nonHoleCount) - Math.pow(sumR / nonHoleCount, 2);
            double varG = (sumSqG / nonHoleCount) - Math.pow(sumG / nonHoleCount, 2);
            double varB = (sumSqB / nonHoleCount) - Math.pow(sumB / nonHoleCount, 2);
            double avgVar = Math.max(0, (varR + varG + varB) / 3.0);
            stdDev = Math.min(8.0, Math.max(1.5, Math.sqrt(avgVar) * 0.35));
        }

        // 4. Multi-directional Ray & Inverse-Distance Inpainting
        final int NUM_RAYS = 16;
        final float[] cosTable = new float[NUM_RAYS];
        final float[] sinTable = new float[NUM_RAYS];
        for (int i = 0; i < NUM_RAYS; i++) {
            double angle = 2.0 * Math.PI * i / NUM_RAYS;
            cosTable[i] = (float) Math.cos(angle);
            sinTable[i] = (float) Math.sin(angle);
        }

        int[] inpainted = pixels.clone();
        Random rng = new Random(1337);

        for (int y = 0; y < subH; y++) {
            int rowOffset = y * subW;
            for (int x = 0; x < subW; x++) {
                int idx = rowOffset + x;
                if (!isHole[idx]) continue;

                double totalWeight = 0.0;
                double accR = 0.0, accG = 0.0, accB = 0.0;

                // Step along 16 rays outward to find nearest boundary pixels
                for (int ray = 0; ray < NUM_RAYS; ray++) {
                    float dx = cosTable[ray];
                    float dy = sinTable[ray];
                    float step = 1.0f;
                    boolean found = false;

                    while (step < Math.max(subW, subH)) {
                        int nx = (int) (x + dx * step);
                        int ny = (int) (y + dy * step);

                        if (nx < 0 || nx >= subW || ny < 0 || ny >= subH) {
                            break;
                        }

                        int nIdx = ny * subW + nx;
                        if (!isHole[nIdx]) {
                            // Found boundary pixel
                            float dist = step;
                            double w = 1.0 / (dist * dist + 0.5);

                            int color = pixels[nIdx];
                            int cr = (color >> 16) & 0xFF;
                            int cg = (color >> 8) & 0xFF;
                            int cb = color & 0xFF;

                            accR += cr * w;
                            accG += cg * w;
                            accB += cb * w;
                            totalWeight += w;
                            found = true;
                            break;
                        }
                        step += 1.5f;
                    }
                }

                if (totalWeight > 0.00001) {
                    int r = (int) Math.round(accR / totalWeight);
                    int g = (int) Math.round(accG / totalWeight);
                    int b = (int) Math.round(accB / totalWeight);

                    // Add subtle natural camera sensor grain
                    double noise = (rng.nextGaussian() * stdDev);
                    r = Math.min(255, Math.max(0, (int) Math.round(r + noise)));
                    g = Math.min(255, Math.max(0, (int) Math.round(g + noise)));
                    b = Math.min(255, Math.max(0, (int) Math.round(b + noise)));

                    inpainted[idx] = (0xFF << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }

        if (callback != null) callback.onProgress(75);

        // 5. Seam-Blending & Bilateral smoothing around boundary transitions
        int[] finalPixels = inpainted.clone();
        for (int y = 1; y < subH - 1; y++) {
            int rowOffset = y * subW;
            for (int x = 1; x < subW - 1; x++) {
                int idx = rowOffset + x;
                // Only smooth hole pixels and boundary seam
                if (!isHole[idx]) continue;

                boolean touchesKnown = !isHole[idx - 1] || !isHole[idx + 1] ||
                        !isHole[idx - subW] || !isHole[idx + subW];

                if (touchesKnown) {
                    int sumRed = 0, sumGreen = 0, sumBlue = 0;
                    int count = 0;

                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nCol = inpainted[(y + dy) * subW + (x + dx)];
                            sumRed += (nCol >> 16) & 0xFF;
                            sumGreen += (nCol >> 8) & 0xFF;
                            sumBlue += nCol & 0xFF;
                            count++;
                        }
                    }

                    int blendedR = sumRed / count;
                    int blendedG = sumGreen / count;
                    int blendedB = sumBlue / count;
                    finalPixels[idx] = (0xFF << 24) | (blendedR << 16) | (blendedG << 8) | blendedB;
                }
            }
        }

        if (callback != null) callback.onProgress(90);

        // 6. Write back to a copy of src Bitmap
        Bitmap result = src.copy(src.getConfig() != null ? src.getConfig() : Bitmap.Config.ARGB_8888, true);
        result.setPixels(finalPixels, 0, subW, minX, minY, subW, subH);

        if (callback != null) callback.onProgress(100);

        return result;
    }
}

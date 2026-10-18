package de.germanminer.addon.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import de.germanminer.addon.map.BlueMapTileClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages GPU DynamicTextures for BlueMap tiles in Minecraft 26.3.
 * Crops BlueMap's 501x1002 dual-layer PNGs directly in native C memory via NativeImage
 * and renders via direct GpuTextureView + GpuSampler so server resourcepack reloads
 * never blank out the map.
 */
public class BlueMapTextureCache {
    public record TileGpuTexture(Identifier id, DynamicTexture texture) {}

    private final BlueMapTileClient tileClient;
    private final Map<BlueMapTileClient.TileKey, TileGpuTexture> uploadedTextures = new ConcurrentHashMap<>();

    public BlueMapTextureCache(BlueMapTileClient tileClient) {
        this.tileClient = tileClient;
    }

    /**
     * Draws a BlueMap tile directly onto GuiGraphicsExtractor if loaded, or triggers async load.
     * Returns true if the tile texture was drawn, or false if still loading.
     */
    public boolean drawTile(GuiGraphicsExtractor graphics, BlueMapTileClient.TileKey key, int x0, int y0, int width, int height) {
        TileGpuTexture gpuTex = getOrUploadTile(key);
        if (gpuTex == null) {
            return false;
        }
        try {
            DynamicTexture dyn = gpuTex.texture();
            graphics.blit(
                dyn.getTextureView(),
                dyn.getSampler(),
                x0, y0,
                x0 + width, y0 + height,
                0.0f, 1.0f,
                0.0f, 1.0f
            );
            return true;
        } catch (Exception e) {
            uploadedTextures.remove(key);
            return false;
        }
    }

    public TileGpuTexture getOrUploadTile(BlueMapTileClient.TileKey key) {
        TileGpuTexture existing = uploadedTextures.get(key);
        if (existing != null) {
            NativeImage pixels = existing.texture().getPixels();
            if (pixels != null && !pixels.isClosed()) {
                return existing;
            }
            uploadedTextures.remove(key);
        }

        byte[] pngBytes = tileClient.getOrRequestTilePng(key);
        if (pngBytes == null || pngBytes.length == 0) {
            return null;
        }

        try {
            NativeImage rawImage = NativeImage.read(pngBytes);
            int w = Math.min(BlueMapTileClient.TILE_PIXELS, rawImage.getWidth());
            int h = Math.min(BlueMapTileClient.TILE_PIXELS, rawImage.getHeight());

            NativeImage squareImage = new NativeImage(w, h, false);
            for (int py = 0; py < h; py++) {
                for (int px = 0; px < w; px++) {
                    int argb = rawImage.getPixel(px, py);
                    // Ensure opaque alpha on valid map pixels so tiles always render vividly
                    if ((argb >>> 24) == 0) {
                        argb = 0xFF0B1120;
                    } else {
                        argb = argb | 0xFF000000;
                    }
                    squareImage.setPixel(px, py, argb);
                }
            }
            rawImage.close();

            String safePath = String.format(
                "bluemap_lod%d_%s_%s",
                key.lod(),
                formatNeg(key.tileX()),
                formatNeg(key.tileZ())
            );
            Identifier id = Identifier.fromNamespaceAndPath("gm-addon", safePath);
            DynamicTexture dynamicTexture = new DynamicTexture(() -> safePath, squareImage);
            Minecraft.getInstance().getTextureManager().register(id, dynamicTexture);

            TileGpuTexture entry = new TileGpuTexture(id, dynamicTexture);
            uploadedTextures.put(key, entry);
            return entry;
        } catch (Exception e) {
            return null;
        }
    }

    public void clearGpuCache() {
        Minecraft mc = Minecraft.getInstance();
        for (TileGpuTexture entry : uploadedTextures.values()) {
            try {
                mc.getTextureManager().release(entry.id());
                entry.texture().close();
            } catch (Exception ignored) {
            }
        }
        uploadedTextures.clear();
        tileClient.clearCacheAndReload();
    }

    private static String formatNeg(int val) {
        return val < 0 ? "m" + Math.abs(val) : "p" + val;
    }
}

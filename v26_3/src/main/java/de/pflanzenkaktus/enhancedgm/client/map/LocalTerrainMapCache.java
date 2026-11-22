package de.pflanzenkaktus.enhancedgm.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders and caches a strictly surface-only local terrain minimap organized in 4x4 chunk (64x64 block) regions.
 * Never scans underground below the solid world surface, ensuring caves, ores, and entities are never exposed.
 */
public class LocalTerrainMapCache {
    public static final int REGION_CHUNKS = 4;
    public static final int REGION_BLOCKS = REGION_CHUNKS * 16; // 64 blocks
    private static final int MAX_GPU_REGIONS = 128;
    private static final int MAX_CHUNKS_PER_TICK = 6;
    private static final long CHUNK_REFRESH_MS = 4000L;
    private static final int UNEXPLORED_ARGB = 0xFF0B1120;

    public record RegionKey(int regionX, int regionZ) {}

    private static final class RegionTexture {
        final Identifier id;
        final DynamicTexture texture;
        final NativeImage image;
        boolean dirty = false;
        final long[] chunkLastUpdatedMs = new long[16];

        RegionTexture(Identifier id, DynamicTexture texture, NativeImage image) {
            this.id = id;
            this.texture = texture;
            this.image = image;
        }
    }

    private final Map<RegionKey, RegionTexture> regionTextures = Collections.synchronizedMap(
        new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<RegionKey, RegionTexture> eldest) {
                if (size() > MAX_GPU_REGIONS) {
                    releaseEntry(eldest.getValue());
                    return true;
                }
                return false;
            }
        }
    );

    public static int blockToRegionCoord(double blockCoord) {
        return Math.floorDiv((int) Math.floor(blockCoord), REGION_BLOCKS);
    }

    public void tickUpdate(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null) {
            return;
        }

        ClientLevel level = mc.level;
        int pChunkX = Math.floorDiv((int) Math.floor(mc.player.getX()), 16);
        int pChunkZ = Math.floorDiv((int) Math.floor(mc.player.getZ()), 16);

        int renderDist = mc.options.getEffectiveRenderDistance();
        int chunkRadius = Math.min(10, Math.max(4, renderDist));
        long nowMs = System.currentTimeMillis();
        int processed = 0;

        // Pass 1: Prioritize unrendered loaded chunks in expanding rings from player
        outerUnrendered:
        for (int r = 0; r <= chunkRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int cx = pChunkX + dx;
                    int cz = pChunkZ + dz;
                    if (tryRasterizeChunk(level, cx, cz, nowMs, false)) {
                        processed++;
                        if (processed >= MAX_CHUNKS_PER_TICK) {
                            break outerUnrendered;
                        }
                    }
                }
            }
        }

        // Pass 2: Periodically refresh nearby chunks around the player so surface edits stay current
        if (processed < MAX_CHUNKS_PER_TICK) {
            outerRefresh:
            for (int r = 0; r <= Math.min(3, chunkRadius); r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        int cx = pChunkX + dx;
                        int cz = pChunkZ + dz;
                        if (tryRasterizeChunk(level, cx, cz, nowMs, true)) {
                            processed++;
                            if (processed >= MAX_CHUNKS_PER_TICK) {
                                break outerRefresh;
                            }
                        }
                    }
                }
            }
        }

        // Upload any modified region textures to the GPU
        synchronized (regionTextures) {
            for (RegionTexture entry : regionTextures.values()) {
                if (entry.dirty) {
                    NativeImage pixels = entry.texture.getPixels();
                    if (pixels != null && !pixels.isClosed()) {
                        entry.texture.upload();
                    }
                    entry.dirty = false;
                }
            }
        }
    }

    private boolean tryRasterizeChunk(ClientLevel level, int chunkX, int chunkZ, long nowMs, boolean refreshOnly) {
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (chunk == null || chunk.isEmpty()) {
            return false;
        }

        int regX = Math.floorDiv(chunkX, REGION_CHUNKS);
        int regZ = Math.floorDiv(chunkZ, REGION_CHUNKS);
        int localChunkX = Math.floorMod(chunkX, REGION_CHUNKS);
        int localChunkZ = Math.floorMod(chunkZ, REGION_CHUNKS);
        int slot = localChunkZ * REGION_CHUNKS + localChunkX;

        RegionTexture region = getOrCreateRegion(new RegionKey(regX, regZ));
        if (region == null) {
            return false;
        }

        long lastUpdated = region.chunkLastUpdatedMs[slot];
        if (!refreshOnly) {
            if (lastUpdated != 0L) {
                return false;
            }
        } else {
            if (lastUpdated == 0L || (nowMs - lastUpdated) < CHUNK_REFRESH_MS) {
                return false;
            }
        }

        rasterizeChunk(level, chunk, chunkX, chunkZ, region, localChunkX, localChunkZ);
        region.chunkLastUpdatedMs[slot] = nowMs;
        return true;
    }

    private void rasterizeChunk(ClientLevel level, LevelChunk chunk, int chunkX, int chunkZ, RegionTexture region, int localChunkX, int localChunkZ) {
        boolean hasCeiling = level.dimensionType().hasCeiling();
        int minY = level.getMinY();
        int maxY = minY + level.getHeight() - 1;

        int chunkBlockX = chunkX * 16;
        int chunkBlockZ = chunkZ * 16;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int worldX = chunkBlockX + lx;
                int worldZ = chunkBlockZ + lz;

                int rawH = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                int startY = Math.clamp(rawH, minY, maxY);

                pos.set(worldX, startY, worldZ);
                BlockState state = chunk.getBlockState(pos);
                int surfaceY = startY;

                if (!hasCeiling) {
                    int steps = 0;
                    while (steps < 8 && surfaceY > minY && state.getMapColor(level, pos) == MapColor.NONE) {
                        surfaceY--;
                        steps++;
                        pos.set(worldX, surfaceY, worldZ);
                        state = chunk.getBlockState(pos);
                    }
                }

                MapColor mapColor = state.getMapColor(level, pos);
                int baseRgb = mapColor.col;
                if (mapColor == MapColor.NONE || baseRgb == 0) {
                    region.image.setPixel(localChunkX * 16 + lx, localChunkZ * 16 + lz, UNEXPLORED_ARGB);
                    continue;
                }

                int baseR = (baseRgb >>> 16) & 0xFF;
                int baseG = (baseRgb >>> 8) & 0xFF;
                int baseB = baseRgb & 0xFF;

                float shade;
                if (state.getFluidState().is(FluidTags.WATER)) {
                    int depth = 0;
                    int probeY = surfaceY - 1;
                    while (depth < 12 && probeY >= minY) {
                        pos.set(worldX, probeY, worldZ);
                        if (!chunk.getBlockState(pos).getFluidState().is(FluidTags.WATER)) {
                            break;
                        }
                        depth++;
                        probeY--;
                    }
                    // Smooth depth gradient for rivers and lakes
                    shade = Math.max(0.55f, 1.0f - (depth * 0.036f));
                } else {
                    int northY = getSurfaceHeightAt(level, chunk, chunkBlockX, chunkBlockZ, worldX, worldZ - 1, minY, maxY, surfaceY);
                    if (surfaceY > northY) {
                        shade = 1.0f;
                    } else if (surfaceY < northY) {
                        shade = 0.72f;
                    } else {
                        shade = 0.86f;
                    }
                }

                int r = Math.clamp(Math.round(baseR * shade), 0, 255);
                int g = Math.clamp(Math.round(baseG * shade), 0, 255);
                int b = Math.clamp(Math.round(baseB * shade), 0, 255);
                int argb = 0xFF000000 | (r << 16) | (g << 8) | b;

                region.image.setPixel(localChunkX * 16 + lx, localChunkZ * 16 + lz, argb);
            }
        }

        region.dirty = true;
    }

    private int getSurfaceHeightAt(
        ClientLevel level,
        LevelChunk currentChunk,
        int curChunkBlockX,
        int curChunkBlockZ,
        int worldX,
        int worldZ,
        int minY,
        int maxY,
        int fallbackY
    ) {
        int lx = worldX - curChunkBlockX;
        int lz = worldZ - curChunkBlockZ;
        LevelChunk targetChunk = currentChunk;

        if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) {
            int cx = Math.floorDiv(worldX, 16);
            int cz = Math.floorDiv(worldZ, 16);
            targetChunk = level.getChunkSource().getChunk(cx, cz, false);
            if (targetChunk == null || targetChunk.isEmpty()) {
                return fallbackY;
            }
            lx = Math.floorMod(worldX, 16);
            lz = Math.floorMod(worldZ, 16);
        }

        int h = targetChunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
        return Math.clamp(h, minY, maxY) - 1;
    }

    private RegionTexture getOrCreateRegion(RegionKey key) {
        RegionTexture existing = regionTextures.get(key);
        if (existing != null) {
            NativeImage pixels = existing.texture.getPixels();
            if (pixels != null && !pixels.isClosed()) {
                return existing;
            }
            regionTextures.remove(key);
        }

        try {
            NativeImage image = new NativeImage(REGION_BLOCKS, REGION_BLOCKS, false);
            for (int y = 0; y < REGION_BLOCKS; y++) {
                for (int x = 0; x < REGION_BLOCKS; x++) {
                    image.setPixel(x, y, UNEXPLORED_ARGB);
                }
            }
            String safePath = String.format("terrain_r_%s_%s", formatNeg(key.regionX()), formatNeg(key.regionZ()));
            Identifier id = Identifier.fromNamespaceAndPath("gm-addon", safePath);
            DynamicTexture dynamicTexture = new DynamicTexture(() -> safePath, image);
            Minecraft.getInstance().getTextureManager().register(id, dynamicTexture);

            RegionTexture entry = new RegionTexture(id, dynamicTexture, image);
            regionTextures.put(key, entry);
            return entry;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean drawRegion(GuiGraphicsExtractor graphics, int regionX, int regionZ, int x0, int y0, int width, int height) {
        RegionTexture entry = regionTextures.get(new RegionKey(regionX, regionZ));
        if (entry == null) {
            return false;
        }
        NativeImage pixels = entry.texture.getPixels();
        if (pixels == null || pixels.isClosed()) {
            return false;
        }
        try {
            graphics.blit(
                entry.id,
                x0, y0,
                x0 + width, y0 + height,
                0.0f, 1.0f,
                0.0f, 1.0f
            );
            return true;
        } catch (Exception e) {
            regionTextures.remove(new RegionKey(regionX, regionZ));
            return false;
        }
    }

    public void clear() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.isSameThread()) {
            mc.execute(this::clearOnRenderThread);
        } else {
            clearOnRenderThread();
        }
    }

    private void clearOnRenderThread() {
        synchronized (regionTextures) {
            for (RegionTexture entry : regionTextures.values()) {
                releaseEntry(entry);
            }
            regionTextures.clear();
        }
    }

    private static void releaseEntry(RegionTexture entry) {
        if (entry == null) {
            return;
        }
        try {
            Minecraft.getInstance().getTextureManager().release(entry.id);
            entry.texture.close();
        } catch (Exception ignored) {
        }
    }

    private static String formatNeg(int val) {
        return val < 0 ? "m" + Math.abs(val) : "p" + val;
    }
}

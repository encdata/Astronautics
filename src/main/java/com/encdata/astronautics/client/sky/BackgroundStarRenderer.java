package com.encdata.astronautics.client.sky;

import com.encdata.astronautics.Astronautics;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Set;
import static org.lwjgl.opengl.GL33C.*;

public final class BackgroundStarRenderer {
    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "starfield");
    private static final Set<String> WARNED = new HashSet<>();
    private static final Matrix4f VIEW = new Matrix4f(), INVERSE_VIEW = new Matrix4f(), INVERSE_PROJECTION = new Matrix4f();
    private static int vao, vbo, uploads, query, submitted, draws;
    private static int uploadOffset;
    private static FloatBuffer uploadBuffer;
    private static boolean bufferReady;
    private static boolean pendingQuery, failed, previousWorldPresent;
    private static long lastPass, worldStart, lastReport;
    private static double cpuMicros, gpuMicros;
    private static final boolean DEBUG = Boolean.getBoolean("astronautics.stars.debug");
    private static final int COUNT = Math.clamp(Integer.getInteger("astronautics.stars.count", 9000), 2000, 9000);

    private BackgroundStarRenderer() {}

    public static void warnOnce(String key, String message) {
        if (WARNED.add(key)) Astronautics.LOGGER.warn("[AERO-STARFIELD] {}", message);
    }

    public static void tick(boolean worldPresent, boolean expected) {
        BackgroundStarCatalog catalog = BackgroundStarCatalog.ready();
        if (!worldPresent) {
            if (query != 0) { glDeleteQueries(query); query = 0; pendingQuery = false; }
            worldStart = lastPass = 0;
            if (previousWorldPresent) failed = false;
            previousWorldPresent = false;
            WARNED.clear();
            initializeWhenReady(catalog);
            return;
        }
        previousWorldPresent = true;
        initializeWhenReady(catalog);
        long now = System.nanoTime();
        if (worldStart == 0) worldStart = now;
        if (DEBUG && expected && now - Math.max(worldStart, lastPass) > 10_000_000_000L)
            warnOnce("missingPass", "Expected sky pass has not been reached for 10 seconds");
        if (DEBUG && now - lastReport > 10_000_000_000L) {
            lastReport = now;
            Astronautics.LOGGER.info("[AERO-STARFIELD] {}", diagnostics());
        }
    }

    public static String diagnostics() {
        var catalog = BackgroundStarCatalog.ready();
        return "catalog=" + (catalog == null ? "loading" : BackgroundStarCatalog.COUNT)
                + " submitted=" + submitted
                + " bright=" + (catalog == null ? 0 : catalog.brightStars)
                + " galactic=" + (catalog == null ? 0 : catalog.galacticStars)
                + " pass=" + (lastPass != 0) + " buffer=" + bufferReady + " draws=" + draws
                + " uploads=" + uploads + " CPU_us=" + (int) cpuMicros + " GPU_us=" + (int) gpuMicros;
    }

    public static void render(Matrix4fc modelView, Matrix4fc projection, Matrix4fc skyRotation,
                              float atmosphere, float sunElevation, float time) {
        RenderSystem.assertOnRenderThread();
        long start = System.nanoTime();
        lastPass = start;
        submitted = draws = 0;
        if (failed) return;
        ShaderProgram shader = VeilRenderSystem.setShader(SHADER);
        if (shader == null) { warnOnce("shader", "Background star shader unavailable; no stars submitted"); return; }
        int oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int oldBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
        boolean depth = glIsEnabled(GL_DEPTH_TEST), cull = glIsEnabled(GL_CULL_FACE), blend = glIsEnabled(GL_BLEND);
        boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        int srcRGB = glGetInteger(GL_BLEND_SRC_RGB), dstRGB = glGetInteger(GL_BLEND_DST_RGB);
        int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        boolean measuring = false;
        try {
            if (!bufferReady) return;
            glBindVertexArray(vao);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE);

            VIEW.set(modelView).m30(0).m31(0).m32(0).mul(skyRotation);
            INVERSE_VIEW.set(VIEW).invert();
            INVERSE_PROJECTION.set(projection).invert();
            if (DEBUG) {
                if (query == 0) query = glGenQueries();
                if (pendingQuery && glGetQueryObjecti(query, GL_QUERY_RESULT_AVAILABLE) != 0) {
                    gpuMicros = glGetQueryObjectui64(query, GL_QUERY_RESULT) / 1000.0;
                    pendingQuery = false;
                }
                if (!pendingQuery && glGetQueryi(GL_TIME_ELAPSED, GL_CURRENT_QUERY) == 0) {
                    glBeginQuery(GL_TIME_ELAPSED, query);
                    measuring = true;
                }
            }
            if (GalacticBackgroundRenderer.render(INVERSE_PROJECTION, INVERSE_VIEW, skyRotation,
                    atmosphere, sunElevation, time)) draws++;
            shader.bind();
            shader.getUniformSafe("SkyView").setMatrix(VIEW);
            shader.getUniformSafe("Projection").setMatrix(projection);
            shader.getUniformSafe("SkyRotation").setMatrix(skyRotation);
            shader.getUniformSafe("Viewport").setVector((float) Minecraft.getInstance().getWindow().getWidth(),
                    (float) Minecraft.getInstance().getWindow().getHeight());
            shader.getUniformSafe("BrightGlow").setFloat(Boolean.getBoolean("astronautics.stars.noGlow") ? 0 : 1);
            AstronomicalVisibility.upload(shader, atmosphere, sunElevation, time);
            glDrawArraysInstanced(GL_TRIANGLES, 0, 6, COUNT);
            submitted = COUNT;
            draws++;
        } catch (RuntimeException exception) {
            failed = true;
            Astronautics.LOGGER.error("[AERO-STARFIELD] Buffer initialization/draw failed", exception);
        } finally {
            if (measuring) { glEndQuery(GL_TIME_ELAPSED); pendingQuery = true; }
            glBindVertexArray(oldVao);
            glBindBuffer(GL_ARRAY_BUFFER, oldBuffer);
            ShaderProgram.unbind();
            GlStateManager._glUseProgram(oldProgram);
            RenderSystem.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
            if (!blend) RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest();
            if (cull) RenderSystem.enableCull();
            RenderSystem.depthMask(depthMask);
            cpuMicros = cpuMicros * .95 + (System.nanoTime() - start) / 1000.0 * .05;
        }
    }

    private static void uploadNextChunk(BackgroundStarCatalog catalog) {
        if (catalog == null || bufferReady) return;
        int previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        try {
            if (vao == 0) {
                vao = glGenVertexArrays();
                vbo = glGenBuffers();
                uploadBuffer = MemoryUtil.memAllocFloat(BackgroundStarCatalog.STRIDE * 1000);
            }
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            if (uploadOffset == 0) {
                glBufferData(GL_ARRAY_BUFFER, (long) BackgroundStarCatalog.COUNT * BackgroundStarCatalog.STRIDE * Float.BYTES, GL_STATIC_DRAW);
            }
            int count = Math.min(1000, BackgroundStarCatalog.COUNT - uploadOffset);
            int floatCount = count * BackgroundStarCatalog.STRIDE;
            uploadBuffer.clear();
            uploadBuffer.put(catalog.uploadData(), uploadOffset * BackgroundStarCatalog.STRIDE, floatCount).flip();
            glBufferSubData(GL_ARRAY_BUFFER, (long) uploadOffset * BackgroundStarCatalog.STRIDE * Float.BYTES, uploadBuffer);
            uploadOffset += count;
            if (uploadOffset == BackgroundStarCatalog.COUNT) {
                glBindVertexArray(vao);
                for (int attribute = 0; attribute < 3; attribute++) {
                    glEnableVertexAttribArray(attribute);
                    glVertexAttribPointer(attribute, 3, GL_FLOAT, false, 36, attribute * 12L);
                    glVertexAttribDivisor(attribute, 1);
                }
                if (glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE) != BackgroundStarCatalog.COUNT * 36)
                    throw new IllegalStateException("Star buffer upload has incorrect size");
                MemoryUtil.memFree(uploadBuffer);
                uploadBuffer = null;
                bufferReady = true;
                uploads++;
            }
        } catch (RuntimeException exception) {
            if (vao != 0) glDeleteVertexArrays(vao);
            if (vbo != 0) glDeleteBuffers(vbo);
            if (uploadBuffer != null) MemoryUtil.memFree(uploadBuffer);
            uploadBuffer = null;
            vao = vbo = uploadOffset = 0;
            throw exception;
        } finally {
            glBindVertexArray(previousVao);
            glBindBuffer(GL_ARRAY_BUFFER, previousBuffer);
        }
    }

    private static void initializeWhenReady(BackgroundStarCatalog catalog) {
        if (catalog == null || bufferReady || failed) return;
        try {
            uploadNextChunk(catalog);
        } catch (RuntimeException exception) {
            failed = true;
            Astronautics.LOGGER.error("[AERO-STARFIELD] Buffer initialization failed", exception);
        }
    }
}

package com.luci.lumen.vk;

import com.luci.lumen.LumenInit;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChunkGeometryCapture {
    private static final ConcurrentLinkedQueue<CapturedSection> pendingSections = new ConcurrentLinkedQueue<>();
    private static boolean enabled = false;

    public static void setEnabled(boolean e) {
        enabled = e;
    }

    public static void capture(SectionCompiler.Results results, SectionPos sectionPos) {
        if (!enabled) return;
        if (results == null || results.renderedLayers == null) return;
        pendingSections.add(new CapturedSection(results, sectionPos));
    }

    public static boolean uploadScene() {
        if (!enabled || pendingSections.isEmpty()) return false;

        List<Integer> allVerts = new ArrayList<>();
        List<Integer> allIndices = new ArrayList<>();
        int vertexBase = 0;

        CapturedSection cs;
        while ((cs = pendingSections.poll()) != null) {
            for (var entry : cs.results.renderedLayers.entrySet()) {
                MeshData mesh = entry.getValue();
                if (mesh == null) continue;

                var drawState = mesh.drawState();
                int vertexCount = drawState.vertexCount();
                int indexCount = drawState.indexCount();
                if (vertexCount == 0 || indexCount == 0) continue;

                var format = drawState.format();
                int vertexSize = format.getVertexSize();
                int posOffset = format.getOffset(VertexFormatElement.POSITION);

                ByteBuffer vb = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                ByteBuffer ib = mesh.indexBuffer().order(ByteOrder.nativeOrder());

                float ox = (float) cs.sectionPos.minBlockX();
                float oy = (float) cs.sectionPos.minBlockY();
                float oz = (float) cs.sectionPos.minBlockZ();

                for (int vi = 0; vi < vertexCount; vi++) {
                    int pos = vi * vertexSize + posOffset;
                    float x = vb.getFloat(pos);
                    float y = vb.getFloat(pos + 4);
                    float z = vb.getFloat(pos + 8);
                    allVerts.add(Float.floatToIntBits(x + ox));
                    allVerts.add(Float.floatToIntBits(y + oy));
                    allVerts.add(Float.floatToIntBits(z + oz));
                }

                int ibBytes = ib.capacity();
                boolean isShort = ibBytes / indexCount <= 2;
                if (!isShort) {
                    IntBuffer intIb = ib.asIntBuffer();
                    for (int ii = 0; ii < indexCount; ii++) {
                        allIndices.add(intIb.get(ii) + vertexBase);
                    }
                } else {
                    ShortBuffer shortIb = ib.asShortBuffer();
                    for (int ii = 0; ii < indexCount; ii++) {
                        allIndices.add((shortIb.get(ii) & 0xFFFF) + vertexBase);
                    }
                }

                vertexBase += vertexCount;
            }
        }

        if (allVerts.isEmpty() || allIndices.isEmpty()) return false;

        float[] verts = new float[allVerts.size()];
        for (int i = 0; i < allVerts.size(); i++) {
            verts[i] = Float.intBitsToFloat(allVerts.get(i));
        }
        int[] idxs = new int[allIndices.size()];
        for (int i = 0; i < allIndices.size(); i++) {
            idxs[i] = allIndices.get(i);
        }

        int primitiveCount = idxs.length / 3;
        int vertexCount = verts.length / 3;

        LumenInit.LOGGER.info("[Lumen] Uploading scene: {} verts, {} tris", vertexCount, primitiveCount);

        return LumenNativeBridge.nativeUploadScene(verts, idxs, vertexCount, primitiveCount);
    }

    public static void clear() {
        pendingSections.clear();
    }

    private record CapturedSection(SectionCompiler.Results results, SectionPos sectionPos) {
    }
}

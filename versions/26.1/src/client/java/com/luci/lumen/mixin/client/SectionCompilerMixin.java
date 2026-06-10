package com.luci.lumen.mixin.client;

import com.luci.lumen.vk.ChunkGeometryCapture;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.SectionPos;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {

    @Inject(method = "compile", at = @At("RETURN"))
    private void onCompile(SectionPos sectionPos, RenderSectionRegion region, VertexSorting sorting, SectionBufferBuilderPack pack, CallbackInfoReturnable<SectionCompiler.Results> cir) {
        SectionCompiler.Results results = cir.getReturnValue();
        if (results != null && results.renderedLayers != null && !results.renderedLayers.isEmpty()) {
            ChunkGeometryCapture.capture(results, sectionPos);
        }
    }
}

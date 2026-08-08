package dev.hypershot.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("renderBlockOutline")
    boolean hypershot$getRenderBlockOutline();

    @Accessor("renderBlockOutline")
    void hypershot$setRenderBlockOutline(boolean value);

    @Accessor("mainRenderTarget")
    void hypershot$setMainRenderTarget(RenderTarget target);
}

package dev.hypershot.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("deltaTracker")
    DeltaTracker.Timer hypershot$getDeltaTracker();

    @Accessor("mainRenderTarget")
    @Mutable
    void hypershot$setMainRenderTarget(RenderTarget target);
}

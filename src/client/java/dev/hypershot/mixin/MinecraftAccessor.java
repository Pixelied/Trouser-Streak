package dev.hypershot.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("deltaTracker")
    DeltaTracker.Timer hypershot$getDeltaTracker();
}

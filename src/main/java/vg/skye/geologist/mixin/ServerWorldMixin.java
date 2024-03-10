package vg.skye.geologist.mixin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.RandomSequencesState;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vg.skye.geologist.Database;
import vg.skye.geologist.DatabaseHolder;

import java.util.List;
import java.util.concurrent.Executor;

@Mixin(ServerWorld.class)
public class ServerWorldMixin implements DatabaseHolder {
    @Unique
    private Database database;

    @Inject(method = "<init>", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/world/World;<init>(Lnet/minecraft/world/MutableWorldProperties;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/registry/DynamicRegistryManager;Lnet/minecraft/registry/entry/RegistryEntry;Ljava/util/function/Supplier;ZZJI)V"))
    private void setup(MinecraftServer server, Executor workerExecutor, LevelStorage.Session session, ServerWorldProperties properties, RegistryKey worldKey, DimensionOptions dimensionOptions, WorldGenerationProgressListener worldGenerationProgressListener, boolean debugWorld, long seed, List spawners, boolean shouldTickTime, RandomSequencesState randomSequencesState, CallbackInfo ci) {
        database = ((DatabaseHolder) session).geologist$getDatabase();
    }

    @NotNull
    @Override
    @Unique
    public Database geologist$getDatabase() {
        return database;
    }
}

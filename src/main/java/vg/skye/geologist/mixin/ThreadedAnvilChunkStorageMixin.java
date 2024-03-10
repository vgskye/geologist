package vg.skye.geologist.mixin;

import com.mojang.datafixers.DataFixer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.thread.ThreadExecutor;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkStatusChangeListener;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.poi.PointOfInterestStorage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vg.skye.geologist.Database;
import vg.skye.geologist.DatabaseHolder;
import vg.skye.geologist.NeedsConfiguration;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(ThreadedAnvilChunkStorage.class)
public class ThreadedAnvilChunkStorageMixin implements DatabaseHolder {
    @Shadow @Final private PointOfInterestStorage pointOfInterestStorage;
    @Shadow @Final
    ServerWorld world;
    @Unique
    private Database database;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void setup(ServerWorld world, LevelStorage.Session session, DataFixer dataFixer, StructureTemplateManager structureTemplateManager, Executor executor, ThreadExecutor mainThreadExecutor, ChunkProvider chunkProvider, ChunkGenerator chunkGenerator, WorldGenerationProgressListener worldGenerationProgressListener, ChunkStatusChangeListener chunkStatusChangeListener, Supplier persistentStateManagerFactory, int viewDistance, boolean dsync, CallbackInfo ci) {
        database = ((DatabaseHolder) session).geologist$getDatabase();
        byte[] poiNamespace = (this.world.getRegistryKey().getValue().toString() + "_poi").getBytes(StandardCharsets.UTF_8);
        ((NeedsConfiguration) this.pointOfInterestStorage).geologist$setDatabaseAndNamespace(
                database,
                poiNamespace
        );
        byte[] namespace = (this.world.getRegistryKey().getValue().toString() + "_chunks").getBytes(StandardCharsets.UTF_8);
        ((NeedsConfiguration) this).geologist$setDatabaseAndNamespace(
                database,
                namespace
        );
    }

    @NotNull
    @Override
    @Unique
    public Database geologist$getDatabase() {
        return database;
    }
}

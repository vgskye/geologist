package vg.skye.geologist.mixin;

import com.mojang.datafixers.DataFixer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.chunk.ChunkStatusChangeListener;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vg.skye.geologist.DatabaseHolder;
import vg.skye.geologist.NeedsConfiguration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(ServerChunkManager.class)
public class ServerChunkManagerMixin {
    @Shadow @Final private PersistentStateManager persistentStateManager;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/io/File;mkdirs()Z"))
    private boolean noMkdir(File instance) {
        return true;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void provideConfig(
            ServerWorld world,
            LevelStorage.Session session,
            DataFixer dataFixer,
            StructureTemplateManager structureTemplateManager,
            Executor workerExecutor,
            ChunkGenerator chunkGenerator,
            int viewDistance,
            int simulationDistance,
            boolean dsync,
            WorldGenerationProgressListener worldGenerationProgressListener,
            ChunkStatusChangeListener chunkStatusChangeListener,
            Supplier<PersistentStateManager> persistentStateManagerFactory,
            CallbackInfo ci
    ) {
        ((NeedsConfiguration) this.persistentStateManager).geologist$setDatabaseAndNamespace(
                ((DatabaseHolder) session).geologist$getDatabase(),
                (world.getDimensionKey().getValue().toString() + "_data").getBytes(StandardCharsets.UTF_8)
        );
    }
}

package vg.skye.geologist.mixin;

import com.google.common.primitives.Longs;
import com.mojang.datafixers.DataFixer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.scanner.NbtScanQuery;
import net.minecraft.nbt.scanner.NbtScanner;
import net.minecraft.nbt.scanner.SelectiveNbtCollector;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.Util;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.thread.ThreadExecutor;
import net.minecraft.world.FeatureUpdater;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkStatusChangeListener;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.storage.NbtScannable;
import net.minecraft.world.storage.StorageIoWorker;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vg.skye.geologist.Database;
import vg.skye.geologist.DatabaseHolder;
import vg.skye.geologist.DatabaseKey;
import vg.skye.geologist.NeedsConfiguration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("OverwriteAuthorRequired")
@Mixin(VersionedChunkStorage.class)
public class VersionedChunkStorageMixin implements NbtScannable, NeedsConfiguration {
    @Mutable
    @Shadow
    @Final
    private StorageIoWorker worker;
    @Unique
    private Database database;
    @Unique
    private byte[] namespace;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void getRocks(Path directory, DataFixer dataFixer, boolean dsync, CallbackInfo ci) {
        try {
            this.worker.close();
        } catch (IOException ignored) {}
        this.worker = null;
    }

    @Overwrite
    public CompletableFuture<Optional<NbtCompound>> getNbt(ChunkPos chunkPos) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(
                        database.readNbt(new DatabaseKey(
                                namespace,
                                Longs.toByteArray(chunkPos.toLong())
                        ))
                )
        );
    }

    @Overwrite
    public void setNbt(ChunkPos chunkPos, NbtCompound nbt) {
        database.writeNbt(new DatabaseKey(
                namespace,
                Longs.toByteArray(chunkPos.toLong())
        ), nbt);
    }

    @Overwrite
    public boolean needsBlending(ChunkPos chunkPos, int checkRadius) {
        // TODO: actually compute this
        return false;
    }

    @Overwrite
    public void completeAll() {
        database.flush();
    }

    @Overwrite
    public void close() {
        database.flush();
    }

    @Overwrite
    public NbtScannable getWorker() {
        return this;
    }

    @Intrinsic
    @Override
    public CompletableFuture<Void> scanChunk(ChunkPos pos, NbtScanner scanner) {
        NbtCompound tag = database.readNbt(new DatabaseKey(
                namespace,
                Longs.toByteArray(pos.toLong())
        ));
        if (tag != null) {
            tag.accept(scanner);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Unique
    @Override
    public void geologist$setDatabaseAndNamespace(
            @NotNull Database database,
            byte @NotNull [] namespace
    ) {
        this.database = database;
        this.namespace = namespace;
    }
}

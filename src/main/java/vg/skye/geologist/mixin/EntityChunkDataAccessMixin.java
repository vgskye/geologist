package vg.skye.geologist.mixin;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Longs;
import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.thread.TaskExecutor;
import net.minecraft.world.storage.ChunkDataList;
import net.minecraft.world.storage.EntityChunkDataAccess;
import net.minecraft.world.storage.StorageIoWorker;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vg.skye.geologist.Database;
import vg.skye.geologist.DatabaseHolder;
import vg.skye.geologist.DatabaseKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@SuppressWarnings("OverwriteAuthorRequired")
@Mixin(EntityChunkDataAccess.class)
public abstract class EntityChunkDataAccessMixin {
    @Unique
    private Database database;
    @Unique
    private byte[] namespace;
    @Shadow @Final private LongSet emptyChunks;

    @Shadow
    private static ChunkDataList<Entity> emptyDataList(ChunkPos pos) {
        throw new AssertionError();
    }

    @Shadow
    private static ChunkPos getChunkPos(NbtCompound chunkNbt) {
        throw new AssertionError();
    }

    @Shadow protected abstract NbtCompound fixChunkData(NbtCompound chunkNbt);

    @Shadow @Final private static Logger LOGGER;

    @Shadow @Final private ServerWorld world;

    @Shadow
    private static void putChunkPos(NbtCompound chunkNbt, ChunkPos pos) {
        throw new AssertionError();
    }

    @Mutable
    @Shadow
    @Final
    private StorageIoWorker dataLoadWorker;

    @Mutable
    @Shadow
    @Final
    private TaskExecutor<Runnable> taskExecutor;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void getRocks(ServerWorld world, Path path, DataFixer dataFixer, boolean dsync, Executor executor, CallbackInfo ci) {
        database = ((DatabaseHolder) world).geologist$getDatabase();
        namespace = (world.getRegistryKey().getValue().toString() + "_entities").getBytes(StandardCharsets.UTF_8);
        try {
            this.dataLoadWorker.close();
            this.taskExecutor.close();
        } catch (IOException ignored) {}
        this.dataLoadWorker = null;
        this.taskExecutor = null;
    }

    @Overwrite
    public CompletableFuture<ChunkDataList<Entity>> readChunkData(ChunkPos pos) {
        if (this.emptyChunks.contains(pos.toLong())) {
            return CompletableFuture.completedFuture(emptyDataList(pos));
        }
        NbtCompound nbt = database.readNbt(new DatabaseKey(
                namespace,
                Longs.toByteArray(pos.toLong())
        ));
        if (nbt == null) {
            this.emptyChunks.add(pos.toLong());
            return CompletableFuture.completedFuture(emptyDataList(pos));
        }
        try {
            ChunkPos realPos = getChunkPos(nbt);
            if (!pos.equals(realPos)) {
                LOGGER.error("Chunk file at {} is in the wrong location. (Expected {}, got {})", pos, pos, realPos);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse chunk {} position info", pos, e);
        }

        NbtCompound nbtCompound = this.fixChunkData(nbt);
        NbtList nbtList = nbtCompound.getList("Entities", NbtElement.COMPOUND_TYPE);
        List<Entity> list = EntityType.streamFromNbt(nbtList, this.world).toList();
        return CompletableFuture.completedFuture(new ChunkDataList<>(pos, list));
    }

    @Overwrite
    public void writeChunkData(ChunkDataList<Entity> dataList) {
        ChunkPos chunkPos = dataList.getChunkPos();
        if (dataList.isEmpty()) {
            if (this.emptyChunks.add(chunkPos.toLong())) {
                database.remove(new DatabaseKey(
                        namespace,
                        Longs.toByteArray(chunkPos.toLong())
                ));
            }
        } else {
            NbtList nbtList = new NbtList();
            dataList.stream().forEach(entity -> {
                NbtCompound nbtCompoundxx = new NbtCompound();
                if (entity.saveNbt(nbtCompoundxx)) {
                    nbtList.add(nbtCompoundxx);
                }
            });
            NbtCompound nbtCompound = NbtHelper.putDataVersion(new NbtCompound());
            nbtCompound.put("Entities", nbtList);
            putChunkPos(nbtCompound, chunkPos);
            database.writeNbt(new DatabaseKey(
                    namespace,
                    Longs.toByteArray(chunkPos.toLong())
            ), nbtCompound);
            this.emptyChunks.remove(chunkPos.toLong());
        }
    }

    @Overwrite
    public void awaitAll(boolean sync) {
        if (sync) {
            database.flush();
        }
    }

    @Overwrite
    public void close() {
        database.flush();
    }
}

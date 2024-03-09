package vg.skye.geologist.mixin;

import com.google.common.primitives.Longs;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryOps;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.storage.SerializingRegionBasedStorage;
import net.minecraft.world.storage.StorageIoWorker;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vg.skye.geologist.Database;
import vg.skye.geologist.DatabaseKey;
import vg.skye.geologist.NeedsConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@SuppressWarnings("OverwriteAuthorRequired")
@Mixin(SerializingRegionBasedStorage.class)
public abstract class SerializingRegionBasedStorageMixin implements NeedsConfiguration {
    @Mutable
    @Shadow
    @Final
    private StorageIoWorker worker;
    @Shadow @Final private static Logger LOGGER;
    @Shadow @Final private DynamicRegistryManager dynamicRegistryManager;

    @Shadow protected abstract <T> Dynamic<T> serialize(ChunkPos chunkPos, DynamicOps<T> ops);

    @Unique
    private Database database;
    @Unique
    private byte[] namespace;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void closeWorker(
            Path path,
            Function<Runnable, Codec<?>> codecFactory,
            Function<Runnable, ?> factory,
            DataFixer dataFixer,
            DataFixTypes dataFixTypes,
            boolean dsync,
            DynamicRegistryManager dynamicRegistryManager,
            HeightLimitView world,
            CallbackInfo ci) {
        try {
            this.worker.close();
        } catch (IOException ignored) {}
        this.worker = null;
    }

    @Overwrite
    private CompletableFuture<Optional<NbtCompound>> loadNbt(ChunkPos pos) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(
                        database.readNbt(new DatabaseKey(
                                namespace,
                                Longs.toByteArray(pos.toLong())
                        ))
                )
        );
    }

    @Overwrite
    private void save(ChunkPos pos) {
        RegistryOps<NbtElement> registryOps = RegistryOps.of(NbtOps.INSTANCE, this.dynamicRegistryManager);
        Dynamic<NbtElement> dynamic = this.serialize(pos, registryOps);
        NbtElement nbtElement = dynamic.getValue();
        if (nbtElement instanceof NbtCompound) {
            database.writeNbt(new DatabaseKey(
                    namespace,
                    Longs.toByteArray(pos.toLong())
            ), (NbtCompound)nbtElement);
        } else {
            LOGGER.error("Expected compound tag, got {}", nbtElement);
        }
    }

    @Overwrite
    public void close() {
        database.flush();
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

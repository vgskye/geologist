package vg.skye.geologist.mixin;

import com.mojang.datafixers.DataFixer;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.util.Uuids;
import net.minecraft.world.WorldSaveHandler;
import net.minecraft.world.level.storage.LevelStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vg.skye.geologist.Database;
import vg.skye.geologist.DatabaseHolder;
import vg.skye.geologist.DatabaseKey;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@SuppressWarnings("OverwriteAuthorRequired")
@Mixin(WorldSaveHandler.class)
public class WorldSaveHandlerMixin implements DatabaseHolder {

    @Shadow @Final private static Logger LOGGER;
    @Shadow @Final protected DataFixer dataFixer;
    @Unique
    private Database database;
    @Unique
    private static final byte[] NAMESPACE = "playerdata".getBytes(StandardCharsets.UTF_8);

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/io/File;mkdirs()Z"))
    private boolean noMkdir(File instance) {
        return true;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void getRocks(LevelStorage.Session session, DataFixer dataFixer, CallbackInfo ci) {
        database = ((DatabaseHolder) session).geologist$getDatabase();
    }

    @Overwrite
    public void savePlayerData(PlayerEntity player) {
        try {
            NbtCompound nbtCompound = player.writeNbt(new NbtCompound());
            database.writeNbt(new DatabaseKey(
                    NAMESPACE,
                    Uuids.toByteArray(player.getUuid())
            ), nbtCompound);
        } catch (Exception e) {
            LOGGER.warn("Failed to save player data for {}", player.getName().getString());
        }
    }

    @Overwrite
    public @Nullable NbtCompound loadPlayerData(PlayerEntity player) {
        NbtCompound nbtCompound = database.readNbt(new DatabaseKey(
                NAMESPACE,
                Uuids.toByteArray(player.getUuid())
        ));

        if (nbtCompound != null) {
            int i = NbtHelper.getDataVersion(nbtCompound, -1);
            player.readNbt(DataFixTypes.PLAYER.update(this.dataFixer, nbtCompound, i));
        }

        return nbtCompound;
    }

    @Overwrite
    public String[] getSavedPlayerIds() {
        return database
                .listKeys(NAMESPACE)
                .stream()
                .map(WorldSaveHandlerMixin::fromBytes)
                .map(UUID::toString)
                .toArray(String[]::new);
    }

    @Unique
    private static UUID fromBytes(byte[] data) {
        long msb = 0;
        long lsb = 0;
        assert data.length == 16 : "data must be 16 bytes in length";
        for (int i=0; i<8; i++)
            msb = (msb << 8) | (data[i] & 0xff);
        for (int i=8; i<16; i++)
            lsb = (lsb << 8) | (data[i] & 0xff);
        return new UUID(msb, lsb);
    }

    @NotNull
    @Override
    @Unique
    public Database geologist$getDatabase() {
        return database;
    }
}

package vg.skye.geologist.mixin;

import com.mojang.datafixers.DataFixer;
import net.minecraft.SharedConstants;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import vg.skye.geologist.Database;
import vg.skye.geologist.DatabaseKey;
import vg.skye.geologist.NeedsConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

@SuppressWarnings("OverwriteAuthorRequired")
@Mixin(PersistentStateManager.class)
public class PersistentStateManagerMixin implements NeedsConfiguration {
    @Shadow @Final private static Logger LOGGER;
    @Shadow @Final private DataFixer dataFixer;
    @Shadow @Final private Map<String, PersistentState> loadedStates;
    @Unique
    private Database database;
    @Unique
    private byte[] namespace;

    @Overwrite
    @Nullable
    private <T extends PersistentState> T readFromFile(Function<NbtCompound, T> readFunction, String id) {
        try {
            NbtCompound nbtCompound = this.readNbt(id, SharedConstants.getGameVersion().getSaveVersion().getId());
            return readFunction.apply(nbtCompound.getCompound("data"));
        } catch (FileNotFoundException ignored) {
        } catch (Exception e) {
            LOGGER.error("Error loading saved data: {}", id, e);
        }

        return null;
    }

    @Overwrite
    public NbtCompound readNbt(String id, int dataVersion) throws IOException {
        NbtCompound nbtCompound = database.readNbt(new DatabaseKey(
                namespace,
                id.getBytes(StandardCharsets.UTF_8)
        ));
        if (nbtCompound == null) {
            throw new FileNotFoundException();
        }
        int i = NbtHelper.getDataVersion(nbtCompound, 1343);
        return DataFixTypes.SAVED_DATA.update(this.dataFixer, nbtCompound, i, dataVersion);
    }

    @Overwrite
    public void save() {
        this.loadedStates.forEach((id, state) -> {
            if (state != null && state.isDirty()) {
                NbtCompound nbtCompound = new NbtCompound();
                nbtCompound.put("data", state.writeNbt(new NbtCompound()));
                NbtHelper.putDataVersion(nbtCompound);

                database.writeNbt(new DatabaseKey(
                        namespace,
                        id.getBytes(StandardCharsets.UTF_8)
                ), nbtCompound);

                state.setDirty(false);
            }
        });
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

package vg.skye.geologist.mixin;

import net.minecraft.world.level.storage.LevelStorage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vg.skye.geologist.Database;
import vg.skye.geologist.DatabaseHolder;

import java.nio.file.Path;

@Mixin(LevelStorage.Session.class)
public class LevelStorageSessionMixin implements DatabaseHolder {
    @Unique
    private Database database;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void openDatabase(LevelStorage levelStorage, String directoryName, Path path, CallbackInfo ci) {
        database = new Database(path);
    }

    @NotNull
    @Override
    public Database geologist$getDatabase() {
        return database;
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void closeDatabase(CallbackInfo ci) {
        database.close();
    }
}

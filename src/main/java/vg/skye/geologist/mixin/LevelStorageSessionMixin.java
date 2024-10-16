package vg.skye.geologist.mixin;

import net.minecraft.world.level.storage.LevelStorage;
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

import java.nio.file.Path;

@Mixin(LevelStorage.Session.class)
public class LevelStorageSessionMixin implements DatabaseHolder {
    @Shadow
    @Final
    LevelStorage.LevelSave directory;
    @Unique
    private Database database;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void openDatabase(LevelStorage levelStorage, String directoryName, Path path, CallbackInfo ci) {
        database = new Database(path.resolve("geologist-db"));
    }

    @Inject(method = "deleteSessionLock", at = @At("HEAD"))
    private void deleteDatabase(CallbackInfo ci) {
        database.close();
        database = null;
        Database.deleteDatabase(this.directory.path());
    }

    @NotNull
    @Override
    @Unique
    public Database geologist$getDatabase() {
        return database;
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void closeDatabase(CallbackInfo ci) {
        if (database != null) {
            database.close();
        }
    }
}

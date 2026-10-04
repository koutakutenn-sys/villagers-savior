package dev.villagerssavior.mixin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(PersistentEntitySectionManager.class)
public interface InspectionEntityManagerAccess {
    @Accessor("permanentStorage") EntityPersistentStorage<Entity> savior$storage();
    @Accessor("sectionStorage") EntitySectionStorage<Entity> savior$sections();
}

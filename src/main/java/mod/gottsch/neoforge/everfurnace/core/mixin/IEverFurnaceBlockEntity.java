package mod.gottsch.neoforge.everfurnace.core.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Created by Mark Gottschling on 12/13/2024
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface IEverFurnaceBlockEntity {

    @Accessor
    int getLitTime();

    @Accessor
    int getLitDuration();

    @Accessor
    int getCookingProgress();

    @Accessor
    int getCookingTotalTime();

    @Invoker
    boolean callIsLit();

}

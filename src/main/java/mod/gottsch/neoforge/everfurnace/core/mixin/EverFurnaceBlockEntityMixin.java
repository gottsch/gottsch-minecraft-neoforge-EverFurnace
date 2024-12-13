package mod.gottsch.neoforge.everfurnace.core.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Created by Mark Gottschling on 12/13/2024
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class EverFurnaceBlockEntityMixin  extends BaseContainerBlockEntity implements WorldlyContainer, RecipeCraftingHolder, StackedContentsCompatible {

    @Unique
    private static final int INPUT_SLOT = 0;
    @Unique
    private static final int FUEL_SLOT = 1;
    @Unique
    private static final int OUTPUT_SLOT = 2;
    @Unique
    private static final String LAST_GAME_TIME_TAG = "everfurnace_lastGameTime";

    @Unique
    private long everfurnace$lastGameTime;

    protected EverFurnaceBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void onSave(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        tag.putLong(LAST_GAME_TIME_TAG, this.everfurnace$lastGameTime);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void onLoad(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.everfurnace$lastGameTime = tag.getLong(LAST_GAME_TIME_TAG);
    }

    @Inject(method = "serverTick", at = @At("HEAD")) // target more specifically somewhere closer to the actual calculations?
    private static void onTick(Level world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {
        // cast block entity as a mixin block entity
        EverFurnaceBlockEntityMixin blockEntityMixin = (EverFurnaceBlockEntityMixin) (Object) blockEntity;
        IEverFurnaceBlockEntity everFurnaceBlockEntity = (IEverFurnaceBlockEntity) ((Object) blockEntity);

        // record last world time
        long localLastGameTime = blockEntityMixin.getEverfurnace$lastGameTime();
        blockEntityMixin.setEverfurnace$lastGameTime(world.getGameTime());
        if (!everFurnaceBlockEntity.callIsLit()) {
            return;
        }

        // calculate the difference between game time and the lastGameTime
        long deltaTime = blockEntity.getLevel().getGameTime() - localLastGameTime;

        // exit if not enough time has passed
        if (deltaTime < 20) {
            return;
        }
    }

    @Unique
    public long getEverfurnace$lastGameTime() {
        return everfurnace$lastGameTime;
    }

    @Unique
    public void setEverfurnace$lastGameTime(long everfurnace$lastGameTime) {
        this.everfurnace$lastGameTime = everfurnace$lastGameTime;
    }
}

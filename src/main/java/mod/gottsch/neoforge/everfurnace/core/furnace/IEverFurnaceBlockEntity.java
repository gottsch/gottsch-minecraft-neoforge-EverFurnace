package mod.gottsch.neoforge.everfurnace.core.furnace;

/**
 * Accessor interface for mixin instance fields on {@code EverFurnaceBlockEntityMixin}.
 *
 * <p>All external access to mixin instance fields (from event handlers or other
 * non-mixin classes) must go through this interface. Direct casting to the mixin
 * class itself is not permitted by Mixin's verifier.
 *
 * <p>Usage pattern:
 * <pre>{@code
 * IEverFurnaceBlockEntity mixin =
 *         (IEverFurnaceBlockEntity)(Object) furnaceBlockEntity;
 * int pending = mixin.everfurnace$getPendingNotification();
 * }</pre>
 *
 * <p>{@code EverFurnaceBlockEntityMixin} declares
 * {@code implements IEverFurnaceBlockEntity} and provides the concrete
 * accessor implementations annotated {@code @Unique}.
 *
 * @author by Mark Gottschling on 3/31/2026
 */
public interface IEverFurnaceBlockEntity {

    long everfurnace$getLastGameTime();
    void everfurnace$setLastGameTime(long gameTime);

    int  everfurnace$getPendingNotification();
    void everfurnace$setPendingNotification(int count);

    long everfurnace$getLastNotificationTime();
    void everfurnace$setLastNotificationTime(long gameTime);

    float everfurnace$getPendingXp();
    void  everfurnace$setPendingXp(float xp);
}

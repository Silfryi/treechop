package ht.treechop.common;

import ht.treechop.TreeChopMod;
import ht.treechop.common.capabilities.ChopSettingsCapability;
import ht.treechop.common.capabilities.ChopSettingsProvider;
import ht.treechop.common.config.ConfigHandler;
import ht.treechop.common.event.ChopEvent;
import ht.treechop.common.network.PacketHandler;
import ht.treechop.common.util.ChopResult;
import ht.treechop.common.util.ChopUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tags.TagCollection;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import static ht.treechop.common.util.ChopUtil.isBlockALog;
import static net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = TreeChopMod.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public class Common {

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        ConfigHandler.onReload();
        ChopSettingsCapability.register();
        PacketHandler.init();
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        TagCollection<Block> blockTags = event.getTagManager().getBlocks();
        ConfigHandler.updateTags(blockTags);
    }

    @SubscribeEvent
    public static void onBreakEvent(BlockEvent.BreakEvent event) {
        PlayerEntity agent = event.getPlayer();
        ItemStack tool = agent.getHeldItemMainhand();
        BlockState blockState = event.getState();
        BlockPos pos = event.getPos();

        // Reuse some permission logic from PlayerInteractionManager.tryHarvestBlock
        if (
                !isBlockALog(blockState)
                        || !ConfigHandler.COMMON.enabled.get()
                        || !ChopUtil.canChopWithTool(tool)
                        || !ChopUtil.playerWantsToChop(agent)
                        || event.isCanceled()
                        || !(event.getWorld() instanceof World)
        ) {
            return;
        }

        World world = (World) event.getWorld();
        boolean canceled = MinecraftForge.EVENT_BUS.post(new ChopEvent.StartChopEvent(event, world, agent, pos, blockState));
        if (canceled) {
            return;
        }

        ChopResult chopResult = ChopUtil.getChopResult(
                world,
                pos,
                agent,
                ChopUtil.getNumChopsByTool(tool),
                ChopUtil.playerWantsToFell(agent),
                logPos -> isBlockALog(world, logPos)
        );

        if (chopResult != ChopResult.IGNORED) {
            if (chopResult.apply(pos, agent, tool, ConfigHandler.COMMON.breakLeaves.get())) {
                event.setCanceled(true);

                if (!agent.isCreative()) {
                    ChopUtil.doItemDamage(tool, world, blockState, pos, agent);
                }
            }

            MinecraftForge.EVENT_BUS.post(new ChopEvent.FinishChopEvent(world, agent, pos, blockState));
        }
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        final ResourceLocation loc = new ResourceLocation(TreeChopMod.MOD_ID + "chop_settings_capability");

        Entity entity = event.getObject();
        if (entity instanceof PlayerEntity) {
            event.addCapability(loc, new ChopSettingsProvider());
        }
    }

}

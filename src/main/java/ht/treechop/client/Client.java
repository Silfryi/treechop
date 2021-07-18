package ht.treechop.client;

import ht.treechop.TreeChopMod;
import ht.treechop.client.gui.screen.ChopIndicator;
import ht.treechop.client.gui.screen.ClientSettingsScreen;
import ht.treechop.client.settings.ClientChopSettings;
import ht.treechop.common.Common;
import ht.treechop.common.config.ConfigHandler;
import ht.treechop.common.network.ClientRequestSettingsPacket;
import ht.treechop.common.network.PacketHandler;
import ht.treechop.common.settings.Permissions;
import ht.treechop.common.settings.SettingsField;
import ht.treechop.common.settings.SneakBehavior;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class Client extends Common {

    private static final ClientChopSettings chopSettings = new ClientChopSettings();
    private static final ChopIndicator chopIndicator = new ChopIndicator();
    private static final Permissions serverPermissions = new Permissions();

    private static boolean pendingSync = false;

    public static void setChoppingIndicatorVisibility(boolean showChoppingIndicator) {
        ConfigHandler.CLIENT.showChoppingIndicators.set(showChoppingIndicator);
    }

    public static boolean isChoppingIndicatorEnabled() {
        return ConfigHandler.CLIENT.showChoppingIndicators.get();
    }

    /**
     * This is too early to send packets; as a hacky workaround, let's delay syncing until the next EntityJoinWorldEvent
     */
    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        pendingSync = true;
    }

    @SubscribeEvent
    public void onConnectDelayed(EntityJoinWorldEvent event) {
        if (pendingSync && event.getEntity() == Minecraft.getMinecraft().player) {
            updateChopSettings();
            pendingSync = false;
        }
    }

    public static void updateChopSettings() {
        TreeChopMod.LOGGER.info("Sending chop settings sync request");
        chopSettings.copyFrom(ConfigHandler.CLIENT.getChopSettings());
        PacketHandler.sendToServer(new ClientRequestSettingsPacket(chopSettings));
    }

    @Override
    public void preInit() {
        super.preInit();
        KeyBindings.init();
    }

    @SubscribeEvent
    public void renderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            chopIndicator.render(
                    event.getPartialTicks(),
                    event.getResolution()
            );
        }
    }

    public static void toggleSettingsOverlay() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (ConfigHandler.COMMON.clientGUIEnabled.get()) {
            if (minecraft.currentScreen instanceof ClientSettingsScreen) {
                ClientSettingsScreen.closeScreen();
            } else {
                minecraft.displayGuiScreen(new ClientSettingsScreen());
            }
        }
    }

    public static void requestSetting(SettingsField field, Object value) {
        PacketHandler.sendToServer(new ClientRequestSettingsPacket(field, value));
    }

    public static void toggleChopping() {
        boolean newValue = !chopSettings.get(SettingsField.CHOPPING, Boolean.class);
        chopSettings.set(SettingsField.CHOPPING, newValue);
    }

    public static void toggleFelling() {
        boolean newValue = !chopSettings.get(SettingsField.FELLING, Boolean.class);
        chopSettings.set(SettingsField.FELLING, newValue);
    }

    public static void cycleSneakBehavior() {
        SneakBehavior newValue = chopSettings.getSneakBehavior().cycle();
        chopSettings.set(SettingsField.SNEAK_BEHAVIOR, newValue);
    }

    public static ClientChopSettings getChopSettings() {
        return chopSettings;
    }

    public static void updatePermissions(Permissions permissions) {
        serverPermissions.copy(permissions);
    }

    public static Permissions getServerPermissions() {
        return serverPermissions;
    }

}

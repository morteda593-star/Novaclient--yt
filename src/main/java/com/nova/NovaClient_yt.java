package com.nova;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

@Mod(modid = "novaclient", name = "Nova Client", version = "1.0", clientSideOnly = true)
public class NovaClient {

    static final Minecraft mc = Minecraft.getMinecraft();
    static final Map<String, Boolean> mods = new LinkedHashMap<String, Boolean>();
    static File cfg;

    KeyBinding menuKey, zoomKey;
    boolean openMenu, lastL, lastR, boostApplied, fbApplied;
    float oldGamma, oldFov = -1;
    int sAO, sPart, sRD;
    boolean sFancy;
    final List<Long> left = new ArrayList<Long>();
    final List<Long> right = new ArrayList<Long>();

    static void add(String n, boolean d) { mods.put(n, d); }
    static boolean on(String n) { Boolean b = mods.get(n); return b != null && b; }

    static void load() {
        try {
            FileInputStream in = new FileInputStream(cfg);
            Properties p = new Properties();
            p.load(in);
            in.close();
            for (String k : mods.keySet()) {
                if (p.containsKey(k)) mods.put(k, Boolean.parseBoolean(p.getProperty(k)));
            }
        } catch (Exception ignored) {}
    }

    static void save() {
        try {
            Properties p = new Properties();
            for (Map.Entry<String, Boolean> en : mods.entrySet()) p.setProperty(en.getKey(), String.valueOf(en.getValue()));
            FileOutputStream out = new FileOutputStream(cfg);
            p.store(out, "Nova Client");
            out.close();
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void init(FMLInitializationEvent e) {
        add("FPS", true); add("CPS", true); add("Ping", true); add("Coords", true);
        add("Direction", true); add("Clock", false); add("Keystrokes", true);
        add("Armor", true); add("Potions", true);
        add("Auto Sprint", false); add("Fullbright", false); add("Zoom", true);
        add("FPS Boost", false); add("Bridge Assist", false);
        cfg = new File(mc.mcDataDir, "novaclient.properties");
        load();

        menuKey = new KeyBinding("Open Menu", Keyboard.KEY_RSHIFT, "Nova Client");
        zoomKey = new KeyBinding("Zoom", Keyboard.KEY_C, "Nova Client");
        ClientRegistry.registerKeyBinding(menuKey);
        ClientRegistry.registerKeyBinding(zoomKey);

        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            public String getCommandName() { return "nova"; }
            public String getCommandUsage(ICommandSender s) { return "/nova"; }
            public int getRequiredPermissionLevel() { return 0; }
            public boolean canCommandSenderUseCommand(ICommandSender s) { return true; }
            public void processCommand(ICommandSender s, String[] a) { openMenu = true; }
        });
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || mc.thePlayer == null || mc.theWorld == null) return;

        if (mc.currentScreen == null && (openMenu || menuKey.isPressed())) {
            openMenu = false;
            mc.displayGuiScreen(new MenuGui());
        }

        // Fullbright
        if (on("Fullbright")) {
            if (!fbApplied) { oldGamma = mc.gameSettings.gammaSetting; fbApplied = true; }
            mc.gameSettings.gammaSetting = 100f;
        } else if (fbApplied) {
            mc.gameSettings.gammaSetting = oldGamma;
            fbApplied = false;
        }

        // Zoom
        if (on("Zoom") && mc.currentScreen == null && Keyboard.isKeyDown(zoomKey.getKeyCode())) {
            if (oldFov < 0) oldFov = mc.gameSettings.fovSetting;
            mc.gameSettings.fovSetting = 20f;
        } else if (oldFov >= 0) {
            mc.gameSettings.fovSetting = oldFov;
            oldFov = -1;
        }

        // FPS Boost
        boolean boost = on("FPS Boost");
        if (boost && !boostApplied) {
            sFancy = mc.gameSettings.fancyGraphics;
            sAO = mc.gameSettings.ambientOcclusion;
            sPart = mc.gameSettings.particleSetting;
            sRD = mc.gameSettings.renderDistanceChunks;
            mc.gameSettings.fancyGraphics = false;
            mc.gameSettings.ambientOcclusion = 0;
            mc.gameSettings.particleSetting = 2;
            mc.gameSettings.renderDistanceChunks = Math.min(sRD, 6);
            boostApplied = true;
            mc.renderGlobal.loadRenderers();
        } else if (!boost && boostApplied) {
            mc.gameSettings.fancyGraphics = sFancy;
            mc.gameSettings.ambientOcclusion = sAO;
            mc.gameSettings.particleSetting = sPart;
            mc.gameSettings.renderDistanceChunks = sRD;
            boostApplied = false;
            mc.renderGlobal.loadRenderers();
        }

        // Auto Sprint
        if (on("Auto Sprint") && mc.currentScreen == null
                && mc.gameSettings.keyBindForward.isKeyDown()
                && !mc.thePlayer.isSneaking() && !mc.thePlayer.isUsingItem()
                && mc.thePlayer.getFoodStats().getFoodLevel() > 6) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }

        // Bridge Assist
        if (on("Bridge Assist")) bridge();
    }

    private void bridge() {
        if (mc.currentScreen != null || !mc.gameSettings.keyBindUseItem.isKeyDown()) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) return;
        try {
            ReflectionHelper.setPrivateValue(Minecraft.class, mc, 0, "rightClickDelayTimer", "field_71467_ac");
        } catch (Exception ignored) {}

        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop != null && mop.typeOfHit != MovingObjectPosition.MovingObjectType.MISS) return;

        BlockPos feet = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.5, mc.thePlayer.posZ);
        EnumFacing look = mc.thePlayer.getHorizontalFacing();
        if (mc.theWorld.isAirBlock(feet)) {
            EnumFacing[] order = { look.getOpposite(), look.rotateY(), look.rotateYCCW(), look };
            for (EnumFacing f : order) {
                BlockPos support = feet.offset(f);
                if (!mc.theWorld.isAirBlock(support)) { place(held, support, f.getOpposite()); return; }
            }
        } else if (mc.theWorld.isAirBlock(feet.offset(look))) {
            place(held, feet, look);
        }
    }

    private void place(ItemStack held, BlockPos support, EnumFacing side) {
        Vec3 hit = new Vec3(
                support.getX() + 0.5 + side.getFrontOffsetX() * 0.5,
                support.getY() + 0.5,
                support.getZ() + 0.5 + side.getFrontOffsetZ() * 0.5);
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held, support, side, hit)) {
            mc.thePlayer.swingItem();
        }
    }

    private int cps(List<Long> l) { return l.size(); }

    private void countClicks() {
        boolean l = Mouse.isButtonDown(0), r = Mouse.isButtonDown(1);
        long now = System.currentTimeMillis();
        if (mc.currentScreen == null) {
            if (l && !lastL) left.add(now);
            if (r && !lastR) right.add(now);
        }
        lastL = l; lastR = r;
        while (!left.isEmpty() && now - left.get(0) > 1000) left.remove(0);
        while (!right.isEmpty() && now - right.get(0) > 1000) right.remove(0);
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL || mc.thePlayer == null) return;
        countClicks();
        if (mc.gameSettings.showDebugInfo) return;
        FontRenderer fr = mc.fontRendererObj;
        int w = e.resolution.getScaledWidth(), h = e.resolution.getScaledHeight();

        List<String> lines = new ArrayList<String>();
        if (on("FPS")) lines.add("FPS: " + Minecraft.getDebugFPS());
        if (on("CPS")) lines.add("CPS: " + cps(left) + " | " + cps(right));
        if (on("Ping")) {
            NetworkPlayerInfo info = mc.getNetHandler() == null ? null
                    : mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            lines.add("Ping: " + (info == null ? 0 : info.getResponseTime()) + " ms");
        }
        if (on("Coords")) lines.add("XYZ: " + (int) mc.thePlayer.posX + " / " + (int) mc.thePlayer.posY + " / " + (int) mc.thePlayer.posZ);
        if (on("Direction")) lines.add("Facing: " + mc.thePlayer.getHorizontalFacing().getName());
        if (on("Clock")) lines.add(new SimpleDateFormat("HH:mm").format(new Date()));

        int y = 5;
        for (String s : lines) { fr.drawStringWithShadow(s, 5, y, 0xFFFFFF); y += 11; }

        if (on("Keystrokes")) drawKeys(5, y + 5, fr);

        // potions (right side)
        if (on("Potions")) {
            int py = 5;
            for (Object o : mc.thePlayer.getActivePotionEffects()) {
                PotionEffect pe = (PotionEffect) o;
                String t = I18n.format(pe.getEffectName()) + " " + Potion.getDurationString(pe);
                fr.drawStringWithShadow(t, w - fr.getStringWidth(t) - 5, py, 0xFFFFFF);
                py += 11;
            }
        }

        // armor (right middle)
        if (on("Armor")) {
            int ay = h / 2 - 40;
            for (int i = 3; i >= 0; i--) {
                ItemStack s = mc.thePlayer.inventory.armorInventory[i];
                if (s == null) continue;
                GlStateManager.pushMatrix();
                RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(s, w - 22, ay);
                mc.getRenderItem().renderItemOverlays(fr, s, w - 22, ay);
                RenderHelper.disableStandardItemLighting();
                GlStateManager.popMatrix();
                if (s.getMaxDamage() > 0) {
                    String d = String.valueOf(s.getMaxDamage() - s.getItemDamage());
                    fr.drawStringWithShadow(d, w - 26 - fr.getStringWidth(d), ay + 4, 0xFFFFFF);
                }
                ay += 18;
            }
        }
    }

    private void box(int x, int y, int wd, int ht, boolean down, String label, FontRenderer fr) {
        Gui.drawRect(x, y, x + wd, y + ht, down ? 0xAAFFFFFF : 0x66000000);
        int c = down ? 0x000000 : 0xFFFFFF;
        fr.drawString(label, x + (wd - fr.getStringWidth(label)) / 2, y + (ht - 8) / 2, c);
    }

    private void drawKeys(int x, int y, FontRenderer fr) {
        GameSettingsHolder g = new GameSettingsHolder();
        box(x + 24, y, 22, 22, g.f, "W", fr);
        box(x, y + 24, 22, 22, g.l, "A", fr);
        box(x + 24, y + 24, 22, 22, g.b, "S", fr);
        box(x + 48, y + 24, 22, 22, g.r, "D", fr);
        box(x, y + 48, 34, 20, Mouse.isButtonDown(0), "LMB", fr);
        box(x + 36, y + 48, 34, 20, Mouse.isButtonDown(1), "RMB", fr);
    }

    static class GameSettingsHolder {
        boolean f = mc.gameSettings.keyBindForward.isKeyDown();
        boolean l = mc.gameSettings.keyBindLeft.isKeyDown();
        boolean b = mc.gameSettings.keyBindBack.isKeyDown();
        boolean r = mc.gameSettings.keyBindRight.isKeyDown();
    }

    public static class MenuGui extends GuiScreen {
        private final List<String> names = new ArrayList<String>(mods.keySet());

        private String label(String n) { return n + ": " + (on(n) ? "ON" : "OFF"); }

        @Override
        public void initGui() {
            buttonList.clear();
            int cw = 120, ch = 18, step = 20;
            int rows = (names.size() + 1) / 2;
            int startY = 30;
            for (int i = 0; i < names.size(); i++) {
                int col = i % 2, row = i / 2;
                int x = col == 0 ? width / 2 - cw - 2 : width / 2 + 2;
                buttonList.add(new GuiButton(i, x, startY + row * step, cw, ch, label(names.get(i))));
            }
            buttonList.add(new GuiButton(100, width / 2 - 50, startY + rows * step + 6, 100, ch, "Done"));
        }

        @Override
        protected void actionPerformed(GuiButton b) throws IOException {
            if (b.id == 100) { mc.displayGuiScreen(null); return; }
            String n = names.get(b.id);
            mods.put(n, !on(n));
            b.displayString = label(n);
            save();
        }

        @Override
        public void drawScreen(int mx, int my, float pt) {
            drawDefaultBackground();
            drawCenteredString(fontRendererObj, "Nova Client", width / 2, 12, 0xFFFFFF);
            super.drawScreen(mx, my, pt);
        }

        @Override
        public boolean doesGuiPauseGame() { return false; }
    }
              }

package wintahh.rageutils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import wintahh.rageutils.screen.HudCustomizeScreen;
import wintahh.rageutils.screen.MaterialsScreen;

public class KeyBinds {

    public static final KeyBinding.Category CATEGORY =
        new KeyBinding.Category(Identifier.of("rageutils", "general"));

    public static KeyBinding materialsGuide;
    public static KeyBinding hudCustomize;
    public static KeyBinding hideArmor;

    public static void register() {
        materialsGuide = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.rageutils.materials_guide",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY
        ));

        hudCustomize = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.rageutils.hud_customize",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
        ));

        hideArmor = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.rageutils.hide_armor",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (materialsGuide.wasPressed()) {
                client.setScreen(new MaterialsScreen());
            }
            while (hudCustomize.wasPressed()) {
                client.setScreen(new HudCustomizeScreen());
            }
            while (hideArmor.wasPressed()) {
                RageUtils.HIDE_ARMOR.toggle();
            }
        });
    }
}

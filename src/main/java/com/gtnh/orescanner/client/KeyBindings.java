package com.gtnh.orescanner.client;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class KeyBindings {

    public static KeyBinding scanKey;

    public static void init() {
        scanKey = new KeyBinding("Scan Ore Veins", Keyboard.KEY_O, "key.categories.gtnh_orescanner");
        ClientRegistry.registerKeyBinding(scanKey);
    }
}

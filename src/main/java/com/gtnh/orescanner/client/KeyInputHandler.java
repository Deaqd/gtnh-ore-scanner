package com.gtnh.orescanner.client;

import com.gtnh.orescanner.scanner.OreScannerService;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class KeyInputHandler {

    private static long lastScanTime = 0L;
    private static final long COOLDOWN_MS = 1500L;

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (KeyBindings.scanKey != null && KeyBindings.scanKey.isPressed()) {
            long now = System.currentTimeMillis();
            if (now - lastScanTime >= COOLDOWN_MS) {
                lastScanTime = now;
                OreScannerService.executeScan();
            }
        }
    }
}

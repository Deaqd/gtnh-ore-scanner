package com.gtnh.orescanner.config;

import java.io.File;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraftforge.common.config.Configuration;

@SideOnly(Side.CLIENT)
public class OreScannerConfig {

    public static Configuration config;
    public static final String CATEGORY_GENERAL = Configuration.CATEGORY_GENERAL;

    public static int scanRadius = 8;
    public static int scanRadiusChunks = 8;
    public static boolean soundFeedback = true;
    public static boolean checkAntiXray = false;

    public static void init(File configFile) {
        if (config == null) {
            config = new Configuration(configFile);
            loadConfig();
        }
    }

    public static void loadConfig() {
        try {
            config.load();
            scanRadius = config.getInt(
                "scanRadius",
                CATEGORY_GENERAL,
                8,
                1,
                32,
                "Scan radius in chunks around the player"
            );
            scanRadiusChunks = scanRadius;
            soundFeedback = config.getBoolean(
                "soundFeedback",
                CATEGORY_GENERAL,
                true,
                "Play sound upon scan completion"
            );
            checkAntiXray = config.getBoolean(
                "checkAntiXray",
                CATEGORY_GENERAL,
                false,
                "Enable server anti-xray detection warnings (disabled by default)"
            );
        } catch (Exception e) {
            System.err.println("[GTNH Ore Vein Scanner] Error loading config: " + e.getMessage());
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    public static int getScanRadius() {
        return scanRadius;
    }
}

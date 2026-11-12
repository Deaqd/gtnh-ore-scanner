package com.gtnh.orescanner;

import com.gtnh.orescanner.client.KeyBindings;
import com.gtnh.orescanner.client.KeyInputHandler;
import com.gtnh.orescanner.config.OreScannerConfig;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = GTNHOreScannerMod.MODID,
    name = GTNHOreScannerMod.NAME,
    version = GTNHOreScannerMod.VERSION,
    dependencies = "required-after:visualprospecting;after:gregtech",
    acceptableRemoteVersions = "*"
)
public class GTNHOreScannerMod {

    public static final String MODID = "gtnh_orescanner";
    public static final String NAME = "GTNH Ore Vein Scanner";
    public static final String VERSION = "1.0.0";

    public static final Logger LOG = LogManager.getLogger(MODID);

    @Instance(MODID)
    public static GTNHOreScannerMod instance;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("Pre-initializing " + NAME);
        OreScannerConfig.init(event.getSuggestedConfigurationFile());
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        LOG.info("Initializing " + NAME);
        if (event.getSide() == Side.CLIENT) {
            KeyBindings.init();
            FMLCommonHandler.instance().bus().register(new KeyInputHandler());
        }
    }
}

# GTNH Ore Vein Scanner: Technical Specification (v2.0)

## 1. Project Identity & Target
- **Mod Name**: `GTNH Ore Vein Scanner` (`gtnh-orescanner`)
- **Target Platform**: Minecraft 1.7.10 (Forge 10.13.4.1614)
- **Modpack Compatibility**: GregTech: New Horizons 2.9.0 (Java 17–26 runtime)
- **Primary Objective**: Pure client-side mod that scans loaded client chunks for GregTech 5 Unofficial ore veins and directly registers them into VisualProspecting's client database for JourneyMap visualization.

---

## 2. Core Architecture & Layer Separation

### 2.1 Keybinding & Configuration
- **Keybinding**: Registered under category `"key.categories.gtnh_ore_scanner"`. Default key: `Keyboard.KEY_O`.
- **Scan Cooldown**: 1.5-second debounce preventing double-tap spam.
- **Configuration (`gtnh_ore_scanner.cfg`)**:
  - `scanRadiusChunks`: Default `8` (clamped to client render distance).
  - `soundFeedback`: Default `true`.

### 2.2 Thread-Safe Chunk Snapshotting
To prevent `ConcurrentModificationException` when iterating client chunk tile entities:
1. On hotkey trigger, main client thread iterates loaded chunks and takes an immutable snapshot:
   ```java
   List<OreSnapshot> snapshots = new ArrayList<>();
   for (Chunk chunk : loadedChunksInRange) {
       for (Object te : chunk.chunkTileEntityMap.values()) {
           if (te instanceof GT_TileEntity_Ores) {
               GT_TileEntity_Ores oreTe = (GT_TileEntity_Ores) te;
               snapshots.add(new OreSnapshot(chunk.xPosition, chunk.zPosition, oreTe.xCoord, oreTe.yCoord, oreTe.zCoord, oreTe.mMetaData));
           }
       }
   }
   ```
2. The snapshot list is passed to a background `ExecutorService`.

### 2.3 Symmetrical Chunk Coordinate Normalization
Accounts for GregTech 5U's `Math.abs(coord) % 3 == 1` grid mirroring across chunk 0:
```java
public static int getVeinCenterChunk(int chunkCoord) {
    if (chunkCoord >= 0) {
        return (chunkCoord / 3) * 3 + 1;
    } else {
        return -((-chunkCoord / 3) * 3 + 1);
    }
}
```

### 2.4 Multi-Ore Vein Disambiguation Algorithm
1. Group snapshots by vein center `(centerChunkX, centerChunkZ)`.
2. Extract all distinct `Materials` where `mMetaData < 16000` (filtering out small ores `mMetaData >= 16000`).
3. For candidate `WorldgenGTOreLayer` layers:
   - Match detected materials against `{mPrimaryMeta, mSecondaryMeta, mBetweenMeta, mSporadicMeta}`.
   - Filter candidates by Y-level range: `oreY >= layer.mMinY && oreY <= layer.mMaxY`.
   - Select the vein type with the highest material match score.

### 2.5 VisualProspecting Cache Injection
```java
OreVeinPosition veinPos = new OreVeinPosition(
    dimensionId,
    centerChunkX,
    centerChunkZ,
    resolvedVeinType,
    false,
    VeinSource.PROSPECTION
);

// Dispatched back to client render thread:
Minecraft.getMinecraft().addScheduledTask(() -> {
    ClientCache.instance.putOreVeins(Collections.singletonList(veinPos));
});
```

---

## 3. Failure Mode Mitigations
1. **Server Anti-Xray / Orebfuscator**:
   - If an unprospected chunk yields 0 ores across subterranean levels where stone is present, trigger a non-intrusive chat note: `"[Ore Scanner] No ores visible in loaded chunks. Server anti-xray may be obfuscating underground blocks."`
2. **Missing Dependencies**:
   - Declare `@Mod(dependencies = "required-after:visualprospecting;after:gregtech")`.

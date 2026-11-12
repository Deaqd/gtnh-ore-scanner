# GTNH Ore Vein Scanner v1.0.2

A high-performance, client-side utility mod for **Minecraft 1.7.10** and the **GregTech: New Horizons 2.9+** modpack.

---

> [!IMPORTANT]
> ### 🤖 AI Architecture Disclosure
> This mod was designed, implemented, and verified using **Google Antigravity** and the **Elephant-Goldfish Model (EGM)** multi-agent swarm architecture. See [DISCLAIMER.md](DISCLAIMER.md) for full architecture and multiplayer fair-play details.

---

### ✨ What's New in v1.0.2
- **Instant Map & Minimap Display**: Added map layer invalidation triggers (`OreVeinLayerManager.forceRefresh()` and `LayerRefreshEvent`). Discovered veins appear immediately on JourneyMap and Xaero's World Map without needing to toggle or reopen the map screen.
- **Deep VeinType Matching Engine**: Full resolution across VisualProspecting's `VeinTypeCaching` registry—matching both internal identifiers (e.g. `ore.mix.magnetite`) and localized display names (e.g. `Magnetite & Gold`), ensuring custom and modpack-specific ore mixes map accurately.
- **High-Trust VeinSource Administration**: Elevated default vein registration trust level to `VeinSource.ADMIN` / `VeinSource.API` so that client-scanned veins always overwrite unverified or outdated cache stubs.

---

### 🐛 What's Fixed
- **Map Waypoints Missing Bug ("Found ores, but not showing on the map")**:
  - **Root Cause**: `VisualProspectingBridge.injectPositionsDirect` resolved the 2-argument overload `putOreVeins(List, boolean)` instead of the 1-argument overload, throwing `IllegalArgumentException: wrong number of arguments: 1 expected: 2` on the client thread and silently aborting cache insertion.
  - **Resolution**: Added dual-overload reflective invocation supporting both `putOreVeins(List)` and `putOreVeins(List, boolean)`, backed by a loop fallback and individual exception isolation.
  - **`NO_VEIN` False Positive Guard**: VisualProspecting returns `VeinType.NO_VEIN` rather than `null` when a candidate name is not found. Added strict `isNoVein` validation so dummy invisible veins are never created.

---

### 📥 Installation
1. Download **`gtnh_orescanner-1.0.2.jar`** below.
2. Place it into your GTNH `.minecraft/mods` or Prism Launcher instance mods folder.
3. Remove any previous `gtnh_orescanner-1.0.x.jar`.
4. Launch the game, press **`O`** in-game, and see all ore veins appear directly on your map!

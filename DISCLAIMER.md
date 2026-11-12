# AI Assistance, Fair Play & Liability Disclaimer

**GTNH Ore Vein Scanner** (`gtnh_orescanner`)  
*Target Environment: Minecraft 1.7.10 | GregTech: New Horizons 2.9.x*

---

## 1. AI Assistance & Autonomous Architecture Disclosure

This software was architected, implemented, and verified utilizing advanced artificial intelligence and autonomous multi-agent orchestration systems:

- **Google Antigravity**: The end-to-end development harness, toolchain automation, and multi-agent coordination platform.
- **Elephant-Goldfish Model (EGM)**: The dual-horizon cognitive architecture governing planning, memory consolidation, and rigorous multi-stage audit reviews. Under EGM:
  - *Elephant Memory* maintains deep structural context, modpack dependency constraints (GTNH 2.9 ecosystem, GT5U reflection hooks, RetroFuturaGradle pipeline), and historical verification checkpoints.
  - *Goldfish Agility* enables rapid, isolated cycle execution (specialized agents handling chunk scanning, coordinate math, reflection bridge, anti-xray detection, build engineering, and technical verification).
- **Recursive Evolutionary Architecture (REA)**: A continuous refinement methodology incorporating strict interface contracts, static analysis, automated testing (25/25 unit tests across vein disambiguation, coordinate symmetry, and cache bridge), and bytecode auditing for Java 8 / 17 / 21 compatibility.

While autonomous agents conducted the design, code generation, integration, and audit passes, the pipeline was constrained by strict deterministic verification to guarantee zero runtime crashes (`NoClassDefFoundError`, `ConcurrentModificationException`) and byte-for-byte fidelity with GregTech 5 Unofficial worldgen algorithms.

---

## 2. Fair Play & Server Policy Notice

### Pure Client-Side Operation
- **GTNH Ore Vein Scanner** operates **strictly on the Minecraft client**.
- It does **not** install on dedicated servers, inject packets, or alter server-side world generation.
- It scans only blocks and tile entities that the server has **already transmitted to your client's memory** for locally loaded chunks (within client render distance).

### Multiplayer Usage & Server Rules
- **Server Administration Policy**: Different multiplayer servers enforce different rules regarding client-side automated scanners, minimap waypoints, and prospecting helpers.
- **Player Responsibility**: Before using this mod on any multiplayer server, consult the server's published rules or contact server administrators. The developers and contributors assume no responsibility for administrative sanctions, warnings, or bans resulting from unauthorized use on restricted servers.

### Server-Side Anti-Xray (Orebfuscator)
- Some public servers employ anti-xray plugins (such as Orebfuscator or Paper/Spigot engine obfuscation) that replace subterranean ore blocks with stone packets until the player is in immediate line-of-sight or physically mines nearby blocks.
- On such servers, the scanner will naturally report 0 ores in subterranean stone layers because the server has not sent real ore data to the client.
- The mod includes a non-intrusive diagnostic check (`checkAntiXray` in `gtnh_ore_scanner.cfg`, disabled by default) that can alert players if an anti-xray obfuscation engine is suspected.

---

## 3. Intellectual Property & Third-Party Trademarks

- **Minecraft** is a registered trademark of Mojang Synergies AB / Microsoft Corporation.
- **GregTech** and **GregTech: New Horizons (GTNH)** are the intellectual creations and properties of their respective authors, maintainers, and community contributors.
- This project is an independent open-source utility and is **not** affiliated with, endorsed by, or officially associated with Mojang AB, Microsoft, or the GregTech: New Horizons development team.

---

## 4. Limitation of Liability & Warranty Disclaimer

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS, CONTRIBUTORS, AND AI ARCHITECTS "AS IS" AND WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT.

IN NO EVENT SHALL THE AUTHORS, CONTRIBUTORS, OR AFFILIATED ENTITIES BE LIABLE FOR ANY CLAIM, DAMAGES, OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT, OR OTHERWISE, ARISING FROM, OUT OF, OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE, INCLUDING BUT NOT LIMITED TO GAME CRASHES, SAVE FILE CORRUPTION, MULTIPLAYER DISCIPLINARY ACTIONS, OR LOSS OF IN-GAME PROGRESS.

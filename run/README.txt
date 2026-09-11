ItemGuard 1.0 local Paper 1.21.8 test server
==============================================

This directory is the Paperweight / run-paper working directory.

Automated start (preferred, does not change the plugin runtime classpath):

  Windows:
    gradlew.bat runServer

  Unix:
    ./gradlew runServer

That task downloads Paper 1.21.8 into run/ and copies the built ItemGuard JAR into run/plugins/.

If Gradle cannot download Paper, start a Paper 1.21.8 server yourself:

1. Download paper-1.21.8-*.jar from https://papermc.io/downloads/paper
2. Place it in this folder as paper.jar
3. Copy build/libs/ItemGuard-1.0.0-SNAPSHOT.jar into plugins/
4. Accept the EULA
5. Start with Java 21:
     java -Xms1G -Xmx2G -jar paper.jar nogui

Do not treat a successful Gradle build as a runtime test.

Runtime-only checklist (must be done on a real Paper 1.21.8 process):

A. Paper + ItemGuard, no HuskSync
   - enable, no NoClassDefFoundError
   - "HuskSync Integration: disabled"
   - /ig status works

B. Paper + ItemGuard + HuskSync 3.8.7
   - integration enabled, no linkage error

C. Join with 64 diamond / elytra / netherite sword: no UNKNOWN_GAIN
D. /ig reload while online: no full-inventory UNKNOWN
E. Plugin disable/enable while players online: baseline rebuilt
F. Pickup 1 / 64, partial pickup, hopper race
G. Q drop, Ctrl+Q, death drop, cancelled drop
H. Chest click, shift-click (including 32-space leftover), number keys, double-click, drag
I. Craft, smithing, anvil, grindstone, brewing remainder
J. Bundle insert/extract, shulker slot move vs placed-shulker extract
K. keepInventory true/false
L. Creative inventory creation (should be CREATIVE_INVENTORY, not dupe)
M. /give (UNKNOWN is allowed unless ItemGuardApi is used)
N. HuskSync cross-server, restore, timeout — only if a proxy/two backends exist

Do not claim those items RUNTIME TESTED unless they were actually run here.

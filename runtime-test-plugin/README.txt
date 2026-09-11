ItemGuardTestPlugin
====================

Acceptance helper only. Not a product dependency.

Build after ItemGuard JAR exists:

  cd runtime-test-plugin
  gradle jar

Copy:

  build/libs/ItemGuardTestPlugin-1.0.0-test.jar

into the Paper plugins folder next to ItemGuard.

Commands (op):

  /igtest api <player>              ItemGuardApi + addItem 64 DIAMOND
  /igtest illegal <player>          Sharpness 255 sword
  /igtest custom <player>          legal custom metadata sword
  /igtest oversized <player>       amount > max stack
  /igtest legal-maxstack <player>  custom max stack, legal amount
  /igtest signature <player>       100x serializeAsBytes hash of held item
  /igtest shulker <player>         shulker with 27x64 diamond
  /igtest bundle <player>          bundle containing 64 diamond
  /igtest cancel-drop              toggle cancelling PlayerDropItemEvent

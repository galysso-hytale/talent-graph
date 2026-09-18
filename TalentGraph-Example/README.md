# TalentGraph-Example

A talent graph with no code and no build: copy this folder into the server's
`mods/` directory next to the Talent Graph mod, start the server, type
`/talents`.

```
TalentGraph-Example/
  manifest.json                                   who you are; keep "IncludesAssetPack": true and "ServerVersion"
  Server/TalentGraph/Graphs/Example.json          the graph: one file per graph, the file name is its id
  Common/UI/Custom/TalentGraph/Example/            your images, sent to the players automatically
      Background.png                               the map under the graph, any size
      Spark.png                                    a custom icon, a 64×64 (or 128×128) PNG
  Server/Entity/Effects/Example/                   the entity effects the talents hold (see "Effects")
      Example_Vigor_Regen.json                     +1 health every 2 s, no HUD icon
      Example_Flaming_Blade.json                   a flag, empty on its own
  Server/Item/Interactions/Weapons/Sword/.../Weapon_Sword_Primary_Swing_Down_Damage.json
                                                  what the flag changes: overrides the vanilla file of the
                                                  same name ("Parent": "super" keeps it whole) and adds an
                                                  "EffectCondition" after the hit: flag held → Burn
```

## The graph file

- `"Name"`: shown in the page header.
- `"Background"`: optional. A path relative to `Common/UI/Custom/TalentGraph/`.
  With a background, the canvas is the image and `X`/`Y` are its pixels: read
  them in your image editor. Without one, the canvas is whatever the nodes span.
- `"Talents"`: one entry per node.
  - `"Id"`: lowercase letters, digits, `_`, `-`. Other talents refer to it.
  - `"Name"`, `"MaxRank"` (default 1), `"Cost"` per rank (the last value repeats).
  - `"Requires"`: ids of the talents needed first.
  - `"Icon"`: either one word, the id of a vanilla item (`"Weapon_Sword_Copper"`,
    the 64×64 icon of that item), or a path relative to
    `Common/UI/Custom/TalentGraph/` (`"Example/Spark.png"`).
  - `"X"`, `"Y"`: top-left corner of the 72×72 node.

## Editing live

Run `/talents track start` as an admin, then edit and save the JSON: the tree
reloads on its own for everyone who has it open. If the file has a problem,
you alone see it drawn on the tree, with a banner at the top; everyone else
keeps the previous version. `/talents track stop` ends it. `/talents reload`
reads the files again without the watcher.

## Effects

Each talent can carry an `"Effects"` list: what it does to the player.
The format is in `docs/EFFECTS.md` of the mod. This pack shows two types:

- `Stat` — `vigor` adds health, inline, nothing else to write.
- `EntityEffect` — `vigor` (from rank 2) holds `Example_Vigor_Regen`, a
  Hytale effect file that regenerates health; `flaming_blade` holds
  `Example_Flaming_Blade`, an empty effect used as a **flag**: the vanilla
  damage of the sword's downward strike is overridden in
  `Server/Item/Interactions/` — a file of the same name, `"Parent": "super"`
  to keep everything vanilla, and an `EffectCondition` appended after the
  hit that burns the target when the attacker holds the flag. Every sword
  inherits that file, so every sword gets the branch. Nothing in the mod knows about swords;
  that is all Hytale JSON, and the same trick works for any interaction.
  Override the *damage* file, not the selector: swords set their damage
  through `InteractionVars` in their item file, so a change to the
  selector's default only reaches the swords that define none.

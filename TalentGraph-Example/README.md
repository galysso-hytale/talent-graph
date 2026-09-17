# TalentGraph-Example

A talent graph with no code and no build: copy this folder into the server's
`mods/` directory next to the Talent Graph mod, start the server, type
`/talents`.

```
TalentGraph-Example/
  manifest.json                                   who you are; keep "IncludesAssetPack": true
  Server/TalentGraph/Graphs/Example.json          the graph: one file per graph, the file name is its id
  Common/UI/Custom/TalentGraph/Example/            your images, sent to the players automatically
      Background.png                               the map under the graph, any size
      Spark.png                                    a custom icon, a 64×64 (or 128×128) PNG
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

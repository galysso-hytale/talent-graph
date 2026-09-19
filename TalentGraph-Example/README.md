# TalentGraph-Example

A talent graph with no code and no build: copy this folder into the server's
`mods/` directory next to the Talent Graph mod, start the server, type
`/talents`. It shows every effect type the mod knows, in every form, so
that each node is something to copy.

```
TalentGraph-Example/
  manifest.json                                   who you are; keep "IncludesAssetPack": true and "ServerVersion"
  Server/TalentGraph/Graphs/Example.json          the graph: one file per graph, the file name is its id
  Server/Languages/en-US/talentgraph.lang         the words of this pack's own ids in the tooltips (see "Descriptions")
  Common/UI/Custom/TalentGraph/Example/            your images, sent to the players automatically
      Background.png                               the map under the graph, any size
      Spark.png                                    a custom icon, a 64×64 (or 128×128) PNG
  Server/Entity/Effects/Example/                   the entity effects the talents hold (see "Effects")
      Example_Vigor_Regen_1.json, _2.json          +1 / +2 health every 2 s, no HUD icon; one per rank
      Example_Berserk.json                         half knockback, a tint, a HUD icon; carries a "Name"
      Example_Reckless.json                        "Debuff": true and "ApplyConditions": red, conditional line
      Example_Flaming_Blade.json                   a flag, empty on its own
      Example_Ward.json                            4 s of half damage, applied by the Ward spell
  Server/Item/Interactions/Weapons/Sword/.../Weapon_Sword_Primary_Swing_Down_Damage.json
                                                  what the flag changes: overrides the vanilla file of the
                                                  same name ("Parent": "super" keeps it whole) and adds an
                                                  "EffectCondition" after the hit: flag held → Burn
  Server/Item/RootInteractions/Example/            the spells, one root per key binding (see "Writing a spell")
  Server/Item/Interactions/Example/                what each spell does: cost, effect, projectile, damage
  Server/Projectiles/Example/                      the three arcane bolts
```

## The graph file

- `"Name"`: shown in the page header.
- `"Background"`: optional. A path relative to `Common/UI/Custom/TalentGraph/`.
  With a background, the canvas is the image and `X`/`Y` are its pixels: read
  them in your image editor. Without one, the canvas is whatever the nodes span.
- `"Equipment"`: optional. What the graph forbids by default, and the
  exceptions: here every weapon and every piece of armour is forbidden but
  swords, daggers and leather. Talents open the rest (see "Effects").
- `"Talents"`: one entry per node.
  - `"Id"`: lowercase letters, digits, `_`, `-`. Other talents refer to it.
  - `"Name"`, `"MaxRank"` (default 1), `"Cost"` per rank (the last value repeats).
  - `"Requires"`: ids of the talents needed first.
  - `"Icon"`: either one word, the id of a vanilla item (`"Weapon_Sword_Copper"`,
    the 64×64 icon of that item), or a path relative to
    `Common/UI/Custom/TalentGraph/` (`"Example/Spark.png"`).
  - `"X"`, `"Y"`: top-left corner of the 72×72 node.
  - `"Description"`, `"Details"`: optional texts, see "Descriptions".
  - `"Effects"`: what the talent does, see "Effects".

## Editing live

Run `/talents track start` as an admin, then edit and save the JSON: the tree
reloads on its own for everyone who has it open. If the file has a problem,
you alone see it drawn on the tree, with a banner at the top; everyone else
keeps the previous version. `/talents track stop` ends it. `/talents reload`
reads the files again without the watcher.

## Effects

Each talent carries an `"Effects"` list: what it does to the player. The
reference is `docs/EFFECTS.md` of the mod; this graph shows each type at
least once.

- **`Stat`** — a bonus (or malus) on a stat's maximum, or minimum.
  `vigor` adds health, one value per rank (`[10, 20, 35]`); `iron_skin`
  multiplies stamina by 0.9, a malus the tooltip shows in red;
  `second_wind` adds two points of stamina; `attunement` opens the mana
  pool, `nova` adds signature energy.
- **`EntityEffect`** — a Hytale effect held for as long as the talent is.
  `vigor` holds a regeneration from rank 2, a stronger one at rank 3 (one
  id per rank, counted from rank 1 even with `FromRank`, hence the
  repeated first entry); `berserk` holds two: a buff with a `"Name"` and a HUD
  icon, and a `"Debuff": true` that only applies in water
  (`"ApplyConditions"`), which the tooltip paints red and marks
  conditional; `flaming_blade` holds an empty effect used as a **flag**:
  the vanilla damage of the sword's downward strike is overridden in
  `Server/Item/Interactions/` — a file of the same name, `"Parent": "super"`
  to keep everything vanilla, and an `EffectCondition` appended after the
  hit that burns the target when the attacker holds the flag. Every sword
  inherits that file, so every sword gets the branch. Nothing in the mod
  knows about swords; that is all Hytale JSON, and the same trick works for
  any interaction. Override the *damage* file, not the selector: swords set
  their damage through `InteractionVars` in their item file, so a change to
  the selector's default only reaches the swords that define none.
- **`Equipment`** — opens or closes kinds of items on top of the graph's
  baseline. `attunement` allows staffs, wands and magic weapons;
  `iron_skin` allows iron armour; `marksman` allows bows and crossbows;
  `oath_of_steel` forbids them again with `"Priority": 1`, which beats
  Marksman's allow whatever the order of unlocking.
- **`Ability`** — a spell on one of the six keys, optionally only with a
  given item in hand. `arcane_bolt` puts a bolt on the left click of any
  staff or magic weapon, a stronger one per rank; `ward` on the right
  click; `nova` on the signature key; `mend` on R, maces included;
  `blink` on E with anything in hand or nothing; `focus` on the middle
  click, and lets creative mode keep picking blocks; `storm` names the
  flame staff by id with `"Priority": 1`, so on that staff the signature
  key casts Storm instead of Nova; `battle_mage` wraps the sword's own
  vanilla signature and adds a nova after it (`RunRootInteraction`).
  In game, the abilities bound right now show under the mana bar, one
  square each with the talent's icon and its key; `nova` gives its
  ability an `"Icon"` of its own (`Ingredient_Fire_Essence`, the same
  words as a talent's `"Icon"`), since the talent's picture is about the
  signature energy it adds. A spell the player cannot cast right now is
  greyed — on cooldown, or short of mana or stamina — and a cooldown
  shows as a veil sinking as it recovers. Every cooldown of the example
  sits on the root interaction: one field the game enforces and the
  tooltip and HUD read, nothing to repeat in the graph.
- **`Movement`** — `spring` raises jump height by a tenth. Small values:
  speed changes everyone's game, not only the player's.

## Writing a spell

A spell is three Hytale files and one line in the graph:

1. `Server/Item/RootInteractions/Example/Example_Ward.json` — the **root**,
   what the key starts: the list of interactions to run, `RequireNewClick`,
   and the `Cooldown` the tooltip shows. Its file name is what the graph
   names in `"Interaction"`.
2. `Server/Item/Interactions/Example/Example_Ward.json` — the **chain**:
   a `StatsCondition` checks the cost (`"Costs": { "Stamina": 3 }`) and
   runs `TalentGraph_NoStamina` on failure (a notification the mod
   provides, with `TalentGraph_NoMana`; write your own with
   `"Type": "TalentGraph_Notify", "Message": "…"`), then a `Serial` pays
   it (`ChangeStat`) and does the thing: `ApplyEffect`, `LaunchProjectile`
   (`Example_Bolt_1`, with its file in `Server/Projectiles/`), a
   `Selector` around the player (`Example_Nova_1` → `Example_Nova_1_Damage`).
3. The graph line:
   `{ "Type": "Ability", "Slot": "Secondary", "Interaction": "Example_Ward", "HeldItem": ["Family=Staff"] }`.

Read the `$Comment` at the top of each file: `Example_Bolt_3` branches on
crouching (`Condition`) and charging (`Charging`); `Example_Focus` keeps
a vanilla behaviour in creative mode (`RequiredGameMode`); `Example_Blade_Nova`
runs a vanilla root (`RunRootInteraction`) before its own effect.

## Descriptions

Hover a node: a card lists what the talent gives, one line per effect,
generated from the file — `+10 ➜ +20 Max health`, `Can use: iron armor`,
`Nova` beside the glyph of its key with its cost, cooldown and item in
hand on the lines under it. Gains are green, losses red, what a later rank
gives grey with `(from rank 2)`. Right-click a node for the detail panel: the same lines
with the value of every rank, and the longer texts.

Three places take your own words:

- `"Description"` on a **talent** (`vigor`, `berserk`): one line under the
  title, in the tooltip and the panel. Lore, mostly.
- `"Details"` on a talent (`vigor`, `oath_of_steel`): a paragraph, in the
  panel only.
- `"Description"` on an **effect** (`ward`, `focus`, `flaming_blade`):
  replaces the generated label of that line — the name of the spell, the
  name of the entity effect, the item list — and keeps the rest: colour,
  key, and the notes under the line (`Cost`, `Cooldown`, `With`). The
  cost is read from the `StatsCondition` at the top of the spell's chain,
  the cooldown from the root; when the chain hides them (a cost behind a
  `Condition`, a cooldown on one branch), `"Cost"` and `"Cooldown"` on
  the effect give the words — a copy you keep aligned, so prefer the root.

Without a description, a spell or an entity effect is named after its file
(`Example_Blink` → "Example Blink"), or after the effect's `"Name"` when
it has one (`berserk`). `Server/Languages/en-US/talentgraph.lang` is the
third way, and the one that translates: `ability.Example_Blink = Blink
(4 stamina)` names the spell in the tooltip, `effect.<Id>` an entity
effect, `stat.<Id>` a stat of your own. Add a folder per language the
client offers (`pt-BR`, `ru-RU`, `uk-UA`, `zh-CN`) with the same file. A
`"Name"`, `"Description"` or `"Details"` that is a key of these tables is
shown translated too.

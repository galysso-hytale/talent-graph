# Talent effects

What a talent does to the player is written in its graph file, as a list of
typed **effects**. This file is the reference: a value that is not listed
here is not supported, and the loader says so on the tree.

> **Status.** The format and its validation are in place. `Stat`,
> `EntityEffect`, `Equipment` and `Ability` are applied in game;
> `Movement` is accepted, shown in the report, and does nothing yet.

The mod does not invent game mechanics. Each effect type is a bridge to
something the server already does — entity stats, entity effects, root
interactions, inventory filters, movement settings. What Hytale can express
in its own JSON (an ability, a buff, a resistance) stays written in Hytale
JSON in your pack; the talent only attaches it to the player.

## Where effects go

```json
{
    "Name": "Warrior",
    "Equipment": {
        "Forbidden": ["Type=Weapon"],
        "Allowed":   ["Family=Sword"]
    },
    "Talents": [
        {
            "Id": "toughness", "Name": "Toughness", "MaxRank": 3, "Cost": [1, 2, 3],
            "Effects": [
                { "Type": "Stat", "Stat": "Health", "Amount": [10, 20, 35] },
                { "Type": "Stat", "Stat": "Stamina", "Amount": 0.9, "Calculation": "Multiplicative" }
            ]
        }
    ]
}
```

- `Effects` on a talent: a list, each entry with a `Type` and the fields of
  that type. A talent may have any number of effects, of any types.
- `Equipment` on the graph: the starting point for equipment restrictions,
  see [Equipment](#equipment).

## Conventions shared by every type

| Field | Meaning |
|---|---|
| `Type` | One of `Stat`, `EntityEffect`, `Equipment`, `Ability`, `Movement`. Case matters. |
| `FromRank` | Optional, default 1. The rank from which the effect applies; below it nothing is granted. Lets a talent give +10 health at rank 1 and, from rank 2, an ability as well. |

**Values per rank.** Every numeric `Amount` accepts one number or an array
with one value per rank; the last value repeats for higher ranks, exactly
like `Cost`. A value is the **absolute amount at that rank**, not an
increment: `[10, 20, 35]` gives +35 at rank 3, not +65. Losing a rank
brings the amount back down.

**Validation.** Effects are checked when the graph loads, against every
asset pack the server has loaded — at boot once all packs are in, and again
on each hot reload of the file. Every fault of an effect is a **warning**:
the graph stays playable, the faulty effect is dropped (or the faulty value
repaired, when the message says so), and the node shows the badge with the
message. This is deliberate: a typo in an effect must not take a whole
graph away from players. Edit the file or run `/talents reload` after
fixing a referenced asset (a stat, an entity effect, an interaction).

Messages name the effect by its position and type: `Effect #3 (Stat):
Unknown stat "Helth"`.

A *type* error — a string where a number is expected — is not an effect
fault but a file fault: the asset store rejects the file, the previous
version stays, and the tracker sees where it breaks.

## Stat

Moves the maximum (or minimum) of an entity stat.

```json
{ "Type": "Stat", "Stat": "Health", "Amount": 10 }
{ "Type": "Stat", "Stat": "Stamina", "Amount": [1.1, 1.2, 1.3], "Calculation": "Multiplicative" }
{ "Type": "Stat", "Stat": "Mana", "Amount": -5, "FromRank": 2 }
```

| Field | Required | Values | Default |
|---|---|---|---|
| `Stat` | yes | An `EntityStatType` id: one of the vanilla stats below, or one your pack defines in `Server/Entity/Stats/<Id>.json`. | — |
| `Amount` | yes | Number or per-rank array. Negative (additive) or below 1 (multiplicative) is a malus, for specialisations. | — |
| `Calculation` | no | `Additive`: the amount is added. `Multiplicative`: the bound is multiplied. | `Additive` |
| `Target` | no | `Max` or `Min`: which bound moves. | `Max` |

### How it is applied

The amounts of every ranked talent, across all loaded graphs, that move
the same bound of the same stat are folded into **one** Hytale stat
modifier: additive amounts add up, multiplicative amounts multiply
(`0.8` and `0.9` give `×0.72`). The result sits next to the modifiers of
armour, effects and weapons, under a fixed key per bound and calculation
(`Talent_Max_ADDITIVE`, `Talent_Max_MULTIPLICATIVE`, `Talent_Min_ADDITIVE`,
`Talent_Min_MULTIPLICATIVE`), and Hytale computes the bound as it always
does: `(base + all additives) × sum of all multiplicatives`.

- **Hytale adds multiplicatives across sources.** A talent at `×1.1` and a
  fruit buff at `×1.1` give `×2.2`, not `×1.21`; that is how the game
  combines armour, effects and weapons too, and this mod does not change
  it. Between talents the product rule above applies.
- Raising `Max` raises the current value by the same amount, so what is
  missing stays missing (20/100 becomes 70/150): a health talent gives
  its health at once, but is never a free heal. Lowering `Max` clamps the
  current value. Unlike armour, which leaves the bar where it was.
- The modifiers are saved with the player, as Hytale saves every stat
  modifier. They are brought back in line whenever the player enters a
  world, unlocks or resets a talent, or a graph file is loaded, reloaded
  or removed — never on each stat read. A player who is offline when a
  graph changes is corrected the moment they come back. If the mod is
  uninstalled, the `Talent_*` keys stay in the saves and keep applying;
  reset every player before removing it.

### Vanilla stats

Values from `Server/Entity/Stats/` of the 0.6.5 assets. *Initial*, *Min*
and *Max* are the player's, before any item or effect.

| Id | Role in game | Initial / Min / Max | Notes |
|---|---|---|---|
| `Health` | Hit points. | 100 / 0 / 100 | The obvious one. `Max` +20 is a fifth more health. Regeneration is defined in the stat itself and is not modified here; for a regeneration talent, use an `EntityEffect` modelled on `Potion_Health_Regen`. |
| `Stamina` | Sprinting, dodging, attacks. | 10 / −4 / 10 | Goes negative when overspent, hence the minimum. `Max` ×1.2 makes long fights easier. |
| `Mana` | Magic weapons' resource. | 0 / 0 / 0 | The maximum is **0** until a magic item opens it: a `Max` bonus alone gives nothing to a warrior, but adds to a wand's pool. |
| `Oxygen` | Breath under water. | 100 / 0 / 100 | Refills fast out of water. |
| `SignatureEnergy` | Charge of the weapon's signature ability (`Ability1`). | 0 / 0 / 0 | Maximum opened by the wielded weapon (sword 20, mace 8, flame staff 10, plain staffs none), filled by its hits. |
| `SignatureCharges` | Stored signature charges. | 0 / 0 / 100 | Internal to the signature system; hidden from tooltips. |
| `MagicCharges` | Charges of magic weapons, regenerating one every 2 s. | 0 / 0 / 0 | Maximum opened by the item; hidden from tooltips. |
| `Immunity` | Builds up while immune; at 100 applies the `Immune` effect. | 0 / 0 / 100 | Decays by itself. Of little use to a talent. |
| `StaminaRegenDelay` | Countdown before stamina regenerates. | 0 / −60 / 0 | Negative while waiting. Changing `Min` changes how long the wait can get. |
| `Ammo` | Technical: ammunition display. | 0 / 0 / 0 | Not worth modifying. |
| `GlidingActive` | Technical: whether the glider is open. | 0 / 0 / 1 | Not worth modifying. |
| `DeployablePreview` | Technical: deployable placement preview. | 0 / 0 / 1 | Not worth modifying. |

A stat your pack adds is accepted as soon as its file exists; it only has
an effect in game if some interaction reads it.

## EntityEffect

Keeps a Hytale `EntityEffect` on the player for as long as the talent is
held.

```json
{ "Type": "EntityEffect", "Id": "MyPack_Warrior_Regen" }
{ "Type": "EntityEffect", "Id": ["MyPack_Regen_1", "MyPack_Regen_2", "MyPack_Regen_3"] }
```

| Field | Required | Values | Default |
|---|---|---|---|
| `Id` | yes | An `EntityEffect` id: a file `Server/Entity/Effects/<Id>.json` in your pack (or a vanilla one). One id, or one per rank: the last one repeats for higher ranks, and only the id of the current rank is held, so `MyPack_Regen_2` replaces `MyPack_Regen_1` rather than stacking on it. | — |

The effect is held as an **infinite** effect whatever the file says about
`Duration`, and removed completely when the talent goes. Everything else
comes from the file. What a talent typically wants from one:

| Want | Write in the effect file | Vanilla file to copy |
|---|---|---|
| A **flag** for interactions | `{ "Infinite": true }` and nothing else. An `EffectCondition` interaction (`EntityEffectIds`, `Match: All`) branches on it: that is how an existing attack changes with a talent. See the example pack. | — |
| Regeneration | `"StatModifiers": { "Health": 1 }, "DamageCalculatorCooldown": 2` — +1 every 2 s. `"ValueType": "Percent"` for a percentage of the maximum. Works for `Stamina`, `Mana`, `SignatureEnergy` too. | `Food/Buff/HealthRegen_Buff_T1` |
| Less damage from a cause | `"DamageResistance": { "Fire": [ { "Amount": 0.25, "CalculationType": "Percent" } ] }`. Causes (`Server/Entity/Damage/`): `Physical`, `Slashing`, `Bludgeoning`, `Projectile`, `Elemental`, `Fire`, `Ice`, `Poison`, `Environmental`, `Environment`, `Fall`, `Drowning`, `Suffocation`, `OutOfWorld`, `Command`. `1.0` is immunity. | `Immunity/Immunity_Fire` |
| Speed, knockback | `"ApplicationEffects": { "HorizontalSpeedMultiplier": 1.1, "KnockbackMultiplier": 0.5 }`. For jumping and falling, use `Movement`. | `Status/Slow` |
| A visible signature | `"StatusEffectIcon": "UI/StatusEffects/HealthRegen.png"` for the HUD, `"ApplicationEffects": { "EntityTopTint": "#ff5a3c", "ModelVFXId": …, "Particles": […] }` on the body. | `Status/Burn` |

### Changing an attack with a talent: the flag

Nothing links the talent to the interaction. The effect file is empty;
the interaction asks, at the moment it runs, whether the entity carries an
effect of that **id**. Three files:

1. The flag, `Server/Entity/Effects/MyPack_Flaming_Blade.json`:
   ```json
   { "Infinite": true }
   ```
2. The talent, which puts it on the player:
   ```json
   { "Type": "EntityEffect", "Id": "MyPack_Flaming_Blade" }
   ```
3. The interaction to change. Overriding a vanilla file means a file of the
   same name in your pack; `"Parent": "super"` keeps everything the vanilla
   one had, and you replace only what you want (`Next` is replaced whole,
   so copy what the parent's `Next` did if you want to keep it):
   ```json
   {
     "Parent": "super",
     "Next": {
       "Type": "Serial",
       "Interactions": [
         { "Type": "ApplyEffect", "EffectId": "Red_Flash", "Entity": "Target" },
         {
           "Type": "EffectCondition",
           "EntityEffectIds": ["MyPack_Flaming_Blade"],
           "Match": "All",
           "Next": { "Type": "ApplyEffect", "EffectId": "Burn", "Entity": "Target" }
         }
       ]
     }
   }
   ```
   `EffectCondition` checks the entity running the interaction (the
   attacker) unless `"Entity": "Target"` says otherwise; `Match: All`
   passes when every listed effect is present, `None` when none is. With
   the flag, `Next` runs; without it, nothing more happens.

Who put the effect on the player does not matter to the interaction: a
talent, a potion, `/effect`, another mod. That is why the flag file is
empty — its only job is to exist under that id.

Pick the file to override with care: a weapon's damage is usually set per
item, in its `InteractionVars`, as a child (`"Parent"`) of a shared damage
file. Override that shared file and every item inherits the branch;
override the selector above it and only the items that define no vars are
reached. The example pack does this for the sword's downward strike.

Rules of thumb:

- **No `StatusEffectIcon`, no capsule in the HUD.** Vanilla's own passives
  (`Immunity_Poison`, `Immunity_Fire`) have none. Set one only for an
  effect the player should see; `"Debuff": true` frames it as a malus.
- **For a stat bonus, use `Stat`, not `RawStatModifiers`.** Hytale adds
  the multiplicative modifiers of every active effect together, without
  the product rule `Stat` applies between talents.
- **One instance per effect id.** Two talents that name the same id give
  one effect, not two, and losing either talent removes it for both until
  the next sync. Two talents with regeneration files of their own, each
  +1 every 2 s, do add up to +2: every active effect ticks on its own.
  If a potion or food applies the same id as a talent, they share it too.
  Give each talent an effect file of its own.
- `ApplyConditions` in the file are honoured: while they refuse, the
  effect is not placed, and the next sync tries again.

### How it is applied

Vanilla clears every entity effect when a player dies and again when they
respawn; the talent effects are put back on respawn. They are saved with
the player like any effect, so a reconnection changes nothing. The mod
also keeps, on the player, the list of ids it placed: a talent removed
from a graph while the player was away has its effect taken back at their
next world entry, and an effect whose file disappeared is dropped by the
server itself. Syncs happen at the same moments as for `Stat` — world
entry, unlock, reset, graph load, reload or removal — plus respawn.

## Equipment

Which items a player may use. Two parts: the **baseline** on the graph,
and **talent effects** that open or close classes of items.

### Baseline, on the graph

```json
"Equipment": {
    "Forbidden": ["Type=Weapon", "Type=Armor"],
    "Allowed":   ["Family=Sword", "Armor_Leather_Light_Chest"]
}
```

| Field | Values |
|---|---|
| `Forbidden` | Item lists (see below). What is unusable for everyone until a talent allows it. |
| `Allowed` | Item lists. Exceptions cut into `Forbidden`; wins over it at this level. |

An item that matches neither is free. Three intentions: everything blocked
at the start (`Forbidden: ["Type=Weapon"]`); nothing blocked, talents only
close things (no `Equipment` section); everything blocked except some
(`Forbidden` + `Allowed`). With several graphs loaded, the lists are
unioned.

### Talent effect

```json
{ "Type": "Equipment", "Mode": "Allow",  "Items": ["Family=Sword", "Family=Axe"] }
{ "Type": "Equipment", "Mode": "Forbid", "Items": ["Family=Bow", "Family=Crossbow"], "Priority": 1 }
```

| Field | Required | Values | Default |
|---|---|---|---|
| `Mode` | yes | `Allow` or `Forbid`. | — |
| `Items` | yes | Item list, at least one entry that resolves. | — |
| `Priority` | no | Integer. Between talents, the highest wins. | 0 |

Resolution for one item, in order:

1. Among the rules of unlocked talents that name the item (all graphs,
   `FromRank` respected), the highest `Priority` wins, `Allow` or `Forbid`
   alike — a talent meant to override everything gets a high priority.
2. On a tie, a rule naming the item **by id** beats one naming a **tag**
   (`Armor_Iron_Chest` beats `Family=Iron`); tags are flat, there is no
   other level of specificity.
3. Still tied, `Forbid` wins: a restriction is the stronger commitment.
   Give the allowing talent a `Priority` if you mean the opposite.
4. No talent rule names the item: the baseline speaks, `Allowed` then
   `Forbidden`.
5. Nothing names it: the item is free.

An `Allow` forbids nothing by itself; it opens what the baseline or a
`Forbid` talent closes. An `Allow` that names nothing any `Forbidden`
entry or `Forbid` talent of the same graph names gets a warning: it
changes nothing. (A `Forbid` in *another* graph is not seen by that check.)

### Item lists

Each entry is either an **item id** (`Weapon_Sword_Wood`) or a **tag** the
items carry. Hytale items declare tags as `"Tags": {"Type": ["Weapon"],
"Family": ["Sword"]}`; each key and value is usable as `Key=Value`
(`Type=Weapon`, `Family=Sword`) or as the bare value (`Weapon`, `Sword`).
An entry that is neither a known item id nor a tag carried by at least one
item is skipped with a warning. Tags you add to your own items work the
same way.

Vanilla tags worth knowing (0.6.5 assets; a family applies to every item
of that kind, whatever its material):

| Tag | Covers |
|---|---|
| `Type=Weapon` | All weapons, including shields and bombs. |
| `Type=Armor` | All armour pieces. |
| `Type=Tool` | Pickaxes, hatchets, hammers, shovels… (no family tag). |
| `Family=Sword`, `Family=Longsword`, `Family=Dagger`, `Family=Axe`, `Family=Mace`, `Family=Club`, `Family=Spear`, `Family=Staff`, `Family=Wand`, `Family=Spellbook`, `Family=Shield`, `Family=Bow`, `Family=Crossbow`, `Family=Gun`, `Family=Bomb`, `Family=Arrow`, `Family=Stick`, `Family=Magic` | Weapon families. `Family=Bow` covers shortbows; `Family=Dagger` covers daggers and claws; `Family=Arrow` covers arrows and darts. |
| `Family=Copper`, `Family=Iron`, `Family=Steel`, `Family=Bronze`, `Family=Cobalt`, `Family=Mithril`, `Family=Adamantite`, `Family=Thorium`, `Family=Onyxium`, `Family=Prisma`, `Family=Leather`, `Family=Cloth_Cotton`, `Family=Cloth_Linen`, `Family=Cloth_Wool`, `Family=Cloth_Silk`, `Family=Diving`, `Family=Kweebec`, `Family=Trork`, `Family=Trooper` | Armour families, by material. Armour pieces have no tag for their slot: to target chests only, list ids (`Armor_Iron_Chest`). |

Pitfalls: battleaxes carry `Type=Weapon` but **no family tag** — list them
by id. Avoid the bare key `Type` or `Family` as an entry: it matches every
tagged item.

### What forbidden means

Possession is never touched: a forbidden item can be picked up, carried in
the hotbar, sold, dropped or given. Using it is what is blocked:

- **In hand or in the off hand**: the item stays where it is, visible, and
  the keys it would answer are redirected to a refusal that shows a short
  HUD notification. Which item answers a key is decided as vanilla does:
  the item in hand, except that right click goes to the off-hand item when
  the weapon in hand is one-handed (`Utility.Compatible`: a sword, not a
  staff), an item with a higher declared priority for a key takes it, and
  an empty hand lets the off-hand item answer every key. No swing, no
  arrow, no ability. The hotbar is never reordered.
- **Its stat modifiers are stripped**: a forbidden sword or bow fills no
  signature energy, a forbidden crossbow gives no ammo — the item is worn
  like a stick. Vanilla's own armour and effect modifiers are untouched.
- **Armour**: a forbidden piece is refused at the slot, by drag or by
  shift-click, with the same notification. A piece already worn when it
  becomes forbidden (a reset, a graph edit) is taken off and put back in
  the bag following the player's pickup settings; if the bag is full it is
  dropped on the ground. Nothing is ever destroyed.
- **Creative mode** is exempt: no restriction at all, and the rules come
  back on the return to adventure.
- **What cannot be done**: greying the item out in the inventory. Item
  tooltips are built on the client from the assets, the same for every
  player. The player learns of a restriction at the refusal and in the
  talent's tooltip.

### The refusal message

The refused keys run the root interaction `TalentGraph_Denied`
(`Server/Item/RootInteractions/TalentGraph/TalentGraph_Denied.json`),
which has a 1.5 s cooldown so a held click does not flood, and runs
`Server/Item/Interactions/TalentGraph/TalentGraph_Denied.json`:

```json
{ "Type": "TalentGraph_Notify", "Message": "Your talents do not let you use this" }
```

`TalentGraph_Notify` is an interaction type this mod adds: a HUD
notification to the player running it (vanilla's `ShowEventTitle`
addresses a whole world, `SendMessage` is a chat line). To change the
text, or replace it with a sound, an animation or anything else, ship a
pack with a file at the same path: it overrides this one. The armour
notification ("Your talents do not let you wear …") is not an
interaction and is not replaceable.

### How it is applied

The engine reconciles the player at world entry, at every rank change,
after a graph reload, on respawn, and when the item in hand, the off-hand
item, the armour or the game mode change. Each pass:

- Resolves the item in hand and in the off hand, and writes the refusal
  under the player's `Interactions` component (the same one vanilla and
  other plugins use to override keys), remembering in
  `TalentGraphApplied.Interactions` which keys it wrote. It only takes a
  key that is free or already its own, and only gives back one that still
  holds what it wrote; a key of its own rewritten by someone else is
  forgotten, not touched. The component is created on the first key and
  removed when it becomes empty.
- Marks the player (`HeldItemDenied`, transient) so that a ticking system
  strips the item's stat modifiers right after vanilla recomputes them,
  before anything is sent to the client.
- Replaces the four armour slot filters with vanilla's check plus the
  rules, then returns the worn pieces the rules refuse.

The `Interactions` override is saved with the player: after a crash the
refusal stays in place until the next world entry corrects it. With the
mod removed, the server drops the override itself, as the root interaction
it names no longer exists.

## Ability

Binds a Hytale root interaction to one of the player's keys, optionally
only while a matching item is held.

```json
{ "Type": "Ability", "Slot": "Ability2", "Interaction": "MyPack_Root_Heal",
  "HeldItem": ["Family=Staff", "Family=Mace"] }
{ "Type": "Ability", "Slot": "Primary", "Interaction": ["MyPack_Bolt_1", "MyPack_Bolt_2", "MyPack_Bolt_3"],
  "HeldItem": ["Family=Staff"] }
```

| Field | Required | Values | Default |
|---|---|---|---|
| `Slot` | yes | `Primary`, `Secondary`, `Ability1`, `Ability2`, `Ability3`, `Pick`. | — |
| `Interaction` | yes | A `RootInteraction` id — a file `Server/Item/RootInteractions/<Id>.json` in your pack — or an array with one id per rank, the last repeating. Only the id of the current rank is bound. | — |
| `HeldItem` | no | Item list (as for `Equipment`). The ability is only bound while the judged item matches. Absent: always bound, empty hand included. | always |
| `Priority` | no | Integer, see [Several abilities on one key](#several-abilities-on-one-key). | 0 |

The ability itself is written with Hytale's interaction toolkit
(`ChangeStat` for a heal, `Selector` + `DamageEntity` for a nova,
`ApplyEffect`, `LaunchProjectile`, `ApplyForce`…); nothing about what it
does is described in the graph, and this mod invents no spell language.
The dev pack's `Mage.json` binds all six keys with vanilla bricks only:
`Server/Item/RootInteractions/TalentGraph/Mage/` and
`Server/Item/Interactions/TalentGraph/Mage/` are a worked example of every
recipe below.

### Keys

The server never sees a key press: the client starts an interaction chain
of a given type, and the server runs what that type is bound to. These
are the types a key starts, with the default binding (QWERTY position;
AZERTY in brackets):

| Slot | Default key | In vanilla |
|---|---|---|
| `Primary` | left click | The held item's attack. Binding it replaces the attack while the ability applies. |
| `Secondary` | right click | The held item's secondary (guard, aim…). Judged on the item vanilla would run: the **off-hand item when the weapon in hand is one-handed** (a sword and a shield), the item in hand otherwise (a two-handed staff keeps right click whatever the off hand holds). |
| `Ability1` | Q (A) | The weapon's **signature**. Binding it with a `HeldItem` replaces that weapon's signature; without one, every weapon's — the loader warns. |
| `Ability2` | E | Free: no vanilla item uses it. The natural key for a class ability. |
| `Ability3` | R | Free, except the crossbow's reload. Empty-handed it is "use block or entity", a duplicate of `Use`. The loader warns when a `HeldItem` covers an item that defines its own `Ability3`. |
| `Pick` | middle click | Pick block, useful in creative only. The ability's root interaction can keep that behaviour in creative with `Condition` + `RequiredGameMode` (see `Mage_Focus`). |

**Two-step vanilla abilities.** Some signatures use two keys: the flame
staff's Q only arms a trap (it sets the stat `DeployablePreview`, which
the client draws on the ground), and the *left click* places it — its
`Primary` root starts with `StatsCondition Costs { DeployablePreview: 1 }`
before falling back to the fireball. Replace `Primary` on such an item
and the trap can no longer be placed; worse, a preview armed earlier
stays on the ground for good, since nothing consumes the stat any more.
Before binding `Primary` or `Ability1` on a family, read the roots of
its items: what one key starts, another may finish.

`Use` (F) is deliberately not a slot: it opens doors, talks to NPCs and
harvests, **with a weapon in hand too** (every item is completed with the
unarmed defaults). The hotbar keys 1–9 change the selected slot on the
client's authority and cannot be bound to anything; a spell per number key
is a spell *item* in the hotbar, as vanilla's wands and spellbooks — see
`GrantItem` when it lands.

More spells than keys, without a protocol change: a `Condition` at the
top of the root interaction (`Crouching`, `Running`, `Jumping`,
`Swimming`, `Flying`, `RequiredGameMode`) makes crouch + key a second
spell, predicted by the client; `Charging` distinguishes tap from hold;
`RunRootInteraction` wraps the item's vanilla ability instead of replacing
it; an id per rank makes the spell grow.

### Costs, cooldowns, ammunition

All vanilla, all inside the root interaction, word for word the wands'
recipe (`Wand_Cast_Left_Charged`):

```json
{
  "Type": "StatsCondition",
  "Costs": { "Mana": 25 },
  "Failed": "TalentGraph_NoMana",
  "Next": {
    "Type": "Serial",
    "Interactions": [
      { "Type": "ChangeStat", "StatModifiers": { "Mana": -25 } },
      { "Type": "LaunchProjectile", "ProjectileId": "Fireball" }
    ]
  }
}
```

- `StatsCondition` refuses when the stat is short and runs `Failed`;
  `ChangeStat` pays. Mind each stat's scale (see the [vanilla
  stats](#vanilla-stats) table): `Stamina` runs from 0 to **10**, `Mana`
  to what opens it, `SignatureEnergy` to what the **wielded weapon**
  declares (sword 20, mace 8, most staffs nothing at all) and it only
  fills through the weapon's own hits (`EntityStatsOnHit`). An ability
  that replaces a weapon's attack cuts that supply: give the talent its
  own reserve (`{ "Type": "Stat", "Stat": "SignatureEnergy", "Amount":
  10 }`) and charge it from the ability (`ChangeStat` with a positive
  amount), as the dev pack's `nova` and bolts do. Stats worth using:
  `Mana`, `Stamina`,
  `SignatureEnergy` (with `"ValueType": "Percent"` and `Costs 100` for a
  signature-like ability), `MagicCharges`, `SignatureCharges`.
- `Mana` has a **maximum of 0** in vanilla: it is opened by items
  (silk cloth armour) or by a `Stat` effect — `{ "Type": "Stat", "Stat":
  "Mana", "Amount": 50 }` is the "awakening" talent every mage graph
  starts with.
- `Cooldown` on the root interaction (`{ "Id": "...", "Cooldown": 8 }`)
  keeps the chain from starting again for that long; `RequireNewClick`
  stops a held button from repeating. `CooldownCondition` +
  `TriggerCooldown` put a cooldown on one branch only.
- `ModifyInventory` with `ItemToRemove` consumes an item (ammunition,
  reagent); `AdjustHeldItemDurability` wears the held item.
- `TalentGraph_NoMana` and `TalentGraph_NoStamina`
  (`Server/Item/Interactions/TalentGraph/`) are ready-made `Failed`
  branches: a HUD notification, overridable by a pack file at the same
  path like `TalentGraph_Denied`.

### Several abilities on one key

Only one root interaction can be bound to a key at a time. The judged
item is the one vanilla would run for that key (see the `Secondary` row
above; an empty hand with a shield is judged on the shield for every key).
Among the unlocked talents whose ability is on the same slot and applies
to it (no `HeldItem`, or one entry matching), the engine picks, in order:

1. the highest `Priority`;
2. the talent that **requires** the other, directly or through others —
   the deeper talent is the stronger version;
3. the more specific `HeldItem` match: an item id over a tag over no
   `HeldItem`;
4. file order, then graph id.

So a power-up is written as **ranks** (`"Interaction": ["Bolt_1",
"Bolt_2"]`) or as a talent that `Requires` the weaker one: it wins by
itself. A specific item beats a family: `battle_mage` on `Ability1` with
`Family=Sword` next to `nova` on `Ability1` with `Family=Staff` never
collide, and an ability on `Weapon_Staff_Cobalt` beats one on
`Family=Staff` when that staff is held.

What is **not** resolved is two distinct spells a player can hold at once
on the same key, with `HeldItem`s that name a common item (or none) and the
same `Priority`: the loader warns on the graph, because file order would
decide in game and the player would never know why. Remedies: give them
disjoint `HeldItem`s by item id, different keys, make one require the
other, or, when spell items exist, a grimoire per school. The engine
never chains two spells on one key, nor picks at random.

The equipment rules win over everything: an item the player may not use
keeps its refusal on every key, whatever ability is bound there.

### How it is applied

Same pass as `Equipment` (world entry, rank change, reload, respawn, item
in hand, off hand, armour or game mode change): the engine reads the item
in hand and in the off hand, decides which one each key runs as vanilla
does, resolves the six slots on it, merges the result
with the equipment refusal — the refusal taking its keys — and writes the
whole under the player's `Interactions` component in one go, with the
same ownership rules (only free or own keys, only own keys given back,
recorded in `TalentGraphApplied.Interactions`). The component is
replicated to the client, which starts the chain for the bound root
interaction as it would the item's own, and is saved with the player;
after a crash the binding stays until the next world entry corrects it,
and with the pack removed the server drops the unknown ids itself.
Abilities are bound in creative mode too; only the equipment rules are
exempt there. A dead player has none; they come back at respawn.

## Movement

Modifies one of the player's movement settings.

```json
{ "Type": "Movement", "Setting": "JumpForce", "Amount": 1.1, "Calculation": "Multiplicative" }
```

| Field | Required | Values | Default |
|---|---|---|---|
| `Setting` | yes | One of the settings below. Nothing else. | — |
| `Amount` | yes | Number or per-rank array. | — |
| `Calculation` | no | `Additive` or `Multiplicative`. Multiplicative is the sensible one for every setting here. | `Additive` |

Speed settings shift the balance of combat and exploration for everyone
the player meets; prefer small values (×1.05, ×1.1) and jump height over
run speed.

Vanilla values are those of a player on server 0.6.5.

| Setting | Meaning | Vanilla |
|---|---|---|
| `JumpForce` | Upward impulse of a jump: jump height. | 11.8 |
| `SwimJumpForce` | Same, out of water. | 10.0 |
| `BaseSpeed` | Ground speed everything else multiplies. Affects the balance the most. | 5.5 |
| `Acceleration` | How fast the player reaches speed. | 0.1 |
| `ForwardSprintSpeedMultiplier` | Sprint speed, as a multiple of running. | 1.65 |
| `ForwardRunSpeedMultiplier`, `BackwardRunSpeedMultiplier`, `StrafeRunSpeedMultiplier` | Running speed by direction. | 1.0, 0.65, 0.8 |
| `ForwardWalkSpeedMultiplier`, `BackwardWalkSpeedMultiplier`, `StrafeWalkSpeedMultiplier` | Walking (slow mode) speed by direction. | 0.3, 0.3, 0.3 |
| `ForwardCrouchSpeedMultiplier`, `BackwardCrouchSpeedMultiplier`, `StrafeCrouchSpeedMultiplier` | Crouching speed by direction. | 0.55, 0.4, 0.45 |
| `AirSpeedMultiplier` | Horizontal speed while airborne. | 1.0 |
| `AirControlMaxMultiplier` | How much the player can steer in the air. | 3.13 |
| `ClimbSpeed`, `ClimbSpeedLateral` | Climbing speed, up and sideways. | 0.035, 0.035 |
| `ClimbUpSprintSpeed`, `ClimbDownSprintSpeed` | Climbing speed while sprinting. | 0.045, 0.055 |
| `RollTimeToComplete` | Duration of the landing roll, in seconds; lower is faster. | 0.9 |

Not supported, on purpose: fall damage mitigation (a server-side config,
not a per-player setting) and extra jumps (carried by the boots' item
definition, not by the player).

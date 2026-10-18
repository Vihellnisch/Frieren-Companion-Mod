# Frieren Companion Mod

A fan-made Minecraft mod that brings **Frieren from *Frieren: Beyond Journey's End*** into your world as a companion. Befriend her, travel together, learn her magic, and let her get eaten by a Mimic now and then.

Unofficial and fan-made. Not affiliated with or endorsed by the creators or publishers of Frieren.

- **Minecraft** 1.21.1, **NeoForge** 21.1+
- **Requires** [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) 4.7+

## What's in it

- **Frieren as a companion:** a relationship that grows from Stranger to Friend, Ally and Partner. She follows you, fights alongside you, heals you, trades, keeps a bag and her own armor, sleeps when you sleep, and comes back to her bed if she falls.
- **Magic:** she teaches you spells for the Frieren Staff, from Zoltraak to Midas.
  - Press **H** for the spellbook: pick up to eight spells for the staff's wheel and put them in order.
  - **Spell fusions:** combine two spells in the fusion circle to discover new ones, like Homing Blast, Golden Zoltraak or Butterfly Bombs.
- **Useless spells,** just like hers: Flower Field, Rust Removal, Create Water, Create Torch, Grow Grass and Golden Butterflies. The Flower Field spell grows modded flowers too; a config blacklist keeps progression flowers out.
- **Mimics:** a few loot chests in structures are Mimics. They sit still until you get close. Frieren can't resist them: she dives in head-first, gets swallowed ("It's dark! I'm scared!"), and you push her deeper until it spits her out. They drop the chest's loot, and a grimoire 1% of the time.
- **Head pats:** sneak and right-click her with an empty hand. She bows a little so you can reach.
- **Quests and moments:** grimoire hunts, Himmel's statue, deep-sleep gifts, flowers she picks, a promise ring.

## Config

`config/frierenmod-common.toml`:

- `mimicChance`: how many structure loot chests are Mimics (default 0.08).
- `frierenChestCuriosity`: whether Frieren pokes her head into ordinary chests.
- `flowerFieldBlacklist`: flowers the Flower Field spell never grows (block ids or `#tags`).

## Building

```
./gradlew build
```

The jar ends up in `build/libs/`.

A scripted in-game screenshot tour lives in `src/tour` and doesn't go into the jar:

```
./gradlew runTourClient
```

The `generate_*.py` scripts make the textures, the Mimic model and the extra animations.

## Credits

- Mod by **Vihellnisch**.

## License

The code and the mod's own art are under the [MIT License](LICENSE). The voice clip from the anime is not; see the license file.

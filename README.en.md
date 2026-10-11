# MineCities

*[Version française](README.md)*

Living towns for Minecraft 26.2 (Fabric), inspired by MineColonies but vanilla-friendly: no imposed
blueprints and no prefabricated huts. Build however you like, the mod recognises your houses, and
citizens come to live in them. The village grows into a town, then a city, then a megalopolis. The
whole mod is translated into French and English.

## Playing

1. **Found a town**: craft a **town hall** and place it; you become the mayor. Rename it in an anvil
   first to choose the town's name. Founded in the middle of a vanilla village, the town takes it
   over: villagers keep their trades and count towards the population, their houses become town
   houses, and jobless villagers gradually become citizens. A player can run several towns.
2. **Build a house** with any blocks: an enclosed room, at least one **door** and one **bed** (one
   citizen per bed). Hang a **house plaque** on an inside wall: the house is surveyed and
   registered, or a message tells you what is missing.
3. **The town hall** opens a **screen**: town summary, citizens (job, hunger, family, needs), the
   **registry** of marriage requests, the **buildings**, and the shared **granary**; the town's
   average happiness, and a "Find" button that makes a citizen glow. Entering a territory shows a
   welcome title; holding a town hall or a plaque draws its border in particles. A plaque's door turns
   green once its building is registered, red if it was refused.
   **Population**: the town gets its **five first inhabitants**; below five, newcomers arrive. Beyond,
   it only grows through **births**: citizens who live and work together grow fond of each other,
   those who get along ask the mayor to **marry** them; married couples move in together and, well
   fed with a free bed at home, may have a **child** who grows up in three days. Citizens have a
   gesture for each action (hoe, axe, rod, building, cooking, talking, cheering).
4. **Citizens** stroll by day, sleep in their bed at night (not where beds explode), eat when hungry
   (chests of their house first, then the granary) and can starve to death. They speak: a speech
   bubble above their head, and a line in the chat of nearby players (missing tools or materials,
   hunger…). Right-clicking a citizen repeats what they still need, and gives their mood. Each job
   has its own hat, and an icon above the head tells what is wrong (hungry, homeless, unhappy, hurt).
   **Happiness** (0–10) depends on food, housing, work, family, health and safety. Citizens remember
   their **last 5 meals**: the same food over and over feeds less and bores them, a varied diet (and
   fine dishes) makes them happy, and bigger towns expect more variety. Happy citizens work faster and
   can have children; miserable ones leave after a day.
5. **Jobs**: a vanilla workstation in a declared building creates a job.
   - **Composter → farmer** (needs a hoe): harvests and replants fields, delivers the harvest.
   - **Smoker → cook**: cooks raw food, bakes bread and cookies, makes soups, rabbit stew and
     pumpkin pie when the granary has the ingredients.
   - **Stripped log standing alone → lumberjack** (axe): fells nearby trees and replants.
   - **Hay bale standing alone → breeder**: breeds animals, culls the surplus.
   - **Barrel by the water → fisherman** (fishing rod).
   - **Crafter → builder** (pickaxe, shovel, axe to clear the ground): builds ordered constructions,
     from the ground and from scaffolding he sets up and takes down. Several builders share a site.
   - **Cartography table → courier**: brings builders the materials they call for.
   - **Grindstone → guard** (sword, armour from the granary): patrols, watches at night, fights.
   - **Fletching table → archer** (bow, arrows): shoots from the highest point near the table.
   - **Brewing stand → doctor**: heals the injured and feeds the starving.
   - **Lectern → merchant** (from Town on): buys the tools workers ask for, arrows, or bread with
     the granary's emeralds; otherwise sells the granary's surplus at villager rates (32 emeralds a
     day at most).
   - **Smithing table → inventor**: when the town is attacked, builds an **iron golem** (4 iron
     blocks, a carved pumpkin) or a **snow golem** (2 snow blocks, a pumpkin) in front of the hall.
6. **Have buildings built**: `/ville build <blueprint> [style]` (plains, taiga, desert, savanna)
   places the building in front of the mayor; `/ville upgrade` enlarges it (flat roofs first, gabled
   stair roofs and fine stone for upgrades). Small house → large house → manor; each workshop → a
   large workshop with two workstations. For defence: the **barracks**, the **guard tower** and the
   **inventor's workshop**; for health, the **infirmary**; from Town on, the **market**. Datapacks can add blueprints (`data/<ns>/blueprint/`).
7. **Grow**: village (0) → town (10 inhabitants, 4 houses) → city (30, 12) → megalopolis (75, 30).
   Each tier unlocks things (the market at Town); a City can place 2 **town hall annexes** (4 for a
   megalopolis) that extend the territory by 48 blocks around them and hold a district granary. The
   **treasury** is the emeralds in the granary.
8. **Raids**: towns of 8 inhabitants or more attract pillagers. At dusk, at most every three days,
   a raid may come from the edge of the territory, vanilla style: boss bar, waves of pillagers and
   vindicators marching on the town hall, more waves (up to 7) for bigger towns. Civilians take
   shelter, guards rally at the hall, archers shoot from their towers, the inventor builds golems.
   Raiders reaching the hall loot the granary; a victory leaves emeralds in it and starts a festival.
9. **Alarm bell**: ringing a bell in the territory raises the alarm for two minutes (civilians take
   shelter, guards rally, nearby monsters glow); ringing it again ends it.
10. **Festivals**: the mayor holds one (`/ville festival` or the screen button) for 4 emeralds, at
   most every three days: citizens dance under fireworks and stay happier for a day.

## Commands (`/minecities` or `/ville`)

`info`, `list`, `citizens`, `rename <name>`, `mayor <player>`, `deputy add|remove <player>`,
`blueprints`, `build <blueprint> [style]`, `build cancel`, `upgrade`, `festival`, `marriages`,
`marry accept|refuse <n>`; operators: `admin
immigrate|feed|starve|raid|raid_stop|festival|tier <tier>|auto`.

## Development

Java 25 (Loom 1.18.3, Minecraft 26.2). `./gradlew build` (unit tests), `./gradlew
runClientGameTest` (in-game scenes, screenshots in `build/run/clientGameTest/screenshots`),
`-PminecitiesGametest=city,work,countryside,village,build,family,raid,life,market` to pick scenes. Textures, the town hall
model and the blueprints are generated by the scripts in `tools/`. Design documents (in French):
[BRIEF.md](BRIEF.md) and [docs/briefs](docs/briefs). Licence: MIT.

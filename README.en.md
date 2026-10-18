# MineCities

*[Version française](README.md)*

Living towns for Minecraft 26.2 and 26.3 (Fabric), inspired by MineColonies but vanilla-friendly: no imposed
blueprints and no prefabricated huts. Build however you like, the mod recognises your houses, and
citizens come to live in them. The village grows into a town, then a city, then a megalopolis. The
whole mod is translated into French and English.

## Playing

On their first visit, every player receives the **town guide**, a book that explains everything
below, with a clickable table of contents (`/ville guide` gives another; recipe: a book and a plank).
A bug, an idea, some feedback? `/ville bug` opens the
[report form](https://ajustor.github.io/minecities/report.html), pre-filled with the mod version,
your other mods and your playtime.

1. **Found a town**: craft a **town hall** and place it; you become the mayor. Rename it in an anvil
   first to choose the town's name. Founded in the middle of a vanilla village, the town takes it
   over: villagers keep their trades and count towards the population, their houses become town
   houses, and jobless villagers gradually become citizens. A player can run several towns.
2. **Build a house** with any blocks: an enclosed room, at least one **door** and one **bed** (one
   citizen per bed). Hang a **house plaque** on an inside wall: the house is surveyed and
   registered, or a message tells you what is missing.
3. **The town hall** opens a **screen**: town summary, citizens (job, hunger, family, needs), the
   **registry** of marriage requests, the **buildings**, and the shared **granary**; the town's
   average happiness, and a "Find" button that makes a citizen glow. The **Map** button shows the
   territory flat, like a map: the town hall's and annexes' discs, each building in its kind's colour,
   and building sites; scroll to zoom, drag to move, click a building to make it glow in the world. Entering a territory shows a
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
5. **Jobs**: a vanilla workstation in a declared building creates a job. Every job also has its
   **mod station** (farmer's bench, builder's bench, merchant's counter…) that works like the
   vanilla block: craft it from a crafting table and an item of the trade, and right-click it for
   its **interface** (what the job does, whether the station is in use and why not, its worker and
   what they lack, "Find", and the builder's sites with their progress). On founding, the mayor
   gets the **builder's bench**: placed in a declared house, it gives the town its first builder.
   - **Composter → farmer** (needs a hoe): harvests and replants fields, delivers the harvest.
   - **Smoker → cook**: cooks raw food, bakes bread and cookies, makes soups, rabbit stew and
     pumpkin pie when the granary has the ingredients.
   - **Stripped log standing alone → lumberjack** (axe): fells nearby trees and replants.
   - **Hay bale standing alone → breeder**: breeds animals, culls the surplus.
   - **Barrel by the water → fisherman** (fishing rod).
   - **Crafter → builder** (pickaxe, shovel, axe to clear the ground): builds ordered constructions,
     from the ground and from scaffolding he sets up and takes down. Several builders share a site.
   - **Cartography table → courier**: brings builders the materials they call for; otherwise shuttles
     workshop output to the warehouse and stocks each workshop with what its trade consumes.
   - **Carpenter's bench → carpenter**: planks, sticks, bowls, and the doors, fences, stairs, slabs and
     trapdoors builders ask for.
   - **Grindstone → guard** (sword, armour from the granary): patrols, watches at night, fights.
   - **Fletching table → archer** (bow, arrows): shoots from the highest point near the table.
   - **Brewing stand → doctor**: heals the injured and feeds the starving. He also treats the
     **sick** with their remedy from the stores: honey for a **cold**, milk for **dysentery**, a
     glistering melon slice for **fever**.
   - **Diseases**: out in the rain without a **coat**, citizens catch a cold (more rarely a fever);
     rotten flesh or poisonous food (which the starving eat when nothing else is left) gives
     dysentery. Each disease incubates, then shows (☣ above the head), spreads to neighbours, lowers
     mood and slows work; fever keeps citizens in bed. Nobody dies of it; they recover with time, much
     faster once treated, and are immune for two days. The **coat** (leather and wool, or made by the
     weaver) is taken from the granary, or given with a right click.
   - **Lectern → merchant** (from Town on): buys the tools workers ask for, arrows, or bread with
     the granary's emeralds; otherwise sells the granary's surplus at villager rates (32 emeralds a
     day at most).
   - **Furnace → baker**: bread, cookies and pies (the cook then keeps meat, fish, soups, stews).
   - **Blast furnace → smith** (from Town on): forges the tools workers ask for (iron, or stone), iron
     chestplates for guards, and smelts raw iron, gold and copper.
   - **Mine winch → miner** (pickaxe): digs a real gallery east of the mine (a staircase down to ore
     depth, then corridors), follows veins, lights it with torches, brings ores back; never into water,
     lava, under houses or outside the territory. The winch (right-click) shows what the shaft can
     yield and has yielded; before going down, the miner checks and repairs the way back up, and won't
     go down if it is cut. With no ore left in reach the shaft is worked out, and the mayor seals it
     from the winch (not while ore remains, nor on anyone below).
   - **Jeweler's bench → jeweler** (from Town on): amethyst brooches, gold rings and diadems, sold at
     2, 5 and 12 emeralds by the merchant.
   - **Stonecutter → mason**: bricks, stone bricks, polished andesite.
   - **Glass kiln → glassblower**: glass, panes, white stained panes.
   - **Loom → weaver**: wool from string, beds.
   - **Beehive → beekeeper** (shears): harvests full hives (honey bottles or honeycomb).
   - **Chiseled bookshelf → librarian** (from Town on): books; a library makes the whole town happier
     and children grow up in two days instead of three.
   - **Tanning rack → tanner**: leather from rabbit hides, leather tunics for guards.
   - **Smithing table → inventor**: when the town is attacked, builds an **iron golem** (4 iron
     blocks, a carved pumpkin) or a **snow golem** (2 snow blocks, a pumpkin) in front of the hall.
6. **Have buildings built**: take a **blueprint** at the town hall (**Plans** tab, with its style:
   plains, taiga, desert, savanna) and use it on the ground, or type `/ville build <blueprint> [style]`:
   the building's **ghost** appears in front of the mayor, translucent, for nearby players too. Sneak
   with the blueprint (or `/ville build rotate`) to turn it, `/ville build confirm` (or the chat button)
   to order it, `/ville build cancel` to drop it at no cost; the blueprint can be reused. `/ville upgrade`, or the **Upgrade** button of the building's interface (click its plaque, or
   "Details" in the Buildings tab, which also shows the cost and what the granary has), raises it to the next of its **four
   levels**: hut (raw logs, flat roof), house (planks, stone, glass, gabled roof), hall (stone bricks,
   bricks, iron, lanterns; Town tier), mansion (polished stone, quartz, stained glass, copper, gold;
   City tier). Every building is dressed: eaves, open shutters, a beam and a dormer window in the
   gables, a stone or brick chimney with a smoking campfire, a flower pot, a carpet runner in houses. Each building is also furnished after its trade, more richly at higher
   levels (crafting table, candle tables, pots, plants, counters, cauldrons, anvils, rails, shelves…).
   Houses gain two beds per level, workshops one workstation, and workers get faster. At
   level 4 workers have master's tools: farmer and miner harvest as with Fortune II, the fisherman
   fishes as with Luck of the Sea III, the smith forges full iron armour for guards. For defence: the **barracks**, the **guard tower** and the
   **inventor's workshop**; for health, the **infirmary**; from Town on, the **market**. Datapacks can add blueprints (`data/<ns>/blueprint/`). So can you: save a building with a vanilla
   **structure block** (Save mode, door facing north, a house plaque inside so it registers itself); a few
   seconds later `/ville build <name>` orders it, e.g. `/ville build minecraft:my_house`.
6 bis. **Warehouse**: a warehouse ledger makes a declared building the town's warehouse. Food and
   emeralds go to the granary, everything else to the warehouse. Without couriers everyone walks there;
   with couriers, workers leave their output in their workshop and couriers do the trips. The smith
   also makes shears and iron blocks, the glassblower bottles, the archer fletches arrows.
7. **Grow**: village (0) → town (10 inhabitants, 4 houses) → city (30, 12) → megalopolis (75, 30).
   Each tier unlocks things (the market at Town); a City can place 2 **town hall annexes** (4 for a
   megalopolis) that extend the territory by 48 blocks around them and hold a district granary. The
   **treasury** is the emeralds in the granary. The **megalopolis** unlocks **monuments**: the
   statue, the fountain and the belfry, one of each per town, in polished stone, quartz and gold.
   Once built they make citizens happier (+1, +2 from two on) and give the town **appeal** (shown in
   the summary).
8. **Raids**: towns of 8 inhabitants or more attract pillagers. At dusk, at most every three days,
   a raid may come from the edge of the territory, vanilla style: boss bar, waves of pillagers and
   vindicators marching on the town hall, more waves (up to 7) for bigger towns. Civilians take
   shelter, guards rally at the hall, archers shoot from their towers, the inventor builds golems.
   Raiders reaching the hall loot the granary; a victory leaves emeralds in it and starts a festival.
9. **Alarm bell**: ringing a bell in the territory raises the alarm for two minutes (civilians take
   shelter, guards rally, nearby monsters glow); ringing it again ends it. **Disasters**, rare and
   optional (`disasters`): in a thunderstorm, from Town tier, lightning can set a building's roof on
   **fire**; citizens rush in with the granary's **water buckets** (keep some). A **blight** ruins the
   harvest for a day. An **earthquake** hits every town at once: ten seconds of tremors that knock
   blocks down and hurt. Fire and tremors can kill. Each disaster is announced and shown in the
   summary; at most one per town every ten days (`disaster_spacing_days`).
10. **Festivals**: the mayor holds one (`/ville festival` or the screen button) for 4 emeralds, at
   most every three days: citizens dance under fireworks and stay happier for a day.
11. **Decrees** (Council tab or `/ville decree`): rationing, overtime, holiday, each with its effect on
   food, work and mood. **Taxes**, right below: a rate (none, low, normal, high) every adult pays each
   morning into the **tax box**; the higher, the grumpier the town. Guards and archers are paid every
   morning from the granary (one emerald each): the mayor carries the money there from the box, or they
   drag their feet. Construction also costs emeralds from level 2 (4, 12, 32). The day's budget shows in
   the Council tab (`budget` setting in `config/minecities.json`).
12. **Trade between towns**: `/ville trade <town>`; once both mayors have signed, caravans carry the
   surplus of one granary to the other for emeralds.

## Configuration

`config/minecities.json` is created on first launch: newcomers (`newcomer_minimum`, `newcomer_delay`),
hunger (`hunger_day_ticks`), departures (`miserable_citizens_leave`), raids (`raids`,
`raid_min_population`, `raid_odds`, `raid_spacing_days`), `festival_cost`, the market
(`market_reserve`, `market_daily_earnings`), `protect_buildings` (off by default; for towns founded
before ranks: only the mayor, deputies and operators may break blocks of declared buildings), elections (`term_days`, `candidacy_days`, `voting_days`,
`election_spacing_days`), `hall_grace_minutes` (a destroyed town
hall can be placed back at the same spot for that long), `missing_citizen_minutes` (citizens never
seen again are struck off) and `give_guide` (every player gets the town guide on their first visit).

## Residents and ranks

Other players are **visitors**. To become a **resident**, ask to settle (`/ville join`, the button in
the Residents tab, or by sleeping in a bed of one of the town's houses); the mayor or a deputy accepts.
You can be a resident of one town at a time. In the town hall's Residents tab, the mayor sets what
residents and visitors may do: declare a house, open the granary (and annexes), break blocks of
declared buildings. By default residents declare houses and open the granary, visitors nothing; the
mayor and deputies may do everything. Towns founded before ranks keep the old rights (anyone declares
houses and opens the granary).

**Elections.** The mayor's term lasts 7 game days; when it ends, an election opens. The mayor may
also call one at any time, and residents through a petition signed by half of them; at most one
election every 3 days. One day of candidacies (the outgoing mayor runs automatically), then one day
of voting in the Election tab or with `/ville vote`. Each of the town's players votes once; citizens
too, with a chance equal to their happiness: happy ones for the outgoing mayor, others against, and
their vote weighs as much. On a tie, the tied candidates face a new vote. Alone in their town, or
unopposed, the mayor is re-elected automatically.

## Jade

With [Jade](https://modrinth.com/mod/jade) (optional), a citizen's tooltip shows their hunger and
whether they are housed, and a house plaque's tooltip shows its last survey. Each line can be turned
off in Jade's settings. Without Jade, nothing changes.

## Commands (`/minecities` or `/ville`)

`info`, `list`, `citizens`, `rename <name>`, `mayor <player>`, `deputy add|remove <player>`,
`join [town]`, `leave`, `resident [accept|refuse|remove <player>]`,
`permission resident|visitor declare|granary|break true|false`, `election [call|sign|run]`, `vote <candidate>`,
`blueprints`, `build <blueprint> [style]`, `build rotate|confirm|cancel`, `upgrade`, `festival`, `decree <decree>`,
`trade [town]`, `marriages`, `guide` (the town guide), `bug` (a pre-filled link to the report form),
`marry accept|refuse <n>`; operators: `admin
immigrate|feed|starve|raid|raid_stop|festival|tier <tier>|auto`.

## Development

Java 25 (Loom 1.18.3, Minecraft 26.2 and 26.3, one Gradle project and one jar per Minecraft version from the same
sources, see [ARCHITECTURE.md](ARCHITECTURE.md)). `./gradlew build` (both versions: unit tests and every game scene,
headless), `./gradlew :26.2:runGameTest` (the game scenes on a headless server, fast), `./gradlew :26.2:runClientGameTest`
(the same scenes in the real game, screenshots in `versions/26.2/build/run/clientGameTest/screenshots`; `-PminecitiesFilm`
for frame-by-frame films), `-PminecitiesGametest=data,budget,city,work,countryside,village,build,family,raid,life,market,robust,industry,crafts,council,styles,logistics,guide,structures,plans,jade,residents,elections,monuments,health,map,disasters,stations,mine` to pick scenes. Textures, the town hall
model and the blueprints are generated by the scripts in `tools/`; after changing `src/main/resources/data`, run
`python tools/data/migrate.py` (the data of newer Minecraft versions, in `src/overlays/`). Design documents (in French):
[BRIEF.md](BRIEF.md) and [docs/briefs](docs/briefs). Licence: MIT.

# Changelog

## 1.12.0 — 2026-10-23

### New features

- Holding the plan shows the town's border, and the ghost of a building that would stick out of it turns red.
## 1.11.1 — 2026-10-22

### New features

- The builder places blocks at any height from the foot of the site: no more scaffolding needed.

### Fixes

- The builder is no longer put back inside the ground (to suffocate there) when the site backs onto a hill.
## 1.11.0 — 2026-10-21

### New features

- The mod now has its own icon in the mod list.
- MineCities' language can be set apart from the game's, live, from Mod Menu.
## 1.10.0 — 2026-10-20

### New features

- The mod is available for Minecraft 26.3 as well as 26.2: each version has its own jar.
- The recipe book shows all of the mod's recipes (town hall, plaque, stations…), without a recipe mod.
- Town budget: a daily tax at a rate set by the mayor, paid into a tax box, wages for guards and archers, and construction that costs emeralds from level 2.
- The tavern: an innkeeper serves drinks, cheers the town up and draws new residents; the Summary tells when the next one arrives.
- Materials for level 3 and 4 buildings are made in town when construction sites ask for them (smith, carpenter, mason, weaver) or bought at the market; the merchant also supplies the miner's pickaxe and the beekeeper's shears.
- The miner keeps some andesite (for the mason's polished andesite) and gravel (for flint).
- The founding message explains growth: newcomers up to the minimum, then the tavern, and where the granary is.
- The breeder shears and milks, guards pick up mob loot, the doctor brews glistering melon, the merchant buys missing raw materials.
- The merchant also sells what the town makes (bread, dishes, honey, books, bricks, glass…), above the price of the ingredients.
- Homes count for happiness: fine house (+1, +2 at level 4), overcrowded or unlit house (−1).
- Two more decrees, curfew and open borders; decrees can be taken from the Town tier.
- New buildings, levels 1 to 4: the courier post and the archery range.
- Mastery of level-4 workshops needs a book lent by the librarian; the station screen shows its state.
- Every job has a master specialty (cake, double batch, dyed carpets, better prices, area healing, twice-as-fast work…).
- The town hall shows what weighs on happiness: in the summary, and as a tooltip on each citizen's mood.
- The town hall, map and station screens update themselves every two seconds.
- Hovering a blueprint shows the building's cost: emeralds and materials, with what the stores already hold.
- Mod items tell what they are for, and screen buttons have tooltips.
- A "Manage the town hall" screen: rename the town, appoint deputies, hand over the town hall, sign or end agreements, without commands.
- Hunger and unhappiness lower affinities; spouses who no longer get along separate.
- The /town command does the same as /ville.
- The town hall summary estimates how many days the granary food will last.
- The mayor can pause a building site and resume it later, from the builder's bench.
- Citizens no longer break blocks on land protected by a protection mod.
- Workers gain experience in their job: up to 30% faster, rank shown on their station.
- Bad Omen draws a raid on the town, a ravager joins the big waves, and a wandering trader visits every week.
- Moving up a tier also needs happiness, varied jobs and a public building (tavern, monument, library); a town that loses them goes back down.
- The mayor can switch their town's elections off (and back on); a vote in progress is then cancelled.
- Children follow their parents and go to school by the library; newlyweds meet in front of the town hall for the ceremony.
- Trade caravans now travel: a covered wagon pulled by a horse, led by the merchant, that camps at night; the route staff draws their path.
- Caravan merchants set up a stall at the edge of the town they reach and sell their home town's goods to players.
- Caravans travel under escort and can be ambushed by bandits; players can plunder a wagon, at the risk of becoming outlaws.
- Caravans now use a large covered Western wagon with turning wheels, driven by the merchant on the driver's bench; the stall is bigger and displays its goods.

### Fixes

- Craftspeople no longer get stuck returning and taking back their materials without ever working.
- Disbanding a town while its citizens are working no longer crashes the server.
- Removing the tower of an archer standing on top no longer crashes the server.
- A hopper can no longer empty the granary of a town hall or annex (it can still fill it).
- Only the mayor and deputies can tear down a town annex.
- The building screen can no longer be used to declare a house without a plaque or to overload the server.
- A citizen left far from players (at work, out of range) is no longer struck off as missing, and citizens no longer use portals.
- A citizen leaving the town returns what they carried to the granary (or leaves it on the ground) instead of making it vanish.
- The town hall is crafted with a gold ingot instead of a bell, which can't be found without a village.
- Craftspeople no longer get stuck when the ingredients of an item are stored in different chests.
- The cook and the baker gather their ingredients from every store instead of getting stuck on an incomplete chest.
- Citizens eat what they carry when pantries are empty, and those who feed the town keep working when hungry.
- The miner no longer loses ores that don't fit in their pockets, nor those mined after their pickaxe breaks.
- The merchant no longer makes what they carry vanish when the granary is full.
- Interrupted craftspeople and the smith (meal, night) resume their work instead of carrying their materials back.
- All citizens now fight fires, not just half of them.
- A citizen who gets hungry during a festival goes to eat instead of dancing until exhausted.
- A citizen who can't reach their target (behind a fence, on a roof) gives up and moves on.
- The builder no longer waits forever for a courier who doesn't come: they fetch their materials themselves.
- When the stores are full, the courier tells the mayor instead of standing stuck in front of the granary.
- A house straddling an unloaded area is no longer struck off (residents evicted, jobs lost) on every survey.
- Elections only count candidates and votes from town members, and no longer restart rounds endlessly when nobody votes.
- The mayoralty can only be handed to a resident (and not during an election), deputies are picked among residents, a misspelled tier is refused, old blueprints can no longer be ordered, and two towns no longer share a name.
- Guards' armour, caravan emeralds and items citizens set down no longer get lost (nor pile up forever).
- The builder's bench is given only once per player, raid spacing survives a server restart, and tearing down the town hall is safer.
- The builder no longer gives up fetching materials from the granary while climbing down their scaffolding.
- Refusal and missing-tool messages are accurate (no more "marriages" outside the registry).
- Paged marriage registry, vacant stations that tell when a resident is missing, founding refusal with distance and direction.
- Neighbouring towns share the ground where their territories overlap; a town can no longer be founded against an annex.
- A building site can no longer crush a declared house, a finished building, the town hall or an annex; shared walls stay.
- A damaged town in the save is no longer wiped: it is kept as is and its citizens stay.
- A building site no longer finishes with a half-placed bed or door.
- The town hall screen fits at every GUI scale and has a Close button.
- The caravan wagon's canvas is taller, so the merchant driving it no longer pokes through.

### Performance

- Less server work: spaced network requests, cached site plans, lighter guards and miners.
## 1.9.1 — 2026-10-14

### Fixes

- Mod screens no longer overflow, in French either: building screen, town hall, builder, map and guide.
## 1.9.0 — 2026-10-13

### New features

- A "Post" button sends a citizen back to their workstation (Citizens tab, station interface), onto a safe spot, without changing their job.
- The miner's shaft: its winch shows what it can yield, the miner checks and repairs the way back up before going down, and a worked-out shaft can be sealed from the winch.
- One blueprint for every building: its screen picks the building and style, and holding it shows the ongoing sites.

### Fixes

- The breeder no longer wastes wheat: two animals in love far apart in the pen walk towards each other.
- The smith brings guards the armour pieces they need himself, even when the granary is full, and no longer loses a forged piece.
## 1.8.0 — 2026-10-13

### New features

- A mod station for every job (crafted from a crafting table and an item of the trade), the builder's bench given on founding, and interfaces: a station's (its worker, what is missing, building sites) and a building's (level, beds, upgrade with its cost).
- A hand-built building can be rebuilt at level 2 of its family from its interface: the builder pulls down the old one and builds the new one in its place.
## 1.7.0 — 2026-10-13

### New features

- The farmer harvests sugar cane (leaving the base) and picks pumpkins and melons, without sowing where the fruit grows
- The cook makes the sugar for pumpkin pies from sugar cane
- Your own buildings become blueprints: save them with a structure block (Save mode), then order them with /ville build
- Before ordering a building, its translucent ghost appears: rotate it, then confirm or cancel
- The blueprint: taken at the town hall (Plans tab, any style), it is used on the ground to show the building, and can be reused
- Optional Jade support: a citizen's tooltip shows their hunger and housing, a house plaque's tooltip its last survey.
- Player residents: ask to settle in a town (/ville join, or by sleeping in one of its beds), the mayor accepts, and sets per rank who may declare a house, open the granary or break buildings (Residents tab).
- Mayor elections: at the end of the term, when the mayor calls one, or by residents' petition; candidacies, voting in the Election tab or with /ville vote, happy citizens vote for the outgoing mayor, a new round on a tie.
- Monuments: a megalopolis can build a statue, a fountain and a belfry (one of each); they make citizens happier and give the town appeal.
- Diseases: cold (rain without a coat), dysentery (rotten flesh, poisonous food) and fever (keeps citizens in bed), all contagious; the doctor treats them with honey, milk or glistering melon; new coat for citizens.
- Territory map (Map button in the town hall): the territory, annexes and buildings laid flat, with zoom; clicking a building makes it glow.
- Rare, optional disasters: lightning fires in thunderstorms (citizens put them out with the granary's water buckets), a one-day blight, and earthquakes that hit every town.

### Fixes

- Builders lend each other materials and scaffolding: a site no longer gets stuck on its last block
- Citizens no longer get stuck at a door: nobody shuts it in another's face, and a stuck citizen steps aside.
## 1.6.1 — 2026-10-12

### Fixes

- When sending a report fails, the form shows why
## 1.6.0 — 2026-10-12

### New features

- The town guide: a book given on your first visit that explains the whole mod (clickable contents, /ville guide, recipe: a book and a plank)
- /ville bug opens the website's report form, pre-filled with your version, mods and playtime
- The website has a form to report a bug or give feedback, with screenshots, logs, mod version and playtime

### Fixes

- The website fits phone screens better (form, tier table)
## 1.5.0 — 2026-10-12

### Fixes

- Citizens with full pockets pick back up what they had to drop, instead of losing it
- Builders with full pockets make room before fetching a tool or material, instead of dropping it
- Couriers only hand over what fits in the builder's pockets
## 1.4.0 — 2026-10-12

### New features

- Warehouse: a warehouse ledger makes a building the common store (the granary keeps food and treasury)
- Couriers shuttle between workshops and the warehouse, and stock workshops in advance
- New job: carpenter (carpenter's bench), for planks and the woodwork building sites need
- The smith makes shears and iron blocks, the glassblower bottles, the archer fletches arrows, the inventor carves pumpkins

### Fixes

- Builders recover the beds and doors they dismantle, and saw their logs into planks when planks run out
- Couriers no longer lose what they hold, and always hand their delivery over to the builder
## 1.3.0 — 2026-10-12

### New features

- Interiors furnished after each building's trade and level (tables, candles, counters, pots, plants…)

### Fixes

- Fixed a rare crash when a citizen's pantry empties while they eat
## 1.2.0 — 2026-10-11

### New features

- Dressed buildings: eaves, shutters, beamed gables with dormer windows, smoking chimneys, carpets and flower pots
## 1.1.0 — 2026-10-11

### New features

- Mayor's decrees: rationing, overtime, holiday, and the Council tab of the screen
- Trade between towns: agreements signed by both mayors, caravans paid in emeralds
- Master's tools at level 4: fortune for farmer and miner, luck of the sea for the fisherman, full armour for guards
- New job: tanner, at the tanning rack (new block)
- Citizens' armour is now visible, and a helmet hides the job hat
## 1.0.0 — 2026-10-11

### New features

- New jobs: jeweler (jeweler's bench), mason (stonecutter), glassblower (glass kiln), weaver (loom), beekeeper (beehive) and librarian (chiseled bookshelf)
- Jewelry (amethyst brooch, gold ring, diadem), sold dear at the market
- A library makes the town happier and children grow up in two days
- Four-level blueprints for the jeweler's workshop, masonry, glassworks, weavery, apiary and library

### Fixes

- The miner and the beekeeper fetch their tool from the granary without stopping on the way
## 0.9.0 — 2026-10-11

### New features

- Four levels for every building (hut, house, hall, mansion), ever more costly and productive
- New job: miner, at the mine winch (new block), who digs a real gallery and follows ore veins
- New job: smith (blast furnace), who smelts ores and forges tools and chestplates
- New job: baker (furnace), for bread, cookies and pies
- Hats for the merchant, baker, smith and miner
- The town hall granary has twice the room (54 slots)
## 0.8.0 — 2026-10-11

### New features

- Configuration file config/minecities.json (newcomers, hunger, raids, festivals, market…)
- Optional protection of declared buildings: only the mayor, deputies and operators may break their blocks
- A town hall destroyed by an explosion can be placed back at the same spot: the town waits ten minutes
- Citizens who vanish without dying (command, other mod) are struck off after an hour
- Building surveys are spread out over time in big towns

### Fixes

- Citizens stuck at a door during an alarm still reach their shelter
## 0.7.0 — 2026-10-11

### New features

- New job: merchant (lectern, from Town on), who buys missing tools and sells the granary surplus
- The treasury (granary emeralds) and market earnings on the town hall screen
- Each tier unlocks jobs and blueprints (the market at Town), announced with a title and fireworks
- Town hall annexes for cities: an extended territory and a district granary
- Admin command to force a town's tier
## 0.6.0 — 2026-10-11

### New features

- Citizen happiness (0 to 10): hunger, varied meals, housing, job, family, health, safety, festivals
- Citizens tire of eating the same food; the cook makes soups, stews, pies and cookies
- New job: doctor (brewing stand), and the infirmary blueprint
- Festivals held by the mayor, with dancing and fireworks; the alarm bell sends the town to shelter
- Territory welcome title, particle border, green or red plaques, icons above citizens
- A hat for each job, and the Buildings tab of the town hall screen
## 0.5.0 — 2026-10-11

### New features

- New defence jobs: guard (grindstone), archer (fletching table) and inventor (smithing table) who builds golems
- Blueprints for the barracks, the guard tower and the inventor's workshop
- Pillager raids in waves on towns of 8 inhabitants or more, with more waves as the town grows
## 0.4.0 — 2026-10-11

### New features

- Families: five first inhabitants, then the town grows through births; citizens who get along ask the mayor to marry them, couples move in together and have children who grow up
- The town hall opens a screen: summary, citizens, marriage registry (accept or refuse) and granary
- Citizens have a gesture for each action (eating, hoe, axe, fishing, building, cooking, feeding, talking, cheering)
## 0.3.0 — 2026-10-11

### New features

- Found a town with the town hall and become its mayor; declare your houses with a plaque, feed your citizens from the granary, and grow the village into a megalopolis
- A player can be mayor of several towns; starving citizens waste away until they die instead of leaving
- The town hall has its own model: a small stone, oak and spruce building topped with a bell
- Jobs: a composter or a smoker in a declared building employs a citizen; the farmer harvests and replants fields with a hoe, the cook cooks meat and bakes bread at the smoker, and everything goes to the granary
- Founding a town on a vanilla village takes it over: its villagers, who keep their trades, count towards the town's population
- In the Nether and the End, citizens don't go to bed (beds would explode there)
- New jobs: lumberjack (stripped log standing on the floor, axe), breeder (hay bale) and fisherman (barrel by the water, fishing rod)
- In a taken-over village, the villagers' houses become town houses, and jobless villagers gradually become citizens
- The builder (crafter) constructs the buildings ordered by the mayor from eight blueprints, in four styles (plains, taiga, desert, savanna), and enlarges houses; /ville build, /ville blueprints, /ville upgrade
- Buildings have a porch step in front of the door; upgrades get a gabled stair roof and a fine stone foundation (manor, large two-workstation workshops)
- The builder clears with pickaxe, shovel and axe, climbs scaffolding, works from outside, goes five times faster and shares the site with other builders; others step out of his way
- New job: the courier (cartography table) brings builders the materials they call for
- Citizens speak (bubble and nearby chat) when they lack something; right-clicking repeats their needs

### Fixes

- A second plaque in an already declared house is refused instead of competing for its beds
- Citizens open doors on their way and close them behind; they no longer get stuck outside their house


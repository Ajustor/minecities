package com.ajustor.minecities.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * La chronique de la ville : ce qui s'y est passé et pèse sur le moral de ses citoyens (les morts
 * récentes, la dernière fête), l'alerte en cours (la cloche), et les gains du marché.
 */
public final class Chronicle {
	public static final MapCodec<Chronicle> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.LONG.listOf().optionalFieldOf("deaths", List.of()).forGetter(c -> List.copyOf(c.deaths)),
			Codec.LONG.optionalFieldOf("festival_at", 0L).forGetter(c -> c.festivalAt),
			Codec.LONG.optionalFieldOf("alarm_until", 0L).forGetter(c -> c.alarmUntil),
			Codec.LONG.optionalFieldOf("market_day", 0L).forGetter(c -> c.marketDay),
			Codec.INT.optionalFieldOf("earned_today", 0).forGetter(c -> c.earnedToday),
			Codec.LONG.optionalFieldOf("earned_total", 0L).forGetter(c -> c.earnedTotal),
			Codec.STRING.optionalFieldOf("disaster", "").forGetter(c -> c.disaster),
			Codec.LONG.optionalFieldOf("disaster_until", 0L).forGetter(c -> c.disasterUntil),
			Codec.LONG.optionalFieldOf("last_disaster", 0L).forGetter(c -> c.lastDisaster),
			net.minecraft.core.BlockPos.CODEC.optionalFieldOf("fire_min").forGetter(c -> c.fireMin),
			net.minecraft.core.BlockPos.CODEC.optionalFieldOf("fire_max").forGetter(c -> c.fireMax),
			Budget.Ledger.CODEC.optionalFieldOf("ledger").forGetter(c -> java.util.Optional.of(c.ledger)),
			Codec.LONG.optionalFieldOf("served_at", -1L).forGetter(c -> c.servedAt),
			Codec.LONG.optionalFieldOf("last_raid", -1L).forGetter(c -> c.lastRaid),
			net.minecraft.core.BlockPos.CODEC.listOf().optionalFieldOf("mastered", List.of())
					.forGetter(c -> List.copyOf(c.mastered)),
			Codec.LONG.optionalFieldOf("trader_at", -1L).forGetter(c -> c.traderAt)
	).apply(i, (deaths, festivalAt, alarmUntil, marketDay, earnedToday, earnedTotal, disaster, until, last, min,
			max, ledger, servedAt, lastRaid, mastered, traderAt) -> {
		Chronicle chronicle = new Chronicle(deaths, festivalAt, alarmUntil, marketDay, earnedToday, earnedTotal);
		chronicle.disaster = disaster;
		chronicle.disasterUntil = until;
		chronicle.lastDisaster = last;
		chronicle.fireMin = min;
		chronicle.fireMax = max;
		chronicle.ledger = ledger.orElseGet(Budget.Ledger::new);
		chronicle.servedAt = servedAt;
		chronicle.lastRaid = lastRaid;
		chronicle.mastered.addAll(mastered);
		chronicle.traderAt = traderAt;
		return chronicle;
	}));

	/** Une mort pèse sur la ville pendant un jour. */
	public static final long MOURNING = 24000L;
	/** Une fête réjouit la ville pendant un jour. */
	public static final long FESTIVAL_JOY = 24000L;
	/** La fête elle-même (les citoyens dansent à l'hôtel de ville) dure deux minutes. */
	public static final long PARTY = 2400L;
	/** Pas plus d'une fête tous les trois jours. */
	public static final long FESTIVAL_SPACING = 3 * 24000L;

	private final List<Long> deaths;
	private long festivalAt;
	private long alarmUntil;
	/** Le jour (temps de jeu / 24000) des gains du marché comptés dans {@link #earnedToday}. */
	private long marketDay;
	private int earnedToday;
	private long earnedTotal;
	/** Quand le dernier raid a pris fin ; -1 : jamais. */
	private long lastRaid = -1;
	/** Les ateliers (par plaque) dont le bibliothécaire a fait des maîtres (MC-63). */
	private final java.util.Set<net.minecraft.core.BlockPos> mastered = new java.util.HashSet<>();
	/** Quand la taverne a servi pour la dernière fois (MC-58) ; -1 : jamais. */
	private long servedAt = -1;
	/** Les comptes de la ville : l'impôt, les salaires, les chantiers (MC-57). */
	private Budget.Ledger ledger = new Budget.Ledger();
	/** La catastrophe en cours ({@code fire}, {@code blight}, {@code quake} ; vide : aucune) et sa fin. */
	private String disaster = "";
	private long disasterUntil;
	/** Quand la dernière catastrophe a frappé (0 : jamais). */
	private long lastDisaster;
	/** Le bâtiment en feu. */
	private java.util.Optional<net.minecraft.core.BlockPos> fireMin = java.util.Optional.empty();
	private java.util.Optional<net.minecraft.core.BlockPos> fireMax = java.util.Optional.empty();

	private Chronicle(List<Long> deaths, long festivalAt, long alarmUntil, long marketDay, int earnedToday,
			long earnedTotal) {
		this.deaths = new ArrayList<>(deaths);
		this.festivalAt = festivalAt;
		this.alarmUntil = alarmUntil;
		this.marketDay = marketDay;
		this.earnedToday = earnedToday;
		this.earnedTotal = earnedTotal;
	}

	public static Chronicle empty() {
		return new Chronicle(List.of(), 0L, 0L, 0L, 0, 0L);
	}

	/** Les émeraudes que le marché a rapportées aujourd'hui. */
	public int earnedToday(long now) {
		return now / 24000L == marketDay ? earnedToday : 0;
	}

	/** Les émeraudes que le marché a rapportées depuis la fondation. */
	public long lastRaid() {
		return lastRaid;
	}

	public boolean mastered(net.minecraft.core.BlockPos workshop) {
		return mastered.contains(workshop);
	}

	public void master(net.minecraft.core.BlockPos workshop) {
		mastered.add(workshop.immutable());
	}

	/** Quand le marchand ambulant est passé pour la dernière fois (MC-82) ; -1 : jamais. */
	private long traderAt = -1;

	public long traderAt() {
		return traderAt;
	}

	public void traderCame(long now) {
		traderAt = now;
	}

	public void raided(long now) {
		lastRaid = now;
	}

	/** La taverne vient de servir. */
	public void served(long now) {
		servedAt = now;
	}

	/** La taverne a-t-elle servi depuis moins d'un jour ? */
	public boolean tavernOpen(long now) {
		return servedAt >= 0 && now - servedAt < 24000L;
	}

	public Budget.Ledger ledger() {
		return ledger;
	}

	public long earnedTotal() {
		return earnedTotal;
	}

	/** Le marché vient de rapporter tant d'émeraudes. */
	public void earn(long now, int emeralds) {
		if (now / 24000L != marketDay) {
			marketDay = now / 24000L;
			earnedToday = 0;
		}
		earnedToday += emeralds;
		earnedTotal += emeralds;
	}

	/** Un citoyen est mort. */
	public void death(long now) {
		deaths.add(now);
		deaths.removeIf(t -> now - t > MOURNING);
	}

	/** Les morts depuis un jour. */
	public int recentDeaths(long now) {
		return (int) deaths.stream().filter(t -> now - t <= MOURNING && t <= now).count();
	}

	public long festivalAt() {
		return festivalAt;
	}

	/** La ville a fêté quelque chose depuis moins d'un jour. */
	public boolean rejoicing(long now) {
		return festivalAt > 0 && now >= festivalAt && now - festivalAt < FESTIVAL_JOY;
	}

	/** On danse à l'hôtel de ville. */
	public boolean partying(long now) {
		return festivalAt > 0 && now >= festivalAt && now - festivalAt < PARTY;
	}

	/** Peut-on donner une fête maintenant ? */
	public boolean canCelebrate(long now) {
		return festivalAt == 0 || now - festivalAt >= FESTIVAL_SPACING;
	}

	/** Le temps (en ticks) avant la prochaine fête possible. */
	public long untilNextFestival(long now) {
		return canCelebrate(now) ? 0 : FESTIVAL_SPACING - (now - festivalAt);
	}

	public void celebrate(long now) {
		festivalAt = now;
	}

	/** La cloche a sonné l'alerte : jusqu'à quand. */
	public void alarm(long until) {
		alarmUntil = until;
	}

	public boolean alarmed(long now) {
		return now < alarmUntil;
	}

	// Catastrophes (MC-14)

	/** La catastrophe en cours, si elle n'est pas finie. */
	public java.util.Optional<String> disaster(long now) {
		return disaster.isEmpty() || now >= disasterUntil ? java.util.Optional.empty() : java.util.Optional.of(disaster);
	}

	/** La catastrophe enregistrée, même si son échéance est passée (pour l'annoncer finie). */
	public String disasterKind() {
		return disaster;
	}

	public long disasterUntil() {
		return disasterUntil;
	}

	public long lastDisaster() {
		return lastDisaster;
	}

	/** Une catastrophe frappe, jusqu'à {@code until}. */
	public void strike(String kind, long now, long until) {
		disaster = kind;
		disasterUntil = until;
		lastDisaster = now;
		fireMin = java.util.Optional.empty();
		fireMax = java.util.Optional.empty();
	}

	/** Le feu a pris dans ce bâtiment. */
	public void burning(net.minecraft.core.BlockPos min, net.minecraft.core.BlockPos max) {
		fireMin = java.util.Optional.of(min.immutable());
		fireMax = java.util.Optional.of(max.immutable());
	}

	public java.util.Optional<net.minecraft.core.BlockPos> fireMin() {
		return fireMin;
	}

	public java.util.Optional<net.minecraft.core.BlockPos> fireMax() {
		return fireMax;
	}

	/** La catastrophe est finie. */
	public void recover() {
		disaster = "";
		disasterUntil = 0L;
		fireMin = java.util.Optional.empty();
		fireMax = java.util.Optional.empty();
	}
}

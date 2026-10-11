package com.ajustor.minecities.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * La chronique de la ville : ce qui s'y est passé et pèse sur le moral de ses citoyens (les morts
 * récentes, la dernière fête) et l'alerte en cours (la cloche).
 */
public final class Chronicle {
	public static final MapCodec<Chronicle> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.LONG.listOf().optionalFieldOf("deaths", List.of()).forGetter(c -> List.copyOf(c.deaths)),
			Codec.LONG.optionalFieldOf("festival_at", 0L).forGetter(c -> c.festivalAt),
			Codec.LONG.optionalFieldOf("alarm_until", 0L).forGetter(c -> c.alarmUntil)
	).apply(i, Chronicle::new));

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

	private Chronicle(List<Long> deaths, long festivalAt, long alarmUntil) {
		this.deaths = new ArrayList<>(deaths);
		this.festivalAt = festivalAt;
		this.alarmUntil = alarmUntil;
	}

	public static Chronicle empty() {
		return new Chronicle(List.of(), 0L, 0L);
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
}

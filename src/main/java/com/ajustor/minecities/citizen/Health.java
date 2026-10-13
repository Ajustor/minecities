package com.ajustor.minecities.citizen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * La santé d'un citoyen (MC-12) : la maladie qu'il couve ou dont il souffre, et celles dont il vient de
 * guérir (il en est protégé quelque temps). Rien ici ne dépend du monde : les causes (la pluie, un
 * mauvais repas, un voisin malade) sont tirées au sort par {@link #exposed}, {@link #ate} et
 * {@link #caught}, avec le hasard qu'on leur passe.
 */
public final class Health {
	/** Guéri, il est protégé de la même maladie pendant deux jours. */
	public static final long IMMUNITY = 48000L;
	/** Sous la pluie sans manteau, à chaque contrôle : la chance d'un rhume, et celle d'une fièvre. */
	public static final double RAIN_COLD = 0.01;
	public static final double RAIN_FEVER = 0.003;
	/** La chance d'une dysenterie, après de la chair putréfiée ou un aliment empoisonné. */
	public static final double TAINTED = 0.6;

	public static final Codec<Health> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("disease", "").forGetter(h -> h.disease == null ? "" : h.disease.serializedName()),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(h -> h.since),
			Codec.LONG.optionalFieldOf("until", 0L).forGetter(h -> h.until),
			Codec.BOOL.optionalFieldOf("treated", false).forGetter(h -> h.treated),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("immune", Map.of()).forGetter(h -> {
				Map<String, Long> immune = new java.util.HashMap<>();
				h.immune.forEach((d, t) -> immune.put(d.serializedName(), t));
				return immune;
			})
	).apply(i, (disease, since, until, treated, immune) -> {
		Health health = new Health();
		health.disease = Disease.fromSerializedName(disease).orElse(null);
		health.since = since;
		health.until = until;
		health.treated = treated;
		immune.forEach((d, t) -> Disease.fromSerializedName(d).ifPresent(x -> health.immune.put(x, t)));
		return health;
	}));

	private Disease disease;
	/** Quand il l'a attrapée. */
	private long since;
	/** Quand il en guérira. */
	private long until;
	private boolean treated;
	private final Map<Disease, Long> immune = new EnumMap<>(Disease.class);

	/** Ce qu'il couve ou dont il souffre. */
	public Optional<Disease> disease() {
		return Optional.ofNullable(disease);
	}

	/** La maladie dont il a les symptômes (après l'incubation). */
	public Optional<Disease> symptoms(long now) {
		return disease != null && now >= since + disease.incubation() ? Optional.of(disease) : Optional.empty();
	}

	/** Soigné par le médecin : il se remet. */
	public boolean treated() {
		return treated;
	}

	/** Quand il en guérira. */
	public long until() {
		return until;
	}

	/** Le médecin doit-il venir ? Il a les symptômes, et personne ne l'a encore soigné. */
	public boolean needsCare(long now) {
		return symptoms(now).isPresent() && !treated;
	}

	public boolean immune(Disease d, long now) {
		Long until = immune.get(d);
		return until != null && until > now;
	}

	/** Il attrape cette maladie, s'il n'en a pas déjà une et n'en est pas protégé. */
	public boolean contract(Disease d, long now) {
		if (disease != null || immune(d, now)) {
			return false;
		}
		disease = d;
		since = now;
		until = now + d.incubation() + d.duration();
		treated = false;
		return true;
	}

	/** Le médecin le soigne : il guérira après sa convalescence (s'il ne devait pas guérir plus tôt). */
	public boolean treat(long now) {
		if (!needsCare(now)) {
			return false;
		}
		treated = true;
		until = Math.min(until, now + disease.convalescence());
		return true;
	}

	/** Le temps passe : guéri à l'échéance, et protégé un temps. Renvoie la maladie dont il vient de guérir. */
	public Optional<Disease> tick(long now) {
		if (disease == null || now < until) {
			return Optional.empty();
		}
		Disease cured = disease;
		immune.put(cured, now + IMMUNITY);
		disease = null;
		treated = false;
		return Optional.of(cured);
	}

	// Les causes

	/** Sous la pluie, sans manteau : un rhume, plus rarement une fièvre. */
	public Optional<Disease> exposed(boolean rain, boolean coat, long now, RandomGenerator random) {
		if (!rain || coat) {
			return Optional.empty();
		}
		double roll = random.nextDouble();
		Disease d = roll < RAIN_COLD ? Disease.COLD : roll < RAIN_COLD + RAIN_FEVER ? Disease.FEVER : null;
		return d != null && contract(d, now) ? Optional.of(d) : Optional.empty();
	}

	/** Il a mangé quelque chose de gâté ou d'empoisonné : souvent, la dysenterie. */
	public Optional<Disease> ate(boolean tainted, long now, RandomGenerator random) {
		return tainted && random.nextDouble() < TAINTED && contract(Disease.DYSENTERY, now)
				? Optional.of(Disease.DYSENTERY) : Optional.empty();
	}

	/** Un voisin malade de {@code d} : peut-être la lui passe-t-il. */
	public Optional<Disease> caught(Disease d, long now, RandomGenerator random) {
		return random.nextDouble() < d.contagion() && contract(d, now) ? Optional.of(d) : Optional.empty();
	}
}

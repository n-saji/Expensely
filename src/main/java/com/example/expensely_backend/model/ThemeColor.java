package com.example.expensely_backend.model;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ThemeColor {

	public static final String DEFAULT_ID = "supabase";

	private static final Set<String> SUPPORTED_IDS = Set.of(
			"supabase",
			"twitter",
			"bold-tech",
			"nature",
			"amber-minimal",
			"cyberpunk",
			"mono",
			"violet-bloom",
			"tangerine",
			"t3-chat",
			"kodama-grove",
			"catppuccin",
			"graphite",
			"caffeine",
			"ocean-breeze",
			"claude"
	);

	private static final Map<String, String> LEGACY_ALIASES = Map.ofEntries(
			Map.entry("teal", "supabase"),
			Map.entry("blue", "twitter"),
			Map.entry("indigo", "bold-tech"),
			Map.entry("emerald", "nature"),
			Map.entry("amber", "amber-minimal"),
			Map.entry("rose", "cyberpunk"),
			Map.entry("slate", "mono"),
			Map.entry("violet", "violet-bloom"),
			Map.entry("orange", "tangerine"),
			Map.entry("crimson", "t3-chat"),
			Map.entry("forest", "kodama-grove")
	);

	private ThemeColor() {
	}

	public static String normalize(String value) {
		if (value == null || value.isBlank()) {
			return DEFAULT_ID;
		}

		String normalized = value.trim().toLowerCase(Locale.ROOT);
		String migrated = LEGACY_ALIASES.getOrDefault(normalized, normalized);
		if (!SUPPORTED_IDS.contains(migrated)) {
			throw new IllegalArgumentException("Unsupported theme color: " + value);
		}

		return migrated;
	}
}

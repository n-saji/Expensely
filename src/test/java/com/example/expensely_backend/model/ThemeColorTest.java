package com.example.expensely_backend.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThemeColorTest {

	@Test
	void keepsSupportedTweakCnThemeIds() {
		assertEquals("ocean-breeze", ThemeColor.normalize(" Ocean-Breeze "));
	}

	@Test
	void migratesLegacyThemeAliases() {
		assertEquals("supabase", ThemeColor.normalize("teal"));
		assertEquals("twitter", ThemeColor.normalize("BLUE"));
		assertEquals("violet-bloom", ThemeColor.normalize("violet"));
		assertEquals("kodama-grove", ThemeColor.normalize("forest"));
	}

	@Test
	void rejectsUnknownThemeIds() {
		assertThrows(
				IllegalArgumentException.class,
				() -> ThemeColor.normalize("not-a-theme")
		);
	}
}

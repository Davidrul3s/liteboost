package com.liteboost;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * LiteBoost: a tiny, "no-mixin" performance tuner.
 *
 * It runs BEFORE Minecraft starts and lowers a handful of expensive video
 * options inside options.txt. It never touches Minecraft's code, so it cannot
 * crash the game by itself. A backup of your options is saved once as
 * options.txt.liteboost-backup.
 *
 * Settings live in config/liteboost.properties.
 */
public class LiteBoost implements PreLaunchEntrypoint {

	private static final String TAG = "[LiteBoost] ";

	@Override
	public void onPreLaunch() {
		try {
			apply();
		} catch (Throwable t) {
			// Never let this mod stop the game from starting.
			System.err.println(TAG + "Skipped, nothing was changed: " + t);
		}
	}

	private static Properties defaults() {
		Properties p = new Properties();
		p.setProperty("enabled", "true");
		// false = apply once, then leave your settings alone. true = re-apply every launch.
		p.setProperty("apply_every_launch", "false");
		p.setProperty("already_applied", "false");

		p.setProperty("fast_graphics", "true");
		p.setProperty("minimal_particles", "true");
		p.setProperty("clouds_off", "true");
		p.setProperty("entity_shadows_off", "true");
		p.setProperty("smooth_lighting_off", "true");

		// -1 = leave as is. Otherwise 0 to 7 (0 = fastest).
		p.setProperty("biome_blend_radius", "0");
		// These only LOWER your value, never raise it.
		p.setProperty("max_render_distance", "10");
		p.setProperty("max_simulation_distance", "6");
		p.setProperty("max_entity_distance_scaling", "0.5");
		return p;
	}

	private static void apply() throws IOException {
		FabricLoader loader = FabricLoader.getInstance();
		Path configFile = loader.getConfigDir().resolve("liteboost.properties");

		Properties cfg = defaults();
		if (Files.isRegularFile(configFile)) {
			try (InputStream in = Files.newInputStream(configFile)) {
				cfg.load(in);
			}
		} else {
			save(cfg, configFile);
		}

		if (!bool(cfg, "enabled")) {
			return;
		}
		boolean everyLaunch = bool(cfg, "apply_every_launch");
		if (bool(cfg, "already_applied") && !everyLaunch) {
			return;
		}

		Path gameDir = loader.getGameDir();
		Path options = gameDir.resolve("options.txt");
		if (!Files.isRegularFile(options)) {
			// First ever launch: Minecraft has not created options.txt yet.
			System.out.println(TAG + "options.txt not found yet. Settings will be applied on the next launch.");
			return;
		}

		List<String> lines = new ArrayList<>(Files.readAllLines(options, StandardCharsets.UTF_8));

		Path backup = gameDir.resolve("options.txt.liteboost-backup");
		if (!Files.exists(backup)) {
			Files.copy(options, backup);
		}

		if (bool(cfg, "fast_graphics")) {
			set(lines, "graphicsMode", "0");
		}
		if (bool(cfg, "minimal_particles")) {
			set(lines, "particles", "2");
		}
		if (bool(cfg, "clouds_off")) {
			set(lines, "renderClouds", "\"false\"");
		}
		if (bool(cfg, "entity_shadows_off")) {
			set(lines, "entityShadows", "false");
		}
		if (bool(cfg, "smooth_lighting_off")) {
			set(lines, "ao", "false");
		}

		int blend = integer(cfg, "biome_blend_radius", 0);
		if (blend >= 0) {
			set(lines, "biomeBlendRadius", Integer.toString(Math.min(blend, 7)));
		}

		capInt(lines, "renderDistance", clamp(integer(cfg, "max_render_distance", 10), 2, 32));
		capInt(lines, "simulationDistance", clamp(integer(cfg, "max_simulation_distance", 6), 5, 32));
		capDouble(lines, "entityDistanceScaling", clamp(decimal(cfg, "max_entity_distance_scaling", 0.5), 0.5, 5.0));

		Files.write(options, lines, StandardCharsets.UTF_8);

		cfg.setProperty("already_applied", "true");
		save(cfg, configFile);
		System.out.println(TAG + "Lighter video settings applied to options.txt.");
	}

	// ---------- config helpers ----------

	private static void save(Properties cfg, Path file) throws IOException {
		Path parent = file.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (OutputStream out = Files.newOutputStream(file)) {
			cfg.store(out, "LiteBoost settings. Set enabled=false to turn it off.");
		}
	}

	private static boolean bool(Properties p, String key) {
		return Boolean.parseBoolean(p.getProperty(key, "false").trim());
	}

	private static int integer(Properties p, String key, int fallback) {
		try {
			return Integer.parseInt(p.getProperty(key, "").trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static double decimal(Properties p, String key, double fallback) {
		try {
			return Double.parseDouble(p.getProperty(key, "").trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	// ---------- options.txt helpers ----------

	private static String get(List<String> lines, String key) {
		String prefix = key + ":";
		for (String line : lines) {
			if (line.startsWith(prefix)) {
				return line.substring(prefix.length()).trim();
			}
		}
		return null;
	}

	private static void set(List<String> lines, String key, String value) {
		String prefix = key + ":";
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).startsWith(prefix)) {
				lines.set(i, prefix + value);
				return;
			}
		}
		lines.add(prefix + value);
	}

	/** Lowers an integer option to the cap if it is higher (or missing/unreadable). */
	private static void capInt(List<String> lines, String key, int cap) {
		String current = get(lines, key);
		if (current != null) {
			try {
				if (Integer.parseInt(current) <= cap) {
					return;
				}
			} catch (NumberFormatException ignored) {
				// fall through and overwrite with the cap
			}
		}
		set(lines, key, Integer.toString(cap));
	}

	/** Same as capInt, for decimal options. */
	private static void capDouble(List<String> lines, String key, double cap) {
		String current = get(lines, key);
		if (current != null) {
			try {
				if (Double.parseDouble(current) <= cap) {
					return;
				}
			} catch (NumberFormatException ignored) {
				// fall through and overwrite with the cap
			}
		}
		set(lines, key, Double.toString(cap));
	}
}

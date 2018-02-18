/**
 * Copyright 2014-2015 by UnoModding, RyanTheAllmighty and Contributors
 *
 * This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License.
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.
 */
package unomodding.bukkit.playtimelimiter;

import static unomodding.bukkit.playtimelimiter.Configuration.Options.INITIAL_TIME;
import static unomodding.bukkit.playtimelimiter.Configuration.Options.SECONDS_BETWEEN_CHECKS;
import static unomodding.bukkit.playtimelimiter.Configuration.Options.SECONDS_BETWEEN_SAVES;
import static unomodding.bukkit.playtimelimiter.Configuration.Options.TIME_PER_DAY;
import static unomodding.bukkit.playtimelimiter.Configuration.Options.TIME_TRAVELS;

import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.ChatColor;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import unomodding.bukkit.playtimelimiter.exceptions.UnknownPlayerException;
import unomodding.bukkit.playtimelimiter.metrics.MetricsLite;
import unomodding.bukkit.playtimelimiter.threads.PlayTimeCheckerTask;
import unomodding.bukkit.playtimelimiter.threads.PlayTimeSaverTask;
import unomodding.bukkit.playtimelimiter.threads.ShutdownThread;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * PlayTimeLimiter plugin for Bukkit
 * 
 * @author RyanTheAllmighty
 * @author Jamie Mansfield <https://github.com/lexware>
 */
public class PlayTimeLimiter extends JavaPlugin {

	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.create();

	private final PlayTimeListener playerListener = new PlayTimeListener(this);
	private final Configuration configuration = new Configuration(this);
	private Map<String, Integer> timePlayed = new HashMap<>();
	private Map<String, Integer> timeLoggedIn = new HashMap<>();
	private Map<String, Boolean> seenWarningMessages = new HashMap<>();

	private boolean shutdownHookAdded = false;
	private boolean started = false;

	@Override
	public void onDisable() {
		// Save the playtime to file on plugin disable
		this.savePlayTime();

		// Remove tasks from Scheduler
		this.getServer().getScheduler().cancelTasks(this);
	}

	@Override
	public void onEnable() {
		if (!this.shutdownHookAdded) {
			this.shutdownHookAdded = true;
			try {
				Runtime.getRuntime().addShutdownHook(new ShutdownThread(this));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		// Register our events
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvents(playerListener, this);

		// Register our commands
		PlayTimeCommand playTimeCommand = new PlayTimeCommand(this);
		getCommand("playtime").setExecutor(playTimeCommand);
		getCommand("playtime").setTabCompleter(playTimeCommand);

		// Config
		this.started = this.getConfig().isSet(Configuration.Options.TIME_STARTED);
		this.configuration.ensureDefaults();

		// The server started log message
		this.getLogger().info(
				String.format("Server started at %s which was %s ago!",
						this.configuration.getTimeStarted(),
						this.secondsToDaysHoursSecondsString(
								Ints.checkedCast(Instant.now().getEpochSecond()) - this.configuration.getTimeStarted()
						)
				)
		);

		// PlayTimeLimiter v{} is enabled!
		final PluginDescriptionFile descriptor = this.getDescription();
		getLogger().info("PlayTimeLimiter v" + descriptor.getVersion() + " is enabled!");

		// Load the playtime from file
		this.loadPlayTime();

		// Tasks
		this.getServer().getScheduler().scheduleSyncRepeatingTask(this,
			    new PlayTimeSaverTask(this), 30000,
			        getConfig().getInt(SECONDS_BETWEEN_SAVES) * 1000);
		this.getServer().getScheduler().scheduleSyncRepeatingTask(this,
				new PlayTimeCheckerTask(this), 30000,
				getConfig().getInt(SECONDS_BETWEEN_CHECKS) * 1000);

		// Metrics
		try {
			final MetricsLite metrics = new MetricsLite(this);
			metrics.start();
		} catch (final IOException ex) {
			this.getLogger().log(Level.INFO, "Failed to send Metrics data!", ex);
		}
	}

	public int secondsUntilNextDay() {
		final int timeStarted = this.configuration.getTimeStarted();
		int secondsSince = (int) ((System.currentTimeMillis() / 1000) - timeStarted);

		while (secondsSince >= 86400) {
			secondsSince -= 86400;
		}

		return secondsSince;
	}

	public String secondsToDaysHoursSecondsString(int secondsToConvert) {
		final int hours = secondsToConvert / 3600;
		final int minutes = (secondsToConvert % 3600) / 60;
		final int seconds = secondsToConvert % 60;
		return String.format("%02d hours, %02d minutes & %02d seconds", hours,
				minutes, seconds);
	}

	public int getTimeAllowedInSeconds() {
		int timeStarted = this.configuration.getTimeStarted();
		int secondsSince = (int) ((System.currentTimeMillis() / 1000) - timeStarted);
		int secondsAllowed = 0;

		// Add the initial time we give the player at the beginning
		secondsAllowed += getConfig().getInt(INITIAL_TIME);

		// Then for each day including the first day (24 hours realtime) add the
		// set amount of
		// seconds to the time allowed
		while (secondsSince >= 0) {
			if (getConfig().getBoolean(TIME_TRAVELS)) {
				secondsAllowed += getConfig().getInt(TIME_PER_DAY);
			} else {
				secondsAllowed = getConfig().getInt(TIME_PER_DAY);
			}
			secondsSince -= 86400;
		}

		return secondsAllowed;
	}

	public int getTimeAllowedInSeconds(UUID uuid) {
		int secondsAllowed = this.getTimeAllowedInSeconds();

		// Remove the amount of time the player has played to get their time
		// allowed
		secondsAllowed -= getPlayerPlayTime(uuid);

		return secondsAllowed;
	}

	public void addPlayTime(UUID uuid, int seconds)
			throws UnknownPlayerException {
		if (this.timePlayed.containsKey(uuid.toString())) {
			this.timePlayed.put(uuid.toString(), this.timePlayed.get(uuid.toString())
					- seconds);
		} else {
			throw new UnknownPlayerException(uuid);
		}
	}

	public void removePlayTime(UUID uuid, int seconds)
			throws UnknownPlayerException {
		if (this.timePlayed.containsKey(uuid.toString())) {
			this.timePlayed.put(uuid.toString(), this.timePlayed.get(uuid.toString())
					+ seconds);
		} else {
			throw new UnknownPlayerException(uuid);
		}
	}

	public void setPlayTime(UUID uuid, int seconds) throws UnknownPlayerException {
		if(this.timePlayed.containsKey(uuid.toString())) {
			this.timePlayed.put(uuid.toString(), seconds);
		} else {
			throw new UnknownPlayerException(uuid);
		}
	}

	public int getPlayerPlayTime(UUID uuid) {
		int timePlayed = 0;
		if (this.timePlayed.containsKey(uuid.toString())) {
			timePlayed += this.timePlayed.get(uuid.toString());
		}
		if (this.timeLoggedIn.containsKey(uuid.toString())) {
			timePlayed += (int) ((System.currentTimeMillis() / 1000) - this.timeLoggedIn
					.get(uuid.toString()));
		}
		return timePlayed;
	}

	public void setPlayerLoggedIn(UUID uuid) {
		if (!this.timePlayed.containsKey(uuid.toString())) {
			this.timePlayed.put(uuid.toString(), 0);
			this.savePlayTime();
		}
		this.timeLoggedIn.put(uuid.toString(),
				(int) (System.currentTimeMillis() / 1000));
	}

	public void setPlayerLoggedOut(UUID uuid) {
		setPlayerLoggedOut(uuid.toString());
	}

	private void setPlayerLoggedOut(String uuid) {
		if (this.timeLoggedIn.containsKey(uuid)) {
			int timePlayed = (int) ((System.currentTimeMillis() / 1000) - this.timeLoggedIn
					.get(uuid));
			if (this.timePlayed.containsKey(uuid)) {
				timePlayed += this.timePlayed.get(uuid);
			}
			if (timePlayed > this.getTimeAllowedInSeconds()) {
				timePlayed = this.getTimeAllowedInSeconds();
			}
			this.timePlayed.put(uuid, timePlayed);
			this.timeLoggedIn.remove(uuid);
			getLogger().info(
					"Player " + uuid + " played for a total of " + timePlayed
							+ " seconds!");
			this.savePlayTime();
		}
		if (this.seenWarningMessages.containsKey(uuid + ":10")) {
			this.seenWarningMessages.remove(uuid + ":10");
		}
		if (this.seenWarningMessages.containsKey(uuid + ":60")) {
			this.seenWarningMessages.remove(uuid + ":60");
		}
		if (this.seenWarningMessages.containsKey(uuid + ":300")) {
			this.seenWarningMessages.remove(uuid + ":300");
		}
	}

	public boolean hasPlayerSeenMessage(UUID uuid, int time) {
		if (this.seenWarningMessages.containsKey(uuid.toString() + ":" + time)) {
			return this.seenWarningMessages.get(uuid.toString() + ":" + time);
		} else {
			return false;
		}
	}

	public void sentPlayerWarningMessage(UUID uuid, int time) {
		this.seenWarningMessages.put(uuid.toString() + ":" + time, true);
	}

	public boolean start() {
		if (this.started) {
			return false;
		} else {
			this.started = true;
			String initial = (getConfig().getInt(INITIAL_TIME) / 60 / 60) + "";
			String perday = (getConfig().getInt(TIME_PER_DAY) / 60 / 60) + "";
			getServer().broadcastMessage(
					ChatColor.GREEN + "Playtime has now started! You have "
							+ initial
							+ " hour/s of playtime to start with and " + perday
							+ " hour/s of playtime added per day!");
			getConfig().set("timeStarted", Ints.checkedCast(Instant.now().getEpochSecond()));
			saveConfig();
			return true;
		}
	}

	public boolean stop() {
		if (!this.started) {
			return false;
		} else {
			this.started = false;
			return true;
		}
	}

	public boolean hasStarted() {
		return this.started;
	}

	public void loadPlayTime() {
		if (!hasStarted()) {
			return;
		}
		File file = new File(getDataFolder(), "playtime.json");
		if (!getDataFolder().exists()) {
			getDataFolder().mkdirs();
		}
		if (!file.exists()) {
			getLogger().warning(
					"playtime.json file missing! Not loading in values");
			return;
		}
		getLogger().info("Loading data from playtime.json");
		FileReader fileReader;
		try {
			fileReader = new FileReader(file);
			java.lang.reflect.Type type = new TypeToken<Map<String, Integer>>() {
			}.getType();
			this.timePlayed = GSON.fromJson(fileReader, type);
			fileReader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void savePlayTime() {
		this.savePlayTime(false);
	}

	public void savePlayTime(boolean force) {
		if (!hasStarted()) {
			return;
		}

		if (force) {
			for (String key : this.timeLoggedIn.keySet()) {
				this.setPlayerLoggedOut(key);
			}
		}
		File file = new File(getDataFolder(), "playtime.json");
		if (!getDataFolder().exists()) {
			getDataFolder().mkdirs();
		}
		getLogger().info("Saving data to playtime.json");
		FileWriter fw = null;
		BufferedWriter bw = null;
		try {
			if (!file.exists()) {
				file.createNewFile();
			}
			fw = new FileWriter(file);
			bw = new BufferedWriter(fw);
			bw.write(GSON.toJson(this.timePlayed));
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			if (bw != null) {
				bw.close();
			}
			if (fw != null) {
				fw.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
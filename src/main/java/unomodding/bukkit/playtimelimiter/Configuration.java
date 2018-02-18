/**
 * Copyright 2013-2014 by UnoModding, ATLauncher and Contributors
 *
 * This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License.
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.
 */
package unomodding.bukkit.playtimelimiter;

public class Configuration {

    private final PlayTimeLimiter plugin;

    public Configuration(final PlayTimeLimiter plugin) {
        this.plugin = plugin;
    }

    /**
     * Ensures that the plugin configuration has been populated
     * with default values.
     */
    public void ensureDefaults() {
        if (!this.plugin.getConfig().isSet(Options.INITIAL_TIME)) {
            this.plugin.getConfig().set(Options.INITIAL_TIME, 28800);
            this.plugin.saveConfig();
        }

        if (!this.plugin.getConfig().isSet(Options.TIME_PER_DAY)) {
            this.plugin.getConfig().set(Options.TIME_PER_DAY, 3600);
            this.plugin.saveConfig();
        }

        if (!this.plugin.getConfig().isSet(Options.SECONDS_BETWEEN_CHECKS)) {
            this.plugin.getConfig().set(Options.SECONDS_BETWEEN_CHECKS, 10);
            this.plugin.saveConfig();
        }

        if (!this.plugin.getConfig().isSet(Options.SECONDS_BETWEEN_SAVES)) {
            this.plugin.getConfig().set(Options.SECONDS_BETWEEN_SAVES, 600);
            this.plugin.saveConfig();
        }

        if (!this.plugin.getConfig().isSet(Options.TIME_TRAVELS)) {
            this.plugin.getConfig().set(Options.TIME_TRAVELS, false);
            this.plugin.saveConfig();
        }

		/*if (!getConfig().isSet("timeCap")) {
			getConfig().set("timeCap", true);
			saveConfig();
		}

		if (!getConfig().isSet("timeCapValue")) {
			getConfig().set("timeCapValue", 18000);
			saveConfig();
		}*/
    }

    /**
     * Gets the time playtime was started on the server.
     *
     * @return The time playtime was started
     */
    public int getTimeStarted() {
        return this.plugin.getConfig().getInt(Options.TIME_STARTED);
    }

    /**
     * A psuedo-enum of all the configuration options in PlayTimeLimiter.
     */
    public static final class Options {

        /**
         * Time playtime was enabled (in seconds past Epoch).
         */
        public static final String TIME_STARTED = "timeStarted";

        /**
         * Initial playtime given (in seconds).
         *
         * <strong>Default</strong>: <em>28800</em>
         */
        public static final String INITIAL_TIME = "initialTime";

        /**
         * Daily playtime increment/reset (in seconds).
         *
         * <strong>Default</strong>: <em>3600</em>
         */
        public static final String TIME_PER_DAY = "timePerDay";

        /**
         * Time between each playtime check (in seconds).
         *
         * <strong>Default</strong>: <em>10</em>
         */
        public static final String SECONDS_BETWEEN_CHECKS = "secondsBetweenPlayTimeChecks";

        /**
         * Time between each playtime save (in seconds).
         *
         * <strong>Default</strong>: <em>600</em>
         */
        public static final String SECONDS_BETWEEN_SAVES = "secondsBetweenPlayTimeSaving";

        /**
         * Whether playtime should increment, rather than reset.
         *
         * <strong>Default</strong>: <em>false</em>
         */
        public static final String TIME_TRAVELS = "timeTravels";

        private Options() {
        }

    }

}

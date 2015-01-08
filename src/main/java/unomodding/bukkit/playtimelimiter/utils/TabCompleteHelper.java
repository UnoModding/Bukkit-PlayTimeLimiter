/**
 * Copyright 2015 by UnoModding and Contributors
 *
 * This work is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License.
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.
 */
package unomodding.bukkit.playtimelimiter.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * This class uses some methods from CanaryLib <https://github.com/CanaryModTeam/CanaryLib>
 */
public class TabCompleteHelper {
    public static List<String> matchTo(String arg, String[] possible) {
        List<String> matches = new ArrayList<String>();

        for (int index = 0; index < possible.length; index++) {
            if (startsWith(arg, possible[index])) {
                matches.add(possible[index]);
            }
        }

        return matches;
    }

    public static List<String> matchTo(String[] args, String[] possible) {
        String lastArg = args[args.length - 1];
        return matchTo(lastArg, possible);
    }

    public static boolean startsWith(String partial, String possible) {
        return possible.regionMatches(true, 0, partial, 0, partial.length());
    }

    public static String[] getKnownPlayerNames() {
        List<String> names = new ArrayList<String>();
        for(OfflinePlayer player : Bukkit.getServer().getOfflinePlayers()) {
            names.add(player.getName());
        }
        return names.toArray(new String[names.size()]);
    }
}

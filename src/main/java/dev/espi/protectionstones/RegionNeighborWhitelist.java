/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.espi.protectionstones;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persists the players allowed to create an adjacent claim as internal metadata on a ProtectionStones region.
 */
public final class RegionNeighborWhitelist {
    private RegionNeighborWhitelist() {
    }

    public static boolean contains(ProtectedRegion region, UUID playerUuid) {
        if (region == null || playerUuid == null) return false;
        Set<String> whitelist = region.getFlag(FlagHandler.PS_NEIGHBOR_WHITELIST);
        return whitelist != null && whitelist.contains(playerUuid.toString());
    }

    public static Set<UUID> getPlayers(ProtectedRegion region) {
        if (region == null) return Collections.emptySet();

        Set<String> whitelist = region.getFlag(FlagHandler.PS_NEIGHBOR_WHITELIST);
        if (whitelist == null) return Collections.emptySet();

        Set<UUID> players = new HashSet<>();
        for (String entry : whitelist) {
            try {
                players.add(UUID.fromString(entry));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy/manual entries instead of breaking placement or commands.
            }
        }
        return players;
    }

    public static boolean add(ProtectedRegion region, UUID playerUuid) {
        if (region == null || playerUuid == null) return false;

        Set<String> whitelist = copyEntries(region);
        if (!whitelist.add(playerUuid.toString())) return false;

        region.setFlag(FlagHandler.PS_NEIGHBOR_WHITELIST, whitelist);
        return true;
    }

    public static boolean remove(ProtectedRegion region, UUID playerUuid) {
        if (region == null || playerUuid == null) return false;

        Set<String> whitelist = copyEntries(region);
        if (!whitelist.remove(playerUuid.toString())) return false;

        region.setFlag(FlagHandler.PS_NEIGHBOR_WHITELIST, whitelist.isEmpty() ? null : whitelist);
        return true;
    }

    private static Set<String> copyEntries(ProtectedRegion region) {
        Set<String> whitelist = region.getFlag(FlagHandler.PS_NEIGHBOR_WHITELIST);
        return whitelist == null ? new HashSet<>() : new HashSet<>(whitelist);
    }
}

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

package dev.espi.protectionstones.utils;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.FlagHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Shared, side-effect-light helpers for ProtectionStones plot regions. */
public final class PlotUtils {

    public static final int PLOT_PRIORITY_OFFSET = 10;

    private PlotUtils() {
    }

    public static boolean isPlot(ProtectedRegion region) {
        return region != null && region.getFlag(FlagHandler.PS_PLOT) != null;
    }

    // ─── UUID lists stored on a plot ──────────────────────────────────────────
    //
    // A player is in at most one of the two lists at a time:
    //   PS_PLOT_DENIED   hard block, nothing gets through, not even public plot flags
    //   PS_PLOT_EXCLUDED soft exclusion, inherited parent membership no longer applies but
    //                    public plot flags still do, exactly like for a passer-by
    // Keeping them mutually exclusive is the caller's job; see ArgPlot kick/unkick/add.

    private static Set<UUID> getList(ProtectedRegion plot, com.sk89q.worldguard.protection.flags.Flag<String> flag) {
        String raw = plot.getFlag(flag);
        if (raw == null || raw.isBlank()) return new LinkedHashSet<>();

        Set<UUID> uuids = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            try {
                uuids.add(UUID.fromString(part.trim()));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy entries and remove them on the next write.
            }
        }
        return uuids;
    }

    private static void setList(ProtectedRegion plot, com.sk89q.worldguard.protection.flags.Flag<String> flag,
                                Collection<UUID> uuids) {
        String serialized = uuids.stream()
                .filter(java.util.Objects::nonNull)
                .map(UUID::toString)
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
        plot.setFlag(flag, serialized.isEmpty() ? null : serialized);
    }

    private static void addTo(ProtectedRegion plot, com.sk89q.worldguard.protection.flags.Flag<String> flag, UUID uuid) {
        Set<UUID> uuids = getList(plot, flag);
        uuids.add(uuid);
        setList(plot, flag, uuids);
    }

    private static void removeFrom(ProtectedRegion plot, com.sk89q.worldguard.protection.flags.Flag<String> flag, UUID uuid) {
        Set<UUID> uuids = getList(plot, flag);
        uuids.remove(uuid);
        setList(plot, flag, uuids);
    }

    public static Set<UUID> getDenied(ProtectedRegion plot) {
        return getList(plot, FlagHandler.PS_PLOT_DENIED);
    }

    public static boolean isDenied(UUID uuid, ProtectedRegion plot) {
        return getDenied(plot).contains(uuid);
    }

    public static void addDenied(ProtectedRegion plot, UUID uuid) {
        addTo(plot, FlagHandler.PS_PLOT_DENIED, uuid);
    }

    public static void removeDenied(ProtectedRegion plot, UUID uuid) {
        removeFrom(plot, FlagHandler.PS_PLOT_DENIED, uuid);
    }

    public static void setDenied(ProtectedRegion plot, Collection<UUID> denied) {
        setList(plot, FlagHandler.PS_PLOT_DENIED, denied);
    }

    public static Set<UUID> getExcluded(ProtectedRegion plot) {
        return getList(plot, FlagHandler.PS_PLOT_EXCLUDED);
    }

    public static boolean isExcluded(UUID uuid, ProtectedRegion plot) {
        return getExcluded(plot).contains(uuid);
    }

    public static void addExcluded(ProtectedRegion plot, UUID uuid) {
        addTo(plot, FlagHandler.PS_PLOT_EXCLUDED, uuid);
    }

    public static void removeExcluded(ProtectedRegion plot, UUID uuid) {
        removeFrom(plot, FlagHandler.PS_PLOT_EXCLUDED, uuid);
    }

    public static void setExcluded(ProtectedRegion plot, Collection<UUID> excluded) {
        setList(plot, FlagHandler.PS_PLOT_EXCLUDED, excluded);
    }

    /** Drops every plot-level restriction, leaving the player in the plain "not listed" state. */
    public static void clearRestrictions(ProtectedRegion plot, UUID uuid) {
        removeDenied(plot, uuid);
        removeExcluded(plot, uuid);
    }

    public static Map<String, List<ProtectedRegion>> indexByParent(Collection<ProtectedRegion> regions) {
        Map<String, List<ProtectedRegion>> result = new LinkedHashMap<>();
        for (ProtectedRegion region : regions) {
            String parentId = region.getFlag(FlagHandler.PS_PLOT);
            if (parentId == null) continue;
            result.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(region);
        }
        return result;
    }

    public static List<ProtectedRegion> childrenOf(Map<String, List<ProtectedRegion>> index, String parentId) {
        return index.getOrDefault(parentId, Collections.emptyList());
    }

    public static Optional<String> firstParentWithPlot(Collection<ProtectedRegion> regions,
                                                       Collection<String> parentIds) {
        Set<String> checkedIds = new LinkedHashSet<>(parentIds);
        for (ProtectedRegion region : regions) {
            String parentId = region.getFlag(FlagHandler.PS_PLOT);
            if (parentId != null && checkedIds.contains(parentId)) return Optional.of(parentId);
        }
        return Optional.empty();
    }

    public static OptionalInt plotPriority(int parentPriority) {
        if (parentPriority > Integer.MAX_VALUE - PLOT_PRIORITY_OFFSET) return OptionalInt.empty();
        return OptionalInt.of(parentPriority + PLOT_PRIORITY_OFFSET);
    }

    public static boolean boxesOverlap(BlockVector3 firstMin, BlockVector3 firstMax,
                                       BlockVector3 secondMin, BlockVector3 secondMax) {
        return firstMax.getX() >= secondMin.getX() && firstMin.getX() <= secondMax.getX()
                && firstMax.getY() >= secondMin.getY() && firstMin.getY() <= secondMax.getY()
                && firstMax.getZ() >= secondMin.getZ() && firstMin.getZ() <= secondMax.getZ();
    }

    public static boolean intersects(ProtectedRegion region, BlockVector3 selectionMin, BlockVector3 selectionMax) {
        BlockVector3 regionMin = region.getMinimumPoint();
        BlockVector3 regionMax = region.getMaximumPoint();
        if (!boxesOverlap(selectionMin, selectionMax, regionMin, regionMax)) return false;

        int minY = Math.max(selectionMin.getY(), regionMin.getY());
        long minX = Math.max(selectionMin.getX(), regionMin.getX());
        long maxX = Math.min(selectionMax.getX(), regionMax.getX());
        long minZ = Math.max(selectionMin.getZ(), regionMin.getZ());
        long maxZ = Math.min(selectionMax.getZ(), regionMax.getZ());
        for (long x = minX; x <= maxX; x++) {
            for (long z = minZ; z <= maxZ; z++) {
                if (region.contains(BlockVector3.at((int) x, minY, (int) z))) return true;
            }
        }
        return false;
    }

    /**
     * Verifies every X/Z column in the requested cuboid against the real parent geometry.
     * WorldGuard polygonal regions are vertically extruded, so checking both Y boundaries is sufficient.
     */
    public static boolean fullyContains(ProtectedRegion parent, BlockVector3 selectionMin, BlockVector3 selectionMax) {
        BlockVector3 parentMin = parent.getMinimumPoint();
        BlockVector3 parentMax = parent.getMaximumPoint();
        if (selectionMin.getY() < parentMin.getY() || selectionMax.getY() > parentMax.getY()) return false;
        if (selectionMin.getX() < parentMin.getX() || selectionMax.getX() > parentMax.getX()
                || selectionMin.getZ() < parentMin.getZ() || selectionMax.getZ() > parentMax.getZ()) return false;

        for (long x = selectionMin.getX(); x <= selectionMax.getX(); x++) {
            for (long z = selectionMin.getZ(); z <= selectionMax.getZ(); z++) {
                if (!parent.contains(BlockVector3.at((int) x, selectionMin.getY(), (int) z))
                        || !parent.contains(BlockVector3.at((int) x, selectionMax.getY(), (int) z))) {
                    return false;
                }
            }
        }
        return true;
    }
}

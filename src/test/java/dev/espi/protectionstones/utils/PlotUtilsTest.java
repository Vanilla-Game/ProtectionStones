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

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotUtilsTest {

    @Test
    void cuboidContainmentIncludesExactBoundaryAndRejectsYOverflow() {
        ProtectedCuboidRegion parent = new ProtectedCuboidRegion("parent",
                BlockVector3.at(0, 0, 0), BlockVector3.at(10, 20, 10));

        assertTrue(PlotUtils.fullyContains(parent, BlockVector3.at(0, 0, 0), BlockVector3.at(10, 20, 10)));
        assertFalse(PlotUtils.fullyContains(parent, BlockVector3.at(1, -1, 1), BlockVector3.at(9, 20, 9)));
    }

    @Test
    void concaveParentRejectsSelectionCrossingCutout() {
        ProtectedPolygonalRegion parent = new ProtectedPolygonalRegion("u",
                List.of(BlockVector2.at(0, 0), BlockVector2.at(8, 0), BlockVector2.at(8, 8),
                        BlockVector2.at(6, 8), BlockVector2.at(6, 2), BlockVector2.at(2, 2),
                        BlockVector2.at(2, 8), BlockVector2.at(0, 8)), 0, 20);

        assertTrue(PlotUtils.fullyContains(parent, BlockVector3.at(0, 1, 0), BlockVector3.at(1, 10, 8)));
        assertFalse(PlotUtils.fullyContains(parent, BlockVector3.at(1, 1, 1), BlockVector3.at(7, 10, 7)));
    }

    @Test
    void priorityNeverWraps() {
        assertTrue(PlotUtils.plotPriority(100).isPresent());
        assertFalse(PlotUtils.plotPriority(Integer.MAX_VALUE - 9).isPresent());
    }

    @Test
    void deniedSerializationDropsMalformedAndDuplicateEntries() {
        ProtectedCuboidRegion plot = new ProtectedCuboidRegion("plot",
                BlockVector3.ZERO, BlockVector3.ONE);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PlotUtils.setDenied(plot, List.of(first, first, second));

        assertTrue(PlotUtils.isDenied(first, plot));
        assertTrue(PlotUtils.getDenied(plot).equals(Set.of(first, second)));
        PlotUtils.removeDenied(plot, first);
        assertFalse(PlotUtils.isDenied(first, plot));
    }

    @Test
    void deniedAndExcludedListsAreIndependent() {
        ProtectedCuboidRegion plot = new ProtectedCuboidRegion("plot",
                BlockVector3.ZERO, BlockVector3.ONE);
        UUID blocked = UUID.randomUUID();
        UUID guest = UUID.randomUUID();

        PlotUtils.addDenied(plot, blocked);
        PlotUtils.addExcluded(plot, guest);

        assertTrue(PlotUtils.isDenied(blocked, plot));
        assertFalse(PlotUtils.isExcluded(blocked, plot));
        assertTrue(PlotUtils.isExcluded(guest, plot));
        assertFalse(PlotUtils.isDenied(guest, plot));

        // Clearing one list must leave the other untouched
        PlotUtils.removeDenied(plot, blocked);
        assertTrue(PlotUtils.isExcluded(guest, plot));
    }

    @Test
    void excludedSerializationDropsMalformedAndDuplicateEntries() {
        ProtectedCuboidRegion plot = new ProtectedCuboidRegion("plot",
                BlockVector3.ZERO, BlockVector3.ONE);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PlotUtils.setExcluded(plot, List.of(first, first, second));

        assertTrue(PlotUtils.getExcluded(plot).equals(Set.of(first, second)));
        PlotUtils.removeExcluded(plot, first);
        assertFalse(PlotUtils.isExcluded(first, plot));
        assertTrue(PlotUtils.isExcluded(second, plot));
    }

    @Test
    void clearRestrictionsDropsBothStates() {
        ProtectedCuboidRegion plot = new ProtectedCuboidRegion("plot",
                BlockVector3.ZERO, BlockVector3.ONE);
        UUID player = UUID.randomUUID();

        PlotUtils.addDenied(plot, player);
        PlotUtils.addExcluded(plot, player);
        PlotUtils.clearRestrictions(plot, player);

        assertFalse(PlotUtils.isDenied(player, plot));
        assertFalse(PlotUtils.isExcluded(player, plot));
    }

    @Test
    void indexesPlotsOnceByParent() {
        ProtectedCuboidRegion first = new ProtectedCuboidRegion("first", BlockVector3.ZERO, BlockVector3.ONE);
        ProtectedCuboidRegion second = new ProtectedCuboidRegion("second", BlockVector3.ZERO, BlockVector3.ONE);
        first.setFlag(dev.espi.protectionstones.FlagHandler.PS_PLOT, "parent-a");
        second.setFlag(dev.espi.protectionstones.FlagHandler.PS_PLOT, "parent-b");

        Map<String, List<com.sk89q.worldguard.protection.regions.ProtectedRegion>> index =
                PlotUtils.indexByParent(List.of(first, second));
        assertTrue(PlotUtils.childrenOf(index, "parent-a").contains(first));
        assertTrue(PlotUtils.childrenOf(index, "missing").isEmpty());
    }

    @Test
    void mergeGuardFindsPlotsOnAnyAffectedParent() {
        ProtectedCuboidRegion child = new ProtectedCuboidRegion("child", BlockVector3.ZERO, BlockVector3.ONE);
        child.setFlag(dev.espi.protectionstones.FlagHandler.PS_PLOT, "non-root");

        assertTrue(PlotUtils.firstParentWithPlot(List.of(child), List.of("root", "non-root")).isPresent());
        assertFalse(PlotUtils.firstParentWithPlot(List.of(child), List.of("unrelated")).isPresent());
    }

    @Test
    void intersectionUsesPolygonGeometryInsteadOfOnlyBoundingBox() {
        ProtectedPolygonalRegion parent = new ProtectedPolygonalRegion("u",
                List.of(BlockVector2.at(0, 0), BlockVector2.at(8, 0), BlockVector2.at(8, 8),
                        BlockVector2.at(6, 8), BlockVector2.at(6, 2), BlockVector2.at(2, 2),
                        BlockVector2.at(2, 8), BlockVector2.at(0, 8)), 0, 20);

        assertFalse(PlotUtils.intersects(parent, BlockVector3.at(3, 1, 3), BlockVector3.at(5, 10, 7)));
        assertTrue(PlotUtils.intersects(parent, BlockVector3.at(0, 1, 3), BlockVector3.at(3, 10, 7)));
    }
}

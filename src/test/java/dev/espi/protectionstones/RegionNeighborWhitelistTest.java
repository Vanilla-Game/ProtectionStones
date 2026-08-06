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

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionNeighborWhitelistTest {
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void startsEmpty() {
        ProtectedRegion region = region();

        assertFalse(RegionNeighborWhitelist.contains(region, PLAYER));
        assertTrue(RegionNeighborWhitelist.getPlayers(region).isEmpty());
    }

    @Test
    void addsAndReadsPlayerByUuid() {
        ProtectedRegion region = region();

        assertTrue(RegionNeighborWhitelist.add(region, PLAYER));
        assertTrue(RegionNeighborWhitelist.contains(region, PLAYER));
        assertEquals(Set.of(PLAYER), RegionNeighborWhitelist.getPlayers(region));
    }

    @Test
    void addingSamePlayerTwiceDoesNotDuplicateEntry() {
        ProtectedRegion region = region();

        assertTrue(RegionNeighborWhitelist.add(region, PLAYER));
        assertFalse(RegionNeighborWhitelist.add(region, PLAYER));
        assertEquals(Set.of(PLAYER), RegionNeighborWhitelist.getPlayers(region));
    }

    @Test
    void removingLastPlayerClearsInternalFlag() {
        ProtectedRegion region = region();
        RegionNeighborWhitelist.add(region, PLAYER);

        assertTrue(RegionNeighborWhitelist.remove(region, PLAYER));
        assertFalse(RegionNeighborWhitelist.contains(region, PLAYER));
        assertNull(region.getFlag(FlagHandler.PS_NEIGHBOR_WHITELIST));
    }

    @Test
    void ignoresMalformedStoredEntries() {
        ProtectedRegion region = region();
        region.setFlag(FlagHandler.PS_NEIGHBOR_WHITELIST, new HashSet<>(Set.of("not-a-uuid", PLAYER.toString())));

        assertEquals(Set.of(PLAYER), RegionNeighborWhitelist.getPlayers(region));
        assertTrue(RegionNeighborWhitelist.contains(region, PLAYER));
    }

    private static ProtectedRegion region() {
        return new ProtectedCuboidRegion(
                "test",
                BlockVector3.at(0, 0, 0),
                BlockVector3.at(10, 10, 10)
        );
    }
}

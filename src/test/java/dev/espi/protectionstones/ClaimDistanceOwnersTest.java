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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimDistanceOwnersTest {
    @Test
    void returnsUniqueSortedOwnerNamesAcrossClaims() {
        ProtectedRegion first = region("first", "Carol", "Alice");
        ProtectedRegion second = region("second", "Bob", "Alice");

        assertEquals("alice, bob, carol", BlockHandler.getOwnerNames(List.of(second, first)));
    }

    private static ProtectedRegion region(String id, String... owners) {
        ProtectedRegion region = new ProtectedCuboidRegion(
                id,
                BlockVector3.at(0, 0, 0),
                BlockVector3.at(10, 10, 10)
        );
        for (String owner : owners) region.getOwners().addPlayer(owner);
        return region;
    }
}

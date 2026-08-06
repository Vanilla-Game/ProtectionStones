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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimDistancePolicyTest {
    @Test
    void memberIsBlockedByDefault() {
        assertTrue(blocksPlacement(false, true, false));
    }

    @Test
    void memberCanBypassWhenEnabled() {
        assertFalse(blocksPlacement(false, true, true));
    }

    @Test
    void ownerCanAlwaysBypass() {
        assertFalse(blocksPlacement(true, false, false));
        assertFalse(blocksPlacement(true, false, true));
    }

    @Test
    void untrustedNeighborStillBlocksWhenMemberBypassIsEnabled() {
        assertTrue(blocksPlacement(false, false, true));
    }

    @Test
    void memberCannotBypassPlainWorldGuardRegion() {
        assertTrue(ClaimDistancePolicy.blocksPlacement(false, true, true, false, false, 10, 10));
    }

    @Test
    void oneUntrustedNeighborBlocksMixedSet() {
        boolean trustedNeighborBlocks = blocksPlacement(false, true, true);
        boolean untrustedNeighborBlocks = blocksPlacement(false, false, true);

        assertFalse(trustedNeighborBlocks);
        assertTrue(trustedNeighborBlocks || untrustedNeighborBlocks);
    }

    @Test
    void preservesPriorityAndPassthroughBehaviorForUntrustedRegions() {
        assertTrue(ClaimDistancePolicy.blocksPlacement(false, false, false, true, false, 0, 10));
        assertTrue(ClaimDistancePolicy.blocksPlacement(false, false, false, true, true, 10, 10));
        assertFalse(ClaimDistancePolicy.blocksPlacement(false, false, false, true, true, 9, 10));
        assertTrue(ClaimDistancePolicy.blocksPlacement(false, false, false, false, false, 10, 10));
        assertFalse(ClaimDistancePolicy.blocksPlacement(false, false, false, false, false, 9, 10));
    }

    private static boolean blocksPlacement(boolean owner, boolean member, boolean allowMembersToBypass) {
        return ClaimDistancePolicy.blocksPlacement(
                owner,
                member,
                allowMembersToBypass,
                true,
                false,
                0,
                10
        );
    }
}

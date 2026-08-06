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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimDistancePolicyTest {
    @Test
    void untrustedPlayerIsBlocked() {
        assertEquals(ClaimDistancePolicy.BlockReason.NEIGHBOR_APPROVAL_REQUIRED, blockReason(false, false));
    }

    @Test
    void whitelistedNeighborCanBypass() {
        assertEquals(ClaimDistancePolicy.BlockReason.NONE, blockReason(false, true));
    }

    @Test
    void ownerCanAlwaysBypass() {
        assertEquals(ClaimDistancePolicy.BlockReason.NONE, blockReason(true, false));
        assertEquals(ClaimDistancePolicy.BlockReason.NONE, blockReason(true, true));
    }

    @Test
    void neighborWhitelistCannotBypassPlainWorldGuardRegion() {
        assertEquals(ClaimDistancePolicy.BlockReason.OTHER_REGION,
                ClaimDistancePolicy.getBlockReason(false, true, false, false, 10, 10));
    }

    @Test
    void oneUntrustedNeighborBlocksMixedSet() {
        boolean trustedNeighborBlocks = blocksPlacement(false, true);
        boolean untrustedNeighborBlocks = blocksPlacement(false, false);

        assertFalse(trustedNeighborBlocks);
        assertTrue(trustedNeighborBlocks || untrustedNeighborBlocks);
    }

    @Test
    void preservesPriorityAndPassthroughBehaviorForUntrustedRegions() {
        assertEquals(ClaimDistancePolicy.BlockReason.NEIGHBOR_APPROVAL_REQUIRED,
                ClaimDistancePolicy.getBlockReason(false, false, true, false, 0, 10));
        assertEquals(ClaimDistancePolicy.BlockReason.NEIGHBOR_APPROVAL_REQUIRED,
                ClaimDistancePolicy.getBlockReason(false, false, true, true, 10, 10));
        assertEquals(ClaimDistancePolicy.BlockReason.NONE,
                ClaimDistancePolicy.getBlockReason(false, false, true, true, 9, 10));
        assertEquals(ClaimDistancePolicy.BlockReason.OTHER_REGION,
                ClaimDistancePolicy.getBlockReason(false, false, false, false, 10, 10));
        assertEquals(ClaimDistancePolicy.BlockReason.NONE,
                ClaimDistancePolicy.getBlockReason(false, false, false, false, 9, 10));
    }

    private static boolean blocksPlacement(boolean owner, boolean neighborAllowed) {
        return blockReason(owner, neighborAllowed) != ClaimDistancePolicy.BlockReason.NONE;
    }

    private static ClaimDistancePolicy.BlockReason blockReason(boolean owner, boolean neighborAllowed) {
        return ClaimDistancePolicy.getBlockReason(
                owner,
                neighborAllowed,
                true,
                false,
                0,
                10
        );
    }
}

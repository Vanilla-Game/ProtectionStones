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

final class ClaimDistancePolicy {
    enum BlockReason {
        NONE,
        NEIGHBOR_APPROVAL_REQUIRED,
        OTHER_REGION
    }

    private ClaimDistancePolicy() {
    }

    static BlockReason getBlockReason(
            boolean owner,
            boolean neighborAllowed,
            boolean protectionStonesRegion,
            boolean passthroughAllowed,
            int regionPriority,
            int testRegionPriority
    ) {
        if (owner || (protectionStonesRegion && neighborAllowed)) {
            return BlockReason.NONE;
        }

        if (protectionStonesRegion && !passthroughAllowed) {
            return BlockReason.NEIGHBOR_APPROVAL_REQUIRED;
        }

        if (regionPriority < testRegionPriority) {
            return BlockReason.NONE;
        }

        return protectionStonesRegion
                ? BlockReason.NEIGHBOR_APPROVAL_REQUIRED
                : BlockReason.OTHER_REGION;
    }
}

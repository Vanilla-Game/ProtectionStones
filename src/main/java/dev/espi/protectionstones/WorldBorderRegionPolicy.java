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

final class WorldBorderRegionPolicy {
    private WorldBorderRegionPolicy() {
    }

    static boolean isFarEnough(long regionMinX, long regionMaxX, long regionMinZ, long regionMaxZ,
                               double borderCenterX, double borderCenterZ, double borderSize,
                               double maxBorderCoordinate, int distance) {
        if (distance < 0) {
            throw new IllegalArgumentException("distance must not be negative");
        }

        double halfSize = borderSize / 2.0;
        double borderMinX = Math.max(-maxBorderCoordinate, borderCenterX - halfSize);
        double borderMaxX = Math.min(maxBorderCoordinate, borderCenterX + halfSize);
        double borderMinZ = Math.max(-maxBorderCoordinate, borderCenterZ - halfSize);
        double borderMaxZ = Math.min(maxBorderCoordinate, borderCenterZ + halfSize);

        // WorldGuard stores inclusive block coordinates. Add one to the maximum coordinates so the comparison uses
        // the outer faces of the blocks, producing the same physical distance on both sides of the world border.
        return regionMinX >= borderMinX + distance
                && regionMaxX + 1.0 <= borderMaxX - distance
                && regionMinZ >= borderMinZ + distance
                && regionMaxZ + 1.0 <= borderMaxZ - distance;
    }
}

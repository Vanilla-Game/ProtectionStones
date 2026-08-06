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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldBorderRegionPolicyTest {
    private static final double MAX_BORDER_COORDINATE = 29_999_984;

    @Test
    void allowsRegionExactlyAtRequiredDistanceFromEveryBorderEdge() {
        assertTrue(isFarEnough(-490, 489, -490, 489, 10));
    }

    @Test
    void rejectsRegionOneBlockTooCloseToWestEdge() {
        assertFalse(isFarEnough(-491, -400, -100, 100, 10));
    }

    @Test
    void rejectsRegionOneBlockTooCloseToEastEdge() {
        assertFalse(isFarEnough(400, 490, -100, 100, 10));
    }

    @Test
    void rejectsRegionOneBlockTooCloseToNorthEdge() {
        assertFalse(isFarEnough(-100, 100, -491, -400, 10));
    }

    @Test
    void rejectsRegionOneBlockTooCloseToSouthEdge() {
        assertFalse(isFarEnough(-100, 100, 400, 490, 10));
    }

    @Test
    void zeroDistanceAllowsOutermostBlocksInsideBorder() {
        assertTrue(isFarEnough(-500, 499, -500, 499, 0));
    }

    @Test
    void zeroDistanceRejectsRegionCrossingBorder() {
        assertFalse(isFarEnough(-501, 499, -500, 499, 0));
        assertFalse(isFarEnough(-500, 500, -500, 499, 0));
        assertFalse(isFarEnough(-500, 499, -501, 499, 0));
        assertFalse(isFarEnough(-500, 499, -500, 500, 0));
    }

    @Test
    void supportsOffsetFractionalBorderCenter() {
        assertTrue(WorldBorderRegionPolicy.isFarEnough(
                -39, 39, -59, 19,
                0.5, -19.5, 100, MAX_BORDER_COORDINATE, 10
        ));
        assertFalse(WorldBorderRegionPolicy.isFarEnough(
                -40, 39, -59, 19,
                0.5, -19.5, 100, MAX_BORDER_COORDINATE, 10
        ));
    }

    @Test
    void clampsEffectiveBorderToMaximumCoordinate() {
        assertTrue(WorldBorderRegionPolicy.isFarEnough(
                -390, 489, -490, 489,
                100, 0, 1_000, 500, 10
        ));
        assertFalse(WorldBorderRegionPolicy.isFarEnough(
                -390, 490, -490, 489,
                100, 0, 1_000, 500, 10
        ));
    }

    @Test
    void rejectsRegionWhenRequiredDistanceLeavesNoRoom() {
        assertFalse(isFarEnough(0, 0, 0, 0, 500));
    }

    @Test
    void rejectsNegativeDistanceAtPolicyBoundary() {
        assertThrows(IllegalArgumentException.class, () -> isFarEnough(0, 0, 0, 0, -1));
    }

    private static boolean isFarEnough(long minX, long maxX, long minZ, long maxZ, int distance) {
        return WorldBorderRegionPolicy.isFarEnough(
                minX, maxX, minZ, maxZ,
                0, 0, 1_000, MAX_BORDER_COORDINATE, distance
        );
    }
}

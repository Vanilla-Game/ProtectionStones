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

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PSGroupRegionTest {
    private static final String WORLD = "world";
    private static final String ROOT = "ps0x64y0z";
    private static final String MERGED = "ps128x64y0z";

    @Test
    void parsesConsistentMergedRegionMetadata() {
        Map<String, String> types = PSGroupRegion.parseMergedRegionTypes(
                WORLD,
                ROOT,
                Set.of(ROOT, MERGED),
                Set.of(ROOT + " DIAMOND_ORE", MERGED + " EMERALD_ORE")
        );

        assertEquals(Map.of(ROOT, "DIAMOND_ORE", MERGED, "EMERALD_ORE"), types);
    }

    @Test
    void rejectsMissingMergedRegionTypesFlag() {
        InvalidMergedRegionException exception = assertThrows(
                InvalidMergedRegionException.class,
                () -> PSGroupRegion.parseMergedRegionTypes(
                        WORLD, ROOT, Set.of(MERGED), null
                )
        );

        assertEquals(ROOT, exception.getRegionId());
        assertEquals(WORLD, exception.getWorldName());
        assertEquals("missing ps-merged-regions-types flag", exception.getReason());
    }

    @Test
    void rejectsTypeEntriesThatDoNotMatchMergedRegionIds() {
        InvalidMergedRegionException exception = assertThrows(
                InvalidMergedRegionException.class,
                () -> PSGroupRegion.parseMergedRegionTypes(
                        WORLD,
                        ROOT,
                        Set.of(ROOT, MERGED),
                        Set.of(ROOT + " DIAMOND_ORE", "ps256x64y0z EMERALD_ORE")
                )
        );

        assertEquals(
                "merged region IDs and type entries differ (missing types: [" + MERGED
                        + "], unexpected types: [ps256x64y0z])",
                exception.getReason()
        );
    }

    @Test
    void rejectsMalformedTypeEntry() {
        InvalidMergedRegionException exception = assertThrows(
                InvalidMergedRegionException.class,
                () -> PSGroupRegion.parseMergedRegionTypes(
                        WORLD,
                        ROOT,
                        Set.of(ROOT, MERGED),
                        Set.of(ROOT + " DIAMOND_ORE", MERGED)
                )
        );

        assertEquals("invalid ps-merged-regions-types entry: " + MERGED, exception.getReason());
    }

    @Test
    void rejectsGroupThatDoesNotContainItsOwnRegionId() {
        InvalidMergedRegionException exception = assertThrows(
                InvalidMergedRegionException.class,
                () -> PSGroupRegion.parseMergedRegionTypes(
                        WORLD,
                        ROOT,
                        Set.of(MERGED, "ps256x64y0z"),
                        Set.of(MERGED + " DIAMOND_ORE", "ps256x64y0z EMERALD_ORE")
                )
        );

        assertEquals("ps-merged-regions does not contain the group region ID", exception.getReason());
    }
}

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

class ClaimDistanceActionTest {
    @Test
    void defaultsToDenyForMissingOrUnsupportedActions() {
        assertEquals(ClaimDistanceAction.DENY, ClaimDistanceAction.fromConfig(null));
        assertEquals(ClaimDistanceAction.DENY, ClaimDistanceAction.fromConfig("block"));
        assertEquals(ClaimDistanceAction.DENY, ClaimDistanceAction.fromConfig("none"));
    }

    @Test
    void parsesSupportedActionsCaseInsensitively() {
        assertEquals(ClaimDistanceAction.DENY, ClaimDistanceAction.fromConfig("deny"));
        assertEquals(ClaimDistanceAction.WARN, ClaimDistanceAction.fromConfig(" WARN "));
    }

    @Test
    void validatesOnlyDocumentedActions() {
        assertTrue(ClaimDistanceAction.isSupported("deny"));
        assertTrue(ClaimDistanceAction.isSupported("WARN"));
        assertFalse(ClaimDistanceAction.isSupported("none"));
        assertFalse(ClaimDistanceAction.isSupported("block"));
        assertFalse(ClaimDistanceAction.isSupported(null));
    }
}

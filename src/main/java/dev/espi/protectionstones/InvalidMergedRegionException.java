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

/**
 * Raised when a WorldGuard region is marked as merged but its ProtectionStones metadata is incomplete or inconsistent.
 */
public class InvalidMergedRegionException extends IllegalStateException {
    private final String worldName;
    private final String regionId;
    private final String reason;

    InvalidMergedRegionException(String worldName, String regionId, String reason) {
        super("Invalid merged region " + regionId + " in world " + worldName + ": " + reason);
        this.worldName = worldName;
        this.regionId = regionId;
        this.reason = reason;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getReason() {
        return reason;
    }
}

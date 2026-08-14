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

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.utils.MiscUtil;
import dev.espi.protectionstones.utils.Objs;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a region that exists but is a group of merged {@link PSStandardRegion}s.
 * Contains multiple {@link PSMergedRegion} representing the individual merged regions (which don't technically exist in WorldGuard).
 */

public class PSGroupRegion extends PSStandardRegion {
    PSGroupRegion(ProtectedRegion wgregion, RegionManager rgmanager, World world) {
        super(wgregion, rgmanager, world);
        getMergedRegionTypes();
    }

    @Override
    public double getTaxRate() {
        double taxRate = 0;
        for (PSMergedRegion r : getMergedRegions()) {
            taxRate += r.getTaxRate();
        }
        return taxRate;
    }

    @Override
    public String getTaxPeriod() {
        Set<String> s = new HashSet<>();
        getMergedRegions().forEach(r -> s.add(r.getTaxPeriod()));
        return MiscUtil.concatWithoutLast(new ArrayList<>(s), ", ");
    }

    @Override
    public String getTaxPaymentPeriod() {
        Set<String> s = new HashSet<>();
        getMergedRegions().forEach(r -> s.add(r.getTaxPaymentPeriod()));
        return MiscUtil.concatWithoutLast(new ArrayList<>(s), ", ");
    }

    @Override
    public void updateTaxPayments() {
        long currentTime = System.currentTimeMillis();

        List<TaxPayment> payments = Objs.replaceNull(getTaxPaymentsDue(), new ArrayList<>());
        List<LastRegionTaxPaymentEntry> lastAdded = Objs.replaceNull(getRegionLastTaxPaymentAddedEntries(), new ArrayList<>());

        // loop over merged regions
        for (PSMergedRegion r : getMergedRegions()) {
            // taxes disabled
            if (getTypeOptions().taxPeriod == -1) continue;

            boolean found = false;
            for (LastRegionTaxPaymentEntry last : lastAdded) {
                // if the last region payment entry refers to this region
                if (last.getRegionId().equals(r.getId())) {
                    found = true;
                    // if it's time to pay
                    if (last.getLastPaymentAdded() + Duration.ofSeconds(r.getTypeOptions().taxPeriod).toMillis() < currentTime) {
                        payments.add(new TaxPayment(currentTime + Duration.ofSeconds(r.getTypeOptions().taxPaymentTime).toMillis(), r.getTaxRate(), r.getId()));
                        last.setLastPaymentAdded(currentTime);
                    }
                    break;
                }
            }

            if (!found) {
                payments.add(new TaxPayment(currentTime + Duration.ofSeconds(r.getTypeOptions().taxPaymentTime).toMillis(), r.getTaxRate(), r.getId()));
                lastAdded.add(new LastRegionTaxPaymentEntry(r.getId(), currentTime));
            }
        }
        setTaxPaymentsDue(payments);
        setRegionLastTaxPaymentAddedEntries(lastAdded);
    }

    @Override
    public boolean hide() {
        for (PSMergedRegion r : getMergedRegions()) r.hide();
        return true;
    }

    @Override
    public boolean unhide() {
        for (PSMergedRegion r : getMergedRegions()) r.unhide();
        return true;
    }

    @Override
    public boolean deleteRegion(boolean deleteBlock, Player cause) {
        List<PSMergedRegion> l = getMergedRegions();
        if (super.deleteRegion(deleteBlock, cause)) {
            for (PSMergedRegion r : l) {
                if (deleteBlock && !r.isHidden()) {
                    r.getProtectBlock().setType(Material.AIR);
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get the merged region whose ID is the same as the group region ID.
     * @return the root region
     */
    public PSMergedRegion getRootRegion() {
        for (PSMergedRegion r : getMergedRegions()) {
            if (r.getId().equals(getId())) return r;
        }
        return null;
    }

    /**
     * Check if this region contains a specific merged region
     * @param id the psID that would've been generated if the merged region was a standard region
     * @return whether or not the id is a merged region
     */
    public boolean hasMergedRegion(String id) {
        return getMergedRegionTypes().containsKey(id);
    }

    /**
     * Removes the merged region's information from the object.
     * Note: This DOES NOT remove the actual PSMergedRegion object, you have to call deleteRegion() on that as well.
     * @param id the id of the merged region
     */
    public void removeMergedRegionInfo(String id) {
        getMergedRegionTypes();
        getWGRegion().getFlag(FlagHandler.PS_MERGED_REGIONS).remove(id);

        // remove from ps merged region types
        Iterator<String> i = getWGRegion().getFlag(FlagHandler.PS_MERGED_REGIONS_TYPES).iterator();
        while (i.hasNext()) {
            String[] spl = i.next().split(" ");
            String rid = spl[0];
            if (rid.equals(id)) {
                i.remove();
                break;
            }
        }

        // remove from taxes
        if (getWGRegion().getFlag(FlagHandler.PS_TAX_LAST_PAYMENT_ADDED) != null) {
            String entry = "";
            for (String e : getWGRegion().getFlag(FlagHandler.PS_TAX_LAST_PAYMENT_ADDED)) {
                if (e.startsWith(id)) entry = e;
            }
            getWGRegion().getFlag(FlagHandler.PS_TAX_LAST_PAYMENT_ADDED).remove(entry);
        }
    }

    /**
     * Get the list of {@link PSMergedRegion} objects of the regions that were merged into this region.
     * @return the list of regions merged into this region
     */
    public List<PSMergedRegion> getMergedRegions() {
        return getMergedRegionsUnsafe().stream()
                .filter(r -> r.getTypeOptions() != null)
                .collect(Collectors.toList());
    }

    /**
     * Get the list of {@link PSMergedRegion} objects of the regions that were merged into this region.
     * Note: This is unsafe as it includes {@link PSMergedRegion}s that are of types not configured in the config.
     * @return the list of regions merged into this region
     */
    public List<PSMergedRegion> getMergedRegionsUnsafe() {
        List<PSMergedRegion> l = new ArrayList<>();
        for (Map.Entry<String, String> entry : getMergedRegionTypes().entrySet()) {
            l.add(new PSMergedRegion(entry.getKey(), entry.getValue(), this, getWGRegionManager(), getWorld()));
        }
        return l;
    }

    String getMergedRegionType(String id) {
        String type = getMergedRegionTypes().get(id);
        if (type == null) {
            throw invalidMetadata("ps-merged-regions does not contain " + id);
        }
        return type;
    }

    private Map<String, String> getMergedRegionTypes() {
        return parseMergedRegionTypes(
                getWorld().getName(),
                getId(),
                getWGRegion().getFlag(FlagHandler.PS_MERGED_REGIONS),
                getWGRegion().getFlag(FlagHandler.PS_MERGED_REGIONS_TYPES)
        );
    }

    static Map<String, String> parseMergedRegionTypes(String worldName, String regionId,
                                                       Set<String> mergedIds, Set<String> typeEntries) {
        if (mergedIds == null) {
            throw new InvalidMergedRegionException(worldName, regionId, "missing ps-merged-regions flag");
        }
        if (typeEntries == null) {
            throw new InvalidMergedRegionException(worldName, regionId, "missing ps-merged-regions-types flag");
        }
        if (mergedIds.size() < 2) {
            throw new InvalidMergedRegionException(worldName, regionId,
                    "ps-merged-regions must contain at least two region IDs");
        }
        if (!mergedIds.contains(regionId)) {
            throw new InvalidMergedRegionException(worldName, regionId,
                    "ps-merged-regions does not contain the group region ID");
        }

        Map<String, String> typesById = new HashMap<>();
        for (String entry : typeEntries) {
            if (entry == null) {
                throw new InvalidMergedRegionException(worldName, regionId,
                        "ps-merged-regions-types contains a null entry");
            }

            String[] parts = entry.trim().split("\\s+");
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new InvalidMergedRegionException(worldName, regionId,
                        "invalid ps-merged-regions-types entry: " + entry);
            }
            if (typesById.put(parts[0], parts[1]) != null) {
                throw new InvalidMergedRegionException(worldName, regionId,
                        "duplicate type entry for merged region " + parts[0]);
            }
        }

        if (!mergedIds.equals(typesById.keySet())) {
            Set<String> missingTypes = new TreeSet<>(mergedIds);
            missingTypes.removeAll(typesById.keySet());
            Set<String> unexpectedTypes = new TreeSet<>(typesById.keySet());
            unexpectedTypes.removeAll(mergedIds);
            throw new InvalidMergedRegionException(worldName, regionId,
                    "merged region IDs and type entries differ (missing types: " + missingTypes
                            + ", unexpected types: " + unexpectedTypes + ")");
        }

        return Collections.unmodifiableMap(typesById);
    }

    private InvalidMergedRegionException invalidMetadata(String reason) {
        return new InvalidMergedRegionException(getWorld().getName(), getId(), reason);
    }
}

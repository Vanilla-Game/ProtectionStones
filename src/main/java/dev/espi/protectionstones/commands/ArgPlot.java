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

package dev.espi.protectionstones.commands;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.RemovalStrategy;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.FlagHandler;
import dev.espi.protectionstones.PSL;
import dev.espi.protectionstones.PSRegion;
import dev.espi.protectionstones.ProtectionStones;
import dev.espi.protectionstones.utils.PlotUtils;
import dev.espi.protectionstones.utils.UUIDCache;
import dev.espi.protectionstones.utils.WGUtils;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ArgPlot implements PSCommandArg {

    private static final List<String> PLOT_ALLOWED_FLAGS = Arrays.asList("interact", "chest-access");

    // ─── Public static helpers (used by ListenerClass and ArgAddRemove) ────────

    public static boolean isDenied(UUID uuid, ProtectedRegion plot) {
        return PlotUtils.isDenied(uuid, plot);
    }

    public static void addDenied(ProtectedRegion plot, UUID uuid) {
        PlotUtils.addDenied(plot, uuid);
    }

    public static void removeDenied(ProtectedRegion plot, UUID uuid) {
        PlotUtils.removeDenied(plot, uuid);
    }

    public static void clearRestrictions(ProtectedRegion plot, UUID uuid) {
        PlotUtils.clearRestrictions(plot, uuid);
    }

    // ─── Command interface ─────────────────────────────────────────────────────

    @Override
    public List<String> getNames() { return Arrays.asList("plot"); }

    @Override
    public boolean allowNonPlayersToExecute() { return false; }

    @Override
    public List<String> getPermissionsToExecute() { return Arrays.asList("protectionstones.plot"); }

    @Override
    public HashMap<String, Boolean> getRegisteredFlags() { return null; }

    @Override
    public boolean executeArgument(CommandSender s, String[] args, HashMap<String, String> flags) {
        if (!(s instanceof Player p)) {
            return PSL.msg(s, PSL.PLOT_PLAYERS_ONLY.msg());
        }
        if (!p.hasPermission("protectionstones.plot")) {
            return PSL.msg(p, PSL.NO_PERMISSION_PLOT.msg());
        }
        if (args.length < 2) {
            return PSL.msg(p, PSL.PLOT_HELP.msg());
        }
        switch (args[1].toLowerCase()) {
            case "create":  return handleCreate(p, args);
            case "delete":  return handleDelete(p, args);
            case "add":     return handleAdd(p, args);
            case "kick":    return handleKick(p, args);
            case "unkick":  return handleUnkick(p, args);
            case "kickall": return handleKickAll(p, args);
            case "list":    return handleList(p);
            case "flag":    return handleFlag(p, args);
            default:        return PSL.msg(p, PSL.PLOT_HELP.msg());
        }
    }

    // ─── /ps plot create [name] ───────────────────────────────────────────────

    private boolean handleCreate(Player p, String[] args) {
        Region selection;
        try {
            LocalSession session = WorldEdit.getInstance().getSessionManager()
                    .get(BukkitAdapter.adapt(p));
            selection = session.getSelection(BukkitAdapter.adapt(p.getWorld()));
        } catch (IncompleteRegionException e) {
            return PSL.msg(p, PSL.PLOT_NO_SELECTION.msg());
        }

        BlockVector3 selMin = selection.getMinimumPoint();
        BlockVector3 selMax = selection.getMaximumPoint();
        RegionManager rm = WGUtils.getRegionManagerWithPlayer(p);
        if (rm == null) return PSL.msg(p, PSL.PLOT_OUTSIDE_REGION.msg());

        LocalPlayer lp = WorldGuardPlugin.inst().wrapPlayer(p);
        PSRegion parent = findBestParent(p, lp, rm, selMin, selMax);
        if (parent == null) {
            return PSL.msg(p, PSL.PLOT_OUTSIDE_REGION.msg());
        }

        OptionalInt priority = PlotUtils.plotPriority(parent.getWGRegion().getPriority());
        if (priority.isEmpty()) {
            return PSL.msg(p, PSL.PLOT_PRIORITY_TOO_HIGH.msg());
        }

        if (hasConflictingOverlap(rm, parent.getWGRegion(), lp, selMin, selMax, priority.getAsInt())) {
            return PSL.msg(p, PSL.PLOT_OVERLAP.msg());
        }

        // Optional name — checked for uniqueness per player per world
        String plotName = args.length >= 3 ? args[2] : null;
        if (plotName != null) {
            for (ProtectedRegion r : rm.getRegions().values()) {
                if (r.getId().equalsIgnoreCase(plotName)) {
                    return PSL.msg(p, PSL.PLOT_NAME_TAKEN.msg().replace("%name%", plotName));
                }
                if (r.getFlag(FlagHandler.PS_PLOT) == null) continue;
                if (!r.isOwner(lp) && !p.hasPermission("protectionstones.admin")) continue;
                String existingName = r.getFlag(FlagHandler.PS_NAME);
                if (plotName.equalsIgnoreCase(existingName)) {
                    return PSL.msg(p, PSL.PLOT_NAME_TAKEN.msg().replace("%name%", plotName));
                }
            }
        }

        // Economy check
        double cost = getCreateCost();
        Economy eco = ProtectionStones.getInstance().getVaultEconomy();
        if (cost > 0 && eco == null) {
            return PSL.msg(p, PSL.PLOT_ECONOMY_UNAVAILABLE.msg());
        }
        if (cost > 0 && !eco.has(p, cost)) {
            return PSL.msg(p, PSL.NOT_ENOUGH_MONEY.msg().replace("%price%", String.valueOf((long) cost)));
        }

        String plotId = generatePlotId(rm);
        ProtectedCuboidRegion plotWG = new ProtectedCuboidRegion(
                plotId,
                BlockVector3.at(selMin.x(), selMin.y(), selMin.z()),
                BlockVector3.at(selMax.x(), selMax.y(), selMax.z())
        );

        ProtectedRegion parentWG = parent.getWGRegion();
        plotWG.getOwners().addPlayer(p.getUniqueId());
        plotWG.setPriority(priority.getAsInt());
        plotWG.setFlag(FlagHandler.PS_PLOT, parent.getId());

        try {
            plotWG.setParent(parentWG);
        } catch (ProtectedRegion.CircularInheritanceException e) {
            return PSL.msg(p, ChatColor.RED + "Could not set parent region (circular inheritance).");
        }

        if (plotName != null) {
            plotWG.setFlag(FlagHandler.PS_NAME, plotName);
        }

        boolean charged = false;
        if (cost > 0) {
            EconomyResponse withdrawal = eco.withdrawPlayer(p, cost);
            if (!withdrawal.transactionSuccess()) {
                return PSL.msg(p, PSL.PLOT_PAYMENT_FAILED.msg());
            }
            charged = true;
        }

        try {
            rm.addRegion(plotWG);
        } catch (RuntimeException creationFailure) {
            rm.removeRegion(plotId, RemovalStrategy.UNSET_PARENT_IN_CHILDREN);
            if (charged) {
                EconomyResponse refund = eco.depositPlayer(p, cost);
                if (!refund.transactionSuccess()) {
                    ProtectionStones.getPluginLogger().severe("[Plots] REFUND FAILED: player="
                            + p.getUniqueId() + ", amount=" + cost + ", world=" + p.getWorld().getName()
                            + ", reason=" + refund.errorMessage);
                    PSL.msg(p, PSL.PLOT_REFUND_FAILED.msg());
                }
            }
            ProtectionStones.getPluginLogger().severe("[Plots] Could not create plot " + plotId + ": "
                    + creationFailure.getMessage());
            return PSL.msg(p, PSL.PLOT_CREATE_FAILED.msg());
        }

        if (charged) {
            PSL.msg(p, PSL.PAID_MONEY.msg().replace("%price%", String.valueOf((long) cost)));
        }

        return PSL.msg(p, PSL.PLOT_CREATED.msg()
                .replace("%id%", plotName != null ? plotName : plotId)
                .replace("%parent%", parent.getId()));
    }

    // ─── /ps plot delete <name|id> ────────────────────────────────────────────

    private boolean handleDelete(Player p, String[] args) {
        if (args.length < 3) return PSL.msg(p, PSL.PLOT_HELP.msg());
        RegionManager rm = WGUtils.getRegionManagerWithPlayer(p);
        List<ProtectedRegion> matches = findPlots(p, rm, args[2]);
        if (matches.isEmpty()) return PSL.msg(p, PSL.PLOT_NOT_FOUND.msg().replace("%name%", args[2]));
        if (matches.size() > 1) return PSL.msg(p, PSL.PLOT_AMBIGUOUS_NAME.msg().replace("%name%", args[2]));
        ProtectedRegion plot = matches.get(0);
        rm.removeRegion(plot.getId(), RemovalStrategy.UNSET_PARENT_IN_CHILDREN);
        return PSL.msg(p, PSL.PLOT_REMOVED.msg().replace("%id%", displayName(plot)));
    }

    // ─── /ps plot add <name|id> <player> ─────────────────────────────────────

    private boolean handleAdd(Player p, String[] args) {
        if (args.length < 4) return PSL.msg(p, PSL.PLOT_HELP.msg());
        if (!UUIDCache.containsName(args[3])) return PSL.msg(p, PSL.PLAYER_NOT_FOUND.msg());

        RegionManager rm = WGUtils.getRegionManagerWithPlayer(p);
        List<ProtectedRegion> matches = findPlots(p, rm, args[2]);
        if (matches.isEmpty()) return PSL.msg(p, PSL.PLOT_NOT_FOUND.msg().replace("%name%", args[2]));
        if (matches.size() > 1) return PSL.msg(p, PSL.PLOT_AMBIGUOUS_NAME.msg().replace("%name%", args[2]));

        UUID targetUUID = UUIDCache.getUUIDFromName(args[3]);
        ProtectedRegion plot = matches.get(0);

        // Drop every restriction (hard block or soft exclusion) and add to members
        PlotUtils.clearRestrictions(plot, targetUUID);
        plot.getMembers().addPlayer(targetUUID);

        return PSL.msg(p, PSL.PLOT_PLAYER_ADDED.msg()
                .replace("%player%", UUIDCache.getNameFromUUID(targetUUID))
                .replace("%plot%", displayName(plot)));
    }

    // ─── /ps plot kick <name|id> <player> ────────────────────────────────────

    private boolean handleKick(Player p, String[] args) {
        if (args.length < 4) return PSL.msg(p, PSL.PLOT_HELP.msg());
        if (!UUIDCache.containsName(args[3])) return PSL.msg(p, PSL.PLAYER_NOT_FOUND.msg());

        RegionManager rm = WGUtils.getRegionManagerWithPlayer(p);
        List<ProtectedRegion> matches = findPlots(p, rm, args[2]);
        if (matches.isEmpty()) return PSL.msg(p, PSL.PLOT_NOT_FOUND.msg().replace("%name%", args[2]));
        if (matches.size() > 1) return PSL.msg(p, PSL.PLOT_AMBIGUOUS_NAME.msg().replace("%name%", args[2]));

        UUID targetUUID = UUIDCache.getUUIDFromName(args[3]);
        ProtectedRegion plot = matches.get(0);

        // Parent region owner always retains access — cannot be denied
        String parentId = plot.getFlag(FlagHandler.PS_PLOT);
        ProtectedRegion parent = parentId != null ? rm.getRegion(parentId) : null;
        if (isParentOwner(parent, targetUUID)) {
            return PSL.msg(p, PSL.PLOT_CANNOT_KICK_PARENT_OWNER.msg());
        }

        // Remove from members/owners and add to explicit deny list.
        // The two restriction states are mutually exclusive, so drop any soft exclusion first.
        plot.getMembers().removePlayer(targetUUID);
        plot.getOwners().removePlayer(targetUUID);
        PlotUtils.removeExcluded(plot, targetUUID);
        addDenied(plot, targetUUID);

        return PSL.msg(p, PSL.PLOT_PLAYER_KICKED.msg()
                .replace("%player%", UUIDCache.getNameFromUUID(targetUUID))
                .replace("%plot%", displayName(plot)));
    }

    // ─── /ps plot unkick <name|id> <player> ──────────────────────────────────

    /**
     * Lifts a hard block and leaves the player as a plain passer-by: inherited membership from the
     * parent region no longer reaches into the plot, but the plot's public flags do apply.
     * This is deliberately not an undo of {@code kick} — {@code kick} already discarded any direct
     * membership, so restoring personal access needs an explicit {@code /ps plot add}.
     */
    private boolean handleUnkick(Player p, String[] args) {
        if (args.length < 4) return PSL.msg(p, PSL.PLOT_HELP.msg());
        if (!UUIDCache.containsName(args[3])) return PSL.msg(p, PSL.PLAYER_NOT_FOUND.msg());

        RegionManager rm = WGUtils.getRegionManagerWithPlayer(p);
        List<ProtectedRegion> matches = findPlots(p, rm, args[2]);
        if (matches.isEmpty()) return PSL.msg(p, PSL.PLOT_NOT_FOUND.msg().replace("%name%", args[2]));
        if (matches.size() > 1) return PSL.msg(p, PSL.PLOT_AMBIGUOUS_NAME.msg().replace("%name%", args[2]));

        UUID targetUUID = UUIDCache.getUUIDFromName(args[3]);
        ProtectedRegion plot = matches.get(0);

        // Parent region owners always have full access, so there is nothing to soften for them
        String parentId = plot.getFlag(FlagHandler.PS_PLOT);
        ProtectedRegion parent = parentId != null ? rm.getRegion(parentId) : null;
        if (isParentOwner(parent, targetUUID)) {
            return PSL.msg(p, PSL.PLOT_CANNOT_KICK_PARENT_OWNER.msg());
        }

        // Membership and a passer-by state cannot coexist: inherited or direct access would win
        plot.getMembers().removePlayer(targetUUID);
        plot.getOwners().removePlayer(targetUUID);
        PlotUtils.removeDenied(plot, targetUUID);
        PlotUtils.addExcluded(plot, targetUUID);

        return PSL.msg(p, PSL.PLOT_PLAYER_UNKICKED.msg()
                .replace("%player%", UUIDCache.getNameFromUUID(targetUUID))
                .replace("%plot%", displayName(plot)));
    }

    // ─── /ps plot kickall <player> ────────────────────────────────────────────

    private boolean handleKickAll(Player p, String[] args) {
        if (args.length < 3) return PSL.msg(p, PSL.PLOT_HELP.msg());
        if (!UUIDCache.containsName(args[2])) return PSL.msg(p, PSL.PLAYER_NOT_FOUND.msg());

        UUID targetUUID = UUIDCache.getUUIDFromName(args[2]);
        RegionManager rm = WGUtils.getRegionManagerWithPlayer(p);
        if (rm == null) return PSL.msg(p, PSL.PLOT_NOT_FOUND.msg().replace("%name%", args[2]));
        LocalPlayer lp = WorldGuardPlugin.inst().wrapPlayer(p);
        boolean isAdmin = p.hasPermission("protectionstones.admin");

        int count = 0;
        for (ProtectedRegion r : rm.getRegions().values()) {
            if (r.getFlag(FlagHandler.PS_PLOT) == null) continue;
            if (!canManagePlot(lp, r, rm, isAdmin)) continue;

            // Skip plots where target is parent region owner
            String parentId = r.getFlag(FlagHandler.PS_PLOT);
            ProtectedRegion parent = parentId != null ? rm.getRegion(parentId) : null;
            if (isParentOwner(parent, targetUUID)) continue;

            r.getMembers().removePlayer(targetUUID);
            r.getOwners().removePlayer(targetUUID);
            PlotUtils.removeExcluded(r, targetUUID);
            addDenied(r, targetUUID);
            count++;
        }

        if (count == 0) {
            return PSL.msg(p, PSL.PLOT_NOT_FOUND.msg().replace("%name%", args[2]));
        }

        return PSL.msg(p, PSL.PLOT_KICKALL.msg()
                .replace("%player%", UUIDCache.getNameFromUUID(targetUUID))
                .replace("%count%", String.valueOf(count)));
    }

    // ─── /ps plot list ────────────────────────────────────────────────────────

    private boolean handleList(Player p) {
        RegionManager rm = WGUtils.getRegionManagerWithPlayer(p);
        if (rm == null) return PSL.msg(p, PSL.PLOT_LIST_EMPTY.msg());
        LocalPlayer lp = WorldGuardPlugin.inst().wrapPlayer(p);
        boolean isAdmin = p.hasPermission("protectionstones.admin");

        List<String> lines = new ArrayList<>();
        for (ProtectedRegion r : rm.getRegions().values()) {
            if (r.getFlag(FlagHandler.PS_PLOT) == null) continue;
            if (!r.isOwner(lp) && !canManagePlot(lp, r, rm, isAdmin)) continue;

            String name = r.getFlag(FlagHandler.PS_NAME);
            String parentId = r.getFlag(FlagHandler.PS_PLOT);
            ProtectedRegion parent = rm.getRegion(parentId);

            // Effective access: (parent members ∪ plot members ∪ plot owners) − denied
            Set<UUID> denied = PlotUtils.getDenied(r);
            Set<UUID> direct = new HashSet<>(r.getMembers().getUniqueIds());
            direct.addAll(r.getOwners().getUniqueIds());
            Set<UUID> fromParent = parent != null ? new HashSet<>(parent.getMembers().getUniqueIds()) : new HashSet<>();

            Set<UUID> effective = new HashSet<>();
            effective.addAll(direct);
            effective.addAll(fromParent);
            effective.removeAll(denied);
            effective.remove(p.getUniqueId()); // don't list self

            List<String> accessList = new ArrayList<>();
            for (UUID uuid : effective) {
                String pName = UUIDCache.getNameFromUUID(uuid);
                if (pName == null) continue;
                boolean viaParent = fromParent.contains(uuid) && !direct.contains(uuid);
                accessList.add(viaParent
                        ? ChatColor.YELLOW + pName + ChatColor.GRAY + "(via parent)"
                        : ChatColor.WHITE + pName);
            }

            BlockVector3 pMin = r.getMinimumPoint();
            BlockVector3 pMax = r.getMaximumPoint();
            String coords = ChatColor.DARK_GRAY + "("
                    + pMin.x() + "," + pMin.y() + "," + pMin.z()
                    + ChatColor.DARK_GRAY + ")→("
                    + pMax.x() + "," + pMax.y() + "," + pMax.z()
                    + ChatColor.DARK_GRAY + ")";

            String displayStr = name != null ? name : r.getId();
            StringBuilder line = new StringBuilder()
                    .append(ChatColor.AQUA).append("● ").append(ChatColor.WHITE).append(displayStr)
                    .append(ChatColor.GRAY).append(" [").append(parentId).append("] ").append(coords)
                    .append(ChatColor.GRAY).append(" | Access: ")
                    .append(accessList.isEmpty() ? ChatColor.GRAY + "none" : String.join(ChatColor.GRAY + ", ", accessList));

            String blockedNames = joinNames(denied);
            if (!blockedNames.isEmpty()) {
                line.append(ChatColor.GRAY).append(" | Blocked: ").append(ChatColor.RED).append(blockedNames);
            }
            String guestNames = joinNames(PlotUtils.getExcluded(r));
            if (!guestNames.isEmpty()) {
                line.append(ChatColor.GRAY).append(" | Guests: ").append(ChatColor.YELLOW).append(guestNames);
            }
            lines.add(line.toString());
        }

        if (lines.isEmpty()) return PSL.msg(p, PSL.PLOT_LIST_EMPTY.msg());

        PSL.msg(p, PSL.PLOT_LIST_HEADER.msg());
        for (String line : lines) p.sendMessage(line);
        return true;
    }

    // ─── /ps plot flag <name|id> [flag] [value] ──────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean handleFlag(Player p, String[] args) {
        if (args.length < 3) return PSL.msg(p, PSL.PLOT_HELP.msg());
        RegionManager rm = WGUtils.getRegionManagerWithPlayer(p);
        List<ProtectedRegion> matches = findPlots(p, rm, args[2]);
        if (matches.isEmpty()) return PSL.msg(p, PSL.PLOT_NOT_FOUND.msg().replace("%name%", args[2]));
        if (matches.size() > 1) return PSL.msg(p, PSL.PLOT_AMBIGUOUS_NAME.msg().replace("%name%", args[2]));
        ProtectedRegion plot = matches.get(0);

        if (args.length == 3) return showPlotFlags(p, plot);
        if (args.length < 5) return PSL.msg(p, PSL.PLOT_HELP.msg());

        String flagName = args[3].toLowerCase();
        String value    = args[4].toLowerCase();

        if (!PLOT_ALLOWED_FLAGS.contains(flagName)) return PSL.msg(p, PSL.PLOT_FLAG_INVALID.msg());

        Flag flag = Flags.fuzzyMatchFlag(WGUtils.getFlagRegistry(), flagName);
        if (flag == null) return PSL.msg(p, PSL.PLOT_FLAG_INVALID.msg());

        if (value.equals("none") || value.equals("null")) {
            plot.setFlag(flag, null);
            return PSL.msg(p, PSL.PLOT_FLAG_CLEARED.msg()
                    .replace("%flag%", flagName)
                    .replace("%plot%", displayName(plot)));
        }

        try {
            FlagContext fc = FlagContext.create().setInput(value).build();
            plot.setFlag(flag, flag.parseInput(fc));
            return PSL.msg(p, PSL.PLOT_FLAG_SET.msg()
                    .replace("%flag%", flagName)
                    .replace("%value%", value)
                    .replace("%plot%", displayName(plot)));
        } catch (InvalidFlagFormat e) {
            return PSL.msg(p, PSL.PLOT_FLAG_INVALID.msg());
        }
    }

    @SuppressWarnings("rawtypes")
    private boolean showPlotFlags(Player p, ProtectedRegion plot) {
        PSL.msg(p, PSL.PLOT_FLAG_LIST_HEADER.msg().replace("%plot%", displayName(plot)));
        boolean any = false;
        for (String flagName : PLOT_ALLOWED_FLAGS) {
            Flag flag = Flags.fuzzyMatchFlag(WGUtils.getFlagRegistry(), flagName);
            if (flag == null) continue;
            Object val = plot.getFlag(flag);
            if (val != null) {
                any = true;
                p.sendMessage(PSL.PLOT_FLAG_ENTRY.msg()
                        .replace("%flag%", flagName)
                        .replace("%value%", val.toString().toLowerCase()));
            }
        }
        if (!any) PSL.msg(p, PSL.PLOT_FLAG_NONE_SET.msg());
        return true;
    }

    // ─── Tab completion ───────────────────────────────────────────────────────

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!(sender instanceof Player)) return null;
        Player p = (Player) sender;

        if (args.length == 2) {
            return Arrays.asList("create", "delete", "add", "kick", "unkick", "kickall", "list", "flag").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            switch (args[1].toLowerCase()) {
                case "flag":
                case "delete":
                case "add":
                case "kick":
                case "unkick": {
                    RegionManager rm = WGUtils.getRegionManagerWithPlayer(p);
                    if (rm == null) return null;
                    LocalPlayer lp = WorldGuardPlugin.inst().wrapPlayer(p);
                    boolean isAdmin = p.hasPermission("protectionstones.admin");
                    List<String> names = new ArrayList<>();
                    for (ProtectedRegion r : rm.getRegions().values()) {
                        if (r.getFlag(FlagHandler.PS_PLOT) == null) continue;
                        if (!canManagePlot(lp, r, rm, isAdmin)) continue;
                        String plotName = r.getFlag(FlagHandler.PS_NAME);
                        names.add(plotName != null ? plotName : r.getId());
                    }
                    return names.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "kickall":
                    return Bukkit.getOnlinePlayers().stream()
                            .filter(pl -> p.canSee(pl))
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }

        if (args.length == 4) {
            switch (args[1].toLowerCase()) {
                case "add":
                case "kick":
                case "unkick":
                    return Bukkit.getOnlinePlayers().stream()
                            .filter(pl -> p.canSee(pl))
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                case "flag":
                    return PLOT_ALLOWED_FLAGS.stream()
                            .filter(f -> f.startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }

        if (args.length == 5 && args[1].equalsIgnoreCase("flag")) {
            return Arrays.asList("allow", "deny", "none").stream()
                    .filter(v -> v.startsWith(args[4].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return null;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private List<ProtectedRegion> findPlots(Player p, RegionManager rm, String nameOrId) {
        if (rm == null) return Collections.emptyList();
        LocalPlayer lp = WorldGuardPlugin.inst().wrapPlayer(p);
        boolean isAdmin = p.hasPermission("protectionstones.admin");
        List<ProtectedRegion> nameMatches = new ArrayList<>();

        for (ProtectedRegion r : rm.getRegions().values()) {
            if (r.getFlag(FlagHandler.PS_PLOT) == null) continue;
            if (!canManagePlot(lp, r, rm, isAdmin)) continue;
            if (r.getId().equalsIgnoreCase(nameOrId)) return Collections.singletonList(r);
            String name = r.getFlag(FlagHandler.PS_NAME);
            if (name != null && name.equalsIgnoreCase(nameOrId)) nameMatches.add(r);
        }
        return nameMatches;
    }

    private boolean canManagePlot(LocalPlayer lp, ProtectedRegion plot,
                                   RegionManager rm, boolean isAdmin) {
        if (isAdmin) return true;
        if (plot.isOwner(lp)) return true;
        String parentId = plot.getFlag(FlagHandler.PS_PLOT);
        if (parentId != null) {
            ProtectedRegion parent = rm.getRegion(parentId);
            if (parent != null && parent.isOwner(lp)) return true;
        }
        return false;
    }

    private PSRegion findBestParent(Player p, LocalPlayer lp, RegionManager rm,
                                    BlockVector3 selectionMin, BlockVector3 selectionMax) {
        PSRegion best = null;
        long bestFootprint = Long.MAX_VALUE;
        boolean isAdmin = p.hasPermission("protectionstones.admin");

        for (ProtectedRegion r : rm.getRegions().values()) {
            PSRegion psr = PSRegion.fromWGRegion(p.getWorld(), r);
            if (psr == null) continue;
            if (!r.isOwner(lp) && !isAdmin) continue;
            if (!PlotUtils.fullyContains(r, selectionMin, selectionMax)) continue;

            long footprint = regionFootprint(r);
            if (footprint < bestFootprint) {
                bestFootprint = footprint;
                best = psr;
            }
        }
        return best;
    }

    private boolean hasConflictingOverlap(RegionManager rm, ProtectedRegion parent, LocalPlayer player,
                                          BlockVector3 selMin, BlockVector3 selMax, int candidatePriority) {
        for (ProtectedRegion r : rm.getRegions().values()) {
            if (!PlotUtils.intersects(r, selMin, selMax)) continue;
            if (PlotUtils.isPlot(r)) return true;
            if (r == parent || r.getId().equalsIgnoreCase("__global__") || isAncestorOf(parent, r)) continue;
            if (r.isOwner(player)) continue;
            if (candidatePriority >= r.getPriority()) return true;
        }
        return false;
    }

    private boolean isAncestorOf(ProtectedRegion child, ProtectedRegion candidateAncestor) {
        ProtectedRegion current = child.getParent();
        while (current != null) {
            if (current == candidateAncestor || current.getId().equals(candidateAncestor.getId())) return true;
            current = current.getParent();
        }
        return false;
    }

    private boolean isParentOwner(ProtectedRegion parent, UUID targetUUID) {
        if (parent == null) return false;
        if (parent.getOwners().getUniqueIds().contains(targetUUID)) return true;
        Player online = Bukkit.getPlayer(targetUUID);
        return online != null && parent.isOwner(WorldGuardPlugin.inst().wrapPlayer(online));
    }

    private long regionFootprint(ProtectedRegion r) {
        BlockVector3 min = r.getMinimumPoint();
        BlockVector3 max = r.getMaximumPoint();
        return (long)(max.x() - min.x() + 1) * (max.z() - min.z() + 1);
    }

    private String generatePlotId(RegionManager rm) {
        String id;
        do { id = "psplot_" + Long.toHexString(System.nanoTime()); }
        while (rm.getRegion(id) != null);
        return id;
    }

    private double getCreateCost() {
        Double cost = ProtectionStones.getInstance().getConfigOptions().plotCreateCost;
        return cost != null ? cost : 50.0;
    }

    /** Resolves UUIDs to names for /ps plot list, skipping entries the cache no longer knows. */
    private String joinNames(Set<UUID> uuids) {
        return uuids.stream()
                .map(UUIDCache::getNameFromUUID)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    private String displayName(ProtectedRegion plot) {
        String name = plot.getFlag(FlagHandler.PS_NAME);
        return name != null ? name : plot.getId();
    }
}

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

import dev.espi.protectionstones.PSL;
import dev.espi.protectionstones.PSRegion;
import dev.espi.protectionstones.RegionNeighborWhitelist;
import dev.espi.protectionstones.utils.UUIDCache;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ArgNeighbor implements PSCommandArg {
    private enum Operation {
        ADD,
        REMOVE,
        LIST
    }

    @Override
    public List<String> getNames() {
        return Arrays.asList(
                "addneighbor",
                "removeneighbor",
                "neighbors"
        );
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public List<String> getPermissionsToExecute() {
        return Arrays.asList("protectionstones.neighbors");
    }

    @Override
    public HashMap<String, Boolean> getRegisteredFlags() {
        return null;
    }

    @Override
    public boolean executeArgument(CommandSender sender, String[] args, HashMap<String, String> flags) {
        Player player = (Player) sender;
        if (!player.hasPermission("protectionstones.neighbors")) {
            return PSL.msg(player, PSL.NO_PERMISSION_NEIGHBORS.msg());
        }

        PSRegion region = PSRegion.fromLocationGroup(player.getLocation());
        if (region == null) {
            return PSL.msg(player, PSL.NOT_IN_REGION.msg());
        }
        if (!region.isOwner(player.getUniqueId()) && !player.hasPermission("protectionstones.superowner")) {
            return PSL.msg(player, PSL.NOT_OWNER.msg());
        }

        Operation operation = getOperation(args[0]);
        if (operation == Operation.LIST) {
            return listNeighbors(player, region);
        }

        if (args.length < 2) {
            return PSL.msg(player, PSL.COMMAND_REQUIRES_PLAYER_NAME.msg());
        }
        if (!UUIDCache.containsName(args[1])) {
            return PSL.msg(player, PSL.PLAYER_NOT_FOUND.msg());
        }

        UUID neighborUuid = UUIDCache.getUUIDFromName(args[1]);
        String neighborName = UUIDCache.getNameFromUUID(neighborUuid);
        if (neighborName == null) neighborName = args[1];
        if (neighborUuid.equals(player.getUniqueId())) {
            return PSL.msg(player, PSL.NEIGHBOR_CANNOT_ADD_SELF.msg());
        }

        if (operation == Operation.ADD) {
            if (!RegionNeighborWhitelist.add(region.getWGRegion(), neighborUuid)) {
                return PSL.msg(player, PSL.NEIGHBOR_ALREADY_ADDED.msg().replace("%player%", neighborName));
            }
            return PSL.msg(player, PSL.NEIGHBOR_ADDED.msg().replace("%player%", neighborName));
        }

        if (!RegionNeighborWhitelist.remove(region.getWGRegion(), neighborUuid)) {
            return PSL.msg(player, PSL.NEIGHBOR_NOT_ADDED.msg().replace("%player%", neighborName));
        }
        return PSL.msg(player, PSL.NEIGHBOR_REMOVED.msg().replace("%player%", neighborName));
    }

    private boolean listNeighbors(Player player, PSRegion region) {
        List<String> names = new ArrayList<>();
        for (UUID uuid : RegionNeighborWhitelist.getPlayers(region.getWGRegion())) {
            String name = UUIDCache.getNameFromUUID(uuid);
            if (name == null) name = Bukkit.getOfflinePlayer(uuid).getName();
            names.add(name == null ? uuid.toString() : name);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);

        if (names.isEmpty()) {
            return PSL.msg(player, PSL.NEIGHBOR_LIST_EMPTY.msg());
        }
        return PSL.msg(player, PSL.NEIGHBOR_LIST.msg().replace("%players%", String.join(", ", names)));
    }

    private Operation getOperation(String argument) {
        String operation = argument.toLowerCase();
        if (operation.startsWith("add")) return Operation.ADD;
        if (operation.startsWith("remove")) return Operation.REMOVE;
        return Operation.LIST;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!(sender instanceof Player) || args.length != 2) return null;

        Player player = (Player) sender;
        List<String> names = new ArrayList<>();
        Operation operation = getOperation(args[0]);
        if (operation == Operation.ADD) {
            for (Player candidate : Bukkit.getOnlinePlayers()) {
                if (!candidate.equals(player) && player.canSee(candidate)) names.add(candidate.getName());
            }
        } else if (operation == Operation.REMOVE) {
            PSRegion region = PSRegion.fromLocationGroup(player.getLocation());
            if (region != null) {
                for (UUID uuid : RegionNeighborWhitelist.getPlayers(region.getWGRegion())) {
                    String name = UUIDCache.getNameFromUUID(uuid);
                    if (name != null) names.add(name);
                }
            }
        }

        names.sort(Comparator.comparing(String::toLowerCase));
        return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
    }
}

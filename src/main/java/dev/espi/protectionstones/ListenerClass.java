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

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.event.block.PlaceBlockEvent;
import com.sk89q.worldguard.bukkit.util.Materials;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.event.PSBreakProtectBlockEvent;
import dev.espi.protectionstones.event.PSCreateEvent;
import dev.espi.protectionstones.event.PSRemoveEvent;
import dev.espi.protectionstones.utils.RecipeUtil;
import dev.espi.protectionstones.utils.PlotUtils;
import dev.espi.protectionstones.utils.UUIDCache;
import dev.espi.protectionstones.utils.WGUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.block.*;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import com.sk89q.worldguard.protection.managers.RemovalStrategy;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.server.ServerLoadEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ListenerClass implements Listener {

    // Denied players trigger events many times per second (held right click, projectiles), so the
    // "no access" message is rate limited per player instead of being sent on every event.
    private static final long PLOT_DENY_MESSAGE_COOLDOWN_MS = 2000;
    private final Map<UUID, Long> lastPlotDenyMessage = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        // update UUID cache
        UUIDCache.removeUUID(p.getUniqueId());
        UUIDCache.removeName(p.getName());
        UUIDCache.storeUUIDNamePair(p.getUniqueId(), p.getName());

        // allow worldguard to resolve all UUIDs to names
        Bukkit.getScheduler().runTaskAsynchronously(ProtectionStones.getInstance(), () -> UUIDCache.storeWGProfile(p.getUniqueId(), p.getName()));

        // add recipes to player's recipe book
        p.discoverRecipes(RecipeUtil.getRecipeKeys());

        PSPlayer psp = PSPlayer.fromPlayer(p);

        // if by default, players should have protection block placement toggled off
        if (ProtectionStones.getInstance().getConfigOptions().defaultProtectionBlockPlacementOff) {
            ProtectionStones.toggleList.add(p.getUniqueId());
        }

        // tax join message
        if (ProtectionStones.getInstance().getConfigOptions().taxEnabled && ProtectionStones.getInstance().getConfigOptions().taxMessageOnJoin) {
            Bukkit.getScheduler().runTaskAsynchronously(ProtectionStones.getInstance(), () -> {
                int amount = 0;
                for (PSRegion psr : psp.getTaxEligibleRegions()) {
                    for (PSRegion.TaxPayment tp : psr.getTaxPaymentsDue()) {
                        amount += tp.getAmount();
                    }
                }

                if (amount != 0) {
                    PSL.msg(psp, PSL.TAX_JOIN_MSG_PENDING_PAYMENTS.msg().replace("%money%", "" + amount));
                }
            });
        }
    }

    // specifically add WG passthrough bypass here, so other plugins can see the result
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlaceLowPriority(PlaceBlockEvent event) {
        var cause = event.getCause().getRootCause();

        if (cause instanceof Player player && event.getBlocks().size() >= 1) {
            var block = event.getBlocks().get(0);
            if (!ProtectionStones.isProtectBlockItem(player.getInventory().getItemInHand())) {
                return;
            }

            var options = ProtectionStones.getBlockOptions(player.getInventory().getItemInHand());

            if (options != null && options.placingBypassesWGPassthrough) {
                // check if any regions here have the passthrough flag
                // we can't query unfortunately, since null flags seem to equate to ALLOW, when we want it to be DENY
                ApplicableRegionSet set = WGUtils.getRegionManagerWithWorld(event.getWorld()).getApplicableRegions(BukkitAdapter.asBlockVector(block.getLocation()));

                // loop through regions, if any region does not have a passthrough value set, then don't allow
                for (var region : set.getRegions()) {
                    if (region.getFlag(Flags.PASSTHROUGH) == null) {
                        return;
                    }
                }

                // if every region with an explicit passthrough value, then allow passthrough of protection block
                event.setResult(Event.Result.ALLOW);
            }
        }
    }

    // we only create the region after other plugins' event handlers have run
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        BlockHandler.createPSRegion(e);
    }

    // returns the error message, or "" if the player has permission to break the region
    // TODO: refactor and move this to PSRegion, so that /ps unclaim can use the same checks
    private String checkPermissionToBreakProtection(Player p, PSRegion r) {
        // check for destroy permission
        if (!p.hasPermission("protectionstones.destroy")) {
            return PSL.NO_PERMISSION_DESTROY.msg();
        }

        // check if player is owner of region
        if (!r.isOwner(p.getUniqueId()) && !p.hasPermission("protectionstones.superowner")) {
            return PSL.NO_REGION_PERMISSION.msg();
        }

        // cannot break region being rented (prevents splitting merged regions, and breaking as tenant owner)
        if (r.getRentStage() == PSRegion.RentStage.RENTING && !p.hasPermission("protectionstones.superowner")) {
            return PSL.RENT_CANNOT_BREAK_WHILE_RENTING.msg();
        }

        return "";
    }

    // helper method for breaking protection blocks
    // IMPLEMENTATION NOTES: r may be of a non-configured type
    private boolean playerBreakProtection(Player p, PSRegion r) {
        PSProtectBlock blockOptions = r.getTypeOptions();

        // check if player has permission to break the protection
        String error = checkPermissionToBreakProtection(p, r);
        if (!error.isEmpty()) {
            PSL.msg(p, error);
            return false;
        }

        // Call PSBreakEvent
        PSBreakProtectBlockEvent event = new PSBreakProtectBlockEvent(r , p);
        Bukkit.getPluginManager().callEvent(event);
        // don't give ps block to player if the event is cancelled
        if (event.isCancelled()) return false;

        // return protection stone if no drop option is off
        if (blockOptions != null && !blockOptions.noDrop) {
            if (!p.getInventory().addItem(blockOptions.createItem()).isEmpty()) {
                // method will return not empty if item couldn't be added
                if (ProtectionStones.getInstance().getConfigOptions().dropItemWhenInventoryFull) {
                    PSL.msg(p, PSL.NO_ROOM_DROPPING_ON_FLOOR.msg());
                    p.getWorld().dropItem(r.getProtectBlock().getLocation(), blockOptions.createItem());
                } else {
                    PSL.msg(p, PSL.NO_ROOM_IN_INVENTORY.msg());
                    return false;
                }
            }
        }

        // check if removing the region and firing region remove event blocked it
        if (!r.deleteRegion(true, p)) {
            if (!ProtectionStones.getInstance().getConfigOptions().allowMergingHoles) { // side case if the removing creates a hole and those are prevented
                PSL.msg(p, PSL.DELETE_REGION_PREVENTED_NO_HOLES.msg());
            }
            return false;
        }

        PSL.msg(p, PSL.NO_LONGER_PROTECTED.msg());
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent e) {
        // shift-right click block with hand to break
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && !e.isBlockInHand()
                && e.getClickedBlock() != null && ProtectionStones.isProtectBlock(e.getClickedBlock())) {

            PSProtectBlock ppb = ProtectionStones.getBlockOptions(e.getClickedBlock());
            if (ppb.allowShiftRightBreak && e.getPlayer().isSneaking()) {
                PSRegion r = PSRegion.fromLocation(e.getClickedBlock().getLocation());
                if (r != null && playerBreakProtection(e.getPlayer(), r)) { // successful
                    e.getClickedBlock().setType(Material.AIR);
                }
            }
        }
    }

    // this will be the first event handler called in the chain
    // thus we should cancel the event here if possible (so other plugins don't start acting upon it)
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreakLowPriority(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block pb = e.getBlock();

        if (!ProtectionStones.isProtectBlock(pb)) return;

        // check if player has permission to break the protection
        PSRegion r = PSRegion.fromLocation(pb.getLocation());
        if (r != null) {
            String error = checkPermissionToBreakProtection(p, r);
            if (!error.isEmpty()) {
                PSL.msg(p, error);
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block pb = e.getBlock();

        PSProtectBlock blockOptions = ProtectionStones.getBlockOptions(pb);

        // check if block broken is protection stone type
        if (blockOptions == null) return;

        // check if that is actually a protection stone block (owns a region)
        if (!ProtectionStones.isProtectBlock(pb)) {
            // prevent silk touching of protection stone blocks (that aren't holding a region)
            if (blockOptions.preventSilkTouch) {
                ItemStack left = p.getInventory().getItemInMainHand();
                ItemStack right = p.getInventory().getItemInOffHand();
                if (!left.containsEnchantment(Enchantment.SILK_TOUCH) && !right.containsEnchantment(Enchantment.SILK_TOUCH)) {
                    return;
                }
                e.setDropItems(false);
            }
            return;
        }

        PSRegion r = PSRegion.fromLocation(pb.getLocation());

        // break protection
        if (r != null && playerBreakProtection(p, r)) { // successful
            e.setDropItems(false);
            e.setExpToDrop(0);
        } else { // unsuccessful
            e.setCancelled(true);
        }
    }

    // -=-=-=- prevent smelting protection blocks -=-=-=-

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent e) {
        // prevent protect block item to be smelt
        PSProtectBlock options = ProtectionStones.getBlockOptions(e.getSource());
        if (options != null && !options.allowSmeltItem) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFurnaceBurnItem(FurnaceBurnEvent e) {
        // prevent protect block item to be smelt
        Furnace f = (Furnace) e.getBlock().getState();
        if (f.getInventory().getSmelting() != null) {
            PSProtectBlock options = ProtectionStones.getBlockOptions(f.getInventory().getSmelting());
            PSProtectBlock fuelOptions = ProtectionStones.getBlockOptions(f.getInventory().getFuel());
            if ((options != null && !options.allowSmeltItem) || (fuelOptions != null && !fuelOptions.allowSmeltItem)) {
                e.setCancelled(true);
            }
        }
    }

    // -=-=-=- prevent crafting using protection blocks -=-=-=-

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPrepareItemCraft(PrepareItemCraftEvent e) {
        for (ItemStack s : e.getInventory().getMatrix()) {
            PSProtectBlock options = ProtectionStones.getBlockOptions(s);
            if (options != null && !options.allowUseInCrafting) {
                e.getInventory().setResult(new ItemStack(Material.AIR));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent e) {
        if (e.getBlock().getType() != Material.CRAFTER) return;
        if (!(e.getBlock().getState() instanceof Container container)) return;
        for (ItemStack item : container.getInventory().getContents()) {
            if (item == null) continue;
            PSProtectBlock options = ProtectionStones.getBlockOptions(item);
            if (options != null && !options.allowUseInCrafting) {
                e.setCancelled(true);
                e.setResult(new ItemStack(Material.AIR));
                return;
            }
        }
    }

    // -=-=-=- disable grindstone inventory to prevent infinite exp exploit with enchanted_effect option  -=-=-=-
    // see https://github.com/espidev/ProtectionStones/issues/324

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClickEvent(InventoryClickEvent e) {
        if (e.getInventory().getType() == InventoryType.GRINDSTONE) {
            if (ProtectionStones.isProtectBlockItem(e.getCurrentItem())) {
                e.setCancelled(true);
            }
        }
    }


    // -=-=-=- block changes to protection block related events -=-=-=-

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketEmptyEvent e) {
        Block clicked = e.getBlockClicked();
        BlockFace bf = e.getBlockFace();
        Block check = clicked.getWorld().getBlockAt(clicked.getX() + e.getBlockFace().getModX(), clicked.getY() + bf.getModY(), clicked.getZ() + e.getBlockFace().getModZ());
        if (ProtectionStones.isProtectBlock(check)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent e) {
        if (ProtectionStones.isProtectBlock(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent e) {
        if (ProtectionStones.isProtectBlock(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent e) {
        if (ProtectionStones.isProtectBlock(e.getToBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        if (ProtectionStones.isProtectBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent e) {
        if (ProtectionStones.isProtectBlock(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent e) {
        if (ProtectionStones.isProtectBlock(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent e) {
        // unfortunately, the below fix does not really work because Spigot only triggers for the source block, despite
        // what the documentation says: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/event/block/BlockDropItemEvent.html

        // we want to replace protection blocks that have their protection block broken (ex. signs, banners)
        // the block may not exist anymore, and so we have to recreate the isProtectBlock method here
        BlockState bs = e.getBlockState();
        if (!ProtectionStones.isProtectBlockType(bs.getType().toString())) return;

        RegionManager rgm = WGUtils.getRegionManagerWithWorld(bs.getWorld());
        if (rgm == null) return;

        // check if the block is a source block
        ProtectedRegion br = rgm.getRegion(WGUtils.createPSID(bs.getLocation()));
        if (!ProtectionStones.isPSRegion(br) && PSMergedRegion.getMergedRegion(bs.getLocation()) == null) return;

        PSRegion r = PSRegion.fromLocation(bs.getLocation());
        if (r == null) return;

        // puts the block back
        r.unhide();
        e.setCancelled(true);
    }

    // -=-=- prevent protection block piston effects -=-=-

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        pistonUtil(e.getBlocks(), e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        pistonUtil(e.getBlocks(), e);
    }

    private void pistonUtil(List<Block> pushedBlocks, BlockPistonEvent e) {
        for (Block b : pushedBlocks) {
            PSProtectBlock cpb = ProtectionStones.getBlockOptions(b);
            if (cpb != null && ProtectionStones.isProtectBlock(b) && cpb.preventPistonPush) {
                e.setCancelled(true);
            }
        }
    }

    // -=-=- prevent protection blocks from exploding -=-=-

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        boolean isWindCharge = e.getExplosionResult() == ExplosionResult.TRIGGER_BLOCK;
        this.explodeUtil(e.blockList(), e.getBlock().getLocation().getWorld(), isWindCharge);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        boolean isWindCharge = e.getEntity() instanceof AbstractWindCharge || e.getExplosionResult() == ExplosionResult.TRIGGER_BLOCK;
        explodeUtil(e.blockList(), e.getLocation().getWorld(), isWindCharge);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!ProtectionStones.isProtectBlock(e.getBlock())) return;

        // events like ender dragon block break, wither running into block break, etc.
        if (!blockExplodeUtil(e.getBlock().getWorld(), e.getBlock(), false)) {
            // if block shouldn't be exploded, cancel the event
            e.setCancelled(true);
        }
    }

    private void explodeUtil(List<Block> blockList, World w, boolean isWindCharge) {
        // loop through exploded blocks
        for (int i = 0; i < blockList.size(); i++) {
            Block b = blockList.get(i);

            if (ProtectionStones.isProtectBlock(b)) {
                // always remove protection block from exploded list
                blockList.remove(i);
                i--;
            }

            blockExplodeUtil(w, b, isWindCharge);
        }
    }

    // returns whether the block is exploded
    private boolean blockExplodeUtil(World w, Block b, boolean isWindCharge) {
        if (ProtectionStones.isProtectBlock(b)) {
            String id = WGUtils.createPSID(b.getLocation());
            PSProtectBlock blockOptions = ProtectionStones.getBlockOptions(b);

            // if prevent explode
            if (blockOptions.preventExplode) {
                return false;
            }

            if (isWindCharge && blockOptions.preventWindChargeExplode) {
                return false;
            }

            // manually set to air if exploded so there is no natural item drop
            b.setType(Material.AIR);

            // manually add drop
            if (!blockOptions.noDrop) {
                b.getWorld().dropItem(b.getLocation(), blockOptions.createItem());
            }
            // remove region from worldguard if destroy_region_when_explode is enabled
            if (blockOptions.destroyRegionWhenExplode) {
                ProtectionStones.removePSRegion(w, id);
            }
        }
        return true;
    }

    // check player teleporting into region behaviour
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // we only want plugin triggered teleports, ignore natural teleportation
        if (event.getCause() == TeleportCause.ENDER_PEARL || event.getCause() == TeleportCause.CHORUS_FRUIT) return;

        if (event.getPlayer().hasPermission("protectionstones.tp.bypassprevent")) return;

        WorldGuardPlugin wg = WorldGuardPlugin.inst();
        RegionManager rgm = WGUtils.getRegionManagerWithWorld(event.getTo().getWorld());
        BlockVector3 v = BlockVector3.at(event.getTo().getX(), event.getTo().getY(), event.getTo().getZ());

        if (rgm != null) {
            // check if player can teleport into region (no region with preventTeleportIn = true)
            ApplicableRegionSet regions = rgm.getApplicableRegions(v);
            if (regions.getRegions().isEmpty()) return;
            boolean foundNoTeleport = false;
            for (ProtectedRegion r : regions) {
                String f = r.getFlag(FlagHandler.PS_BLOCK_MATERIAL);
                if (f != null && ProtectionStones.getBlockOptions(f) != null && ProtectionStones.getBlockOptions(f).preventTeleportIn)
                    foundNoTeleport = true;
                if (r.getOwners().contains(wg.wrapPlayer(event.getPlayer()))) return;
            }

            if (foundNoTeleport) {
                PSL.msg(event.getPlayer(), PSL.REGION_CANT_TELEPORT.msg());
                event.setCancelled(true);
            }
        }
    }

    // -=-=-=- player defined events -=-=-=-

    private void execEvent(String action, CommandSender s, String player, PSRegion region) {
        if (player == null) player = "";

        // split action_type: action
        String[] sp = action.split(": ");
        if (sp.length == 0) return;

        StringBuilder act = new StringBuilder(sp[1]);
        for (int i = 2; i < sp.length; i++) act.append(": ").append(sp[i]); // add anything extra that has a colon

        act = new StringBuilder(act.toString()
                .replace("%player%", player)
                .replace("%world%", region.getWorld().getName())
                .replace("%region%", region.getName() == null ? region.getId() : region.getName() + " (" + region.getId() + ")")
                .replace("%block_x%", region.getProtectBlock().getX() + "")
                .replace("%block_y%", region.getProtectBlock().getY() + "")
                .replace("%block_z%", region.getProtectBlock().getZ() + ""));

        switch (sp[0]) {
            case "player_command":
                if (s != null) Bukkit.getServer().dispatchCommand(s, act.toString());
                break;
            case "console_command":
                Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), act.toString());
                break;
            case "message":
                if (s != null) s.sendMessage(ChatColor.translateAlternateColorCodes('&', act.toString()));
                break;
            case "global_message":
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', act.toString()));
                }
                ProtectionStones.getPluginLogger().info(ChatColor.translateAlternateColorCodes('&', act.toString()));
                break;
            case "console_message":
                ProtectionStones.getPluginLogger().info(ChatColor.translateAlternateColorCodes('&', act.toString()));
                break;
        }
    }

    @EventHandler
    public void onPSCreate(PSCreateEvent event) {
        if (event.isCancelled()) return;
        if (!event.getRegion().getTypeOptions().eventsEnabled) return;

        // run on next tick (after the region is created to allow for edits to the region)
        Bukkit.getServer().getScheduler().runTask(ProtectionStones.getInstance(), () -> {
            // run custom commands (in config)
            for (String action : event.getRegion().getTypeOptions().regionCreateCommands) {
                execEvent(action, event.getPlayer(), event.getPlayer().getName(), event.getRegion());
            }
        });
    }

    @EventHandler
    public void onPSRemove(PSRemoveEvent event) {
        if (event.isCancelled()) return;
        if (event.getRegion().getTypeOptions() == null) return;
        if (!event.getRegion().getTypeOptions().eventsEnabled) return;

        // run custom commands (in config)
        for (String action : event.getRegion().getTypeOptions().regionDestroyCommands) {
            if (event.getPlayer() == null) {
                execEvent(action, null, null, event.getRegion());
            } else {
                execEvent(action, event.getPlayer(), event.getPlayer().getName(), event.getRegion());
            }
        }
    }

    // ─── Plot deny listeners (block players in ps-plot-denied from accessing plots) ───

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlotDenyBlockBreak(BlockBreakEvent e) {
        if (isPlotDenied(e.getPlayer(), e.getBlock().getLocation(), PlotAction.BUILD)
                || isPlotDenied(e.getPlayer(), getOtherHalf(e.getBlock()), PlotAction.BUILD)) {
            denyWithMessage(e.getPlayer(), e);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlotDenyBlockPlace(BlockPlaceEvent e) {
        checkPlotDenied(e.getPlayer(), e.getBlock().getLocation(), PlotAction.BUILD, e);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlotDenyMultiPlace(BlockMultiPlaceEvent e) {
        for (BlockState state : e.getReplacedBlockStates()) {
            if (isPlotDenied(e.getPlayer(), state.getLocation(), PlotAction.BUILD)) {
                denyWithMessage(e.getPlayer(), e);
                return;
            }
        }
    }

    // Covers right-click (doors, chests, buttons), left-click, and PHYSICAL (pressure plates, tripwires)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlotDenyInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK
                && e.getAction() != Action.LEFT_CLICK_BLOCK
                && e.getAction() != Action.PHYSICAL) return;
        PlotAction action = blockAction(e.getAction(), e.getClickedBlock().getType());
        if (isPlotDenied(e.getPlayer(), e.getClickedBlock().getLocation(), action)
                || isPlotDenied(e.getPlayer(), getOtherHalf(e.getClickedBlock()), action)) {
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (rightClickWouldHaveDoneSomething(e)) sendPlotDenyMessage(e.getPlayer());
                return;
            }
            e.setCancelled(true);
            if (e.getAction() != Action.PHYSICAL) sendPlotDenyMessage(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlotDenyInteractEntity(PlayerInteractEntityEvent e) {
        checkPlotDenied(e.getPlayer(), e.getRightClicked().getLocation(),
                entityAction(e.getRightClicked()), e);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlotDenyEntityDamage(EntityDamageByEntityEvent e) {
        Player responsible = getResponsiblePlayer(e.getDamager());
        if (responsible != null) checkPlotDenied(responsible, e.getEntity().getLocation(), PlotAction.BUILD, e);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlotDenyHangingBreak(HangingBreakByEntityEvent e) {
        Player responsible = getResponsiblePlayer(e.getRemover());
        if (responsible != null) checkPlotDenied(responsible, e.getEntity().getLocation(), PlotAction.BUILD, e);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlotDenyProjectileHit(ProjectileHitEvent e) {
        Player responsible = getResponsiblePlayer(e.getEntity());
        if (responsible == null) return;
        Location hit = e.getHitEntity() != null ? e.getHitEntity().getLocation()
                : e.getHitBlock() != null ? e.getHitBlock().getLocation() : e.getEntity().getLocation();
        // Projectiles are always treated as BUILD: a public interact flag is meant for players
        // standing at the block, not for shooting mechanisms from outside the plot.
        if (!isPlotDenied(responsible, hit, PlotAction.BUILD)) return;
        // Cancelling already suppresses the hit effect. Do not remove the projectile: a thrown trident
        // would be destroyed and the player would lose the item. Drop it in place instead.
        e.setCancelled(true);
        e.getEntity().setVelocity(new Vector(0, 0, 0));
        sendPlotDenyMessage(responsible);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlotDenyBucketEmpty(PlayerBucketEmptyEvent e) {
        Block changed = e.getBlockClicked().getRelative(e.getBlockFace());
        checkPlotDenied(e.getPlayer(), changed.getLocation(), PlotAction.BUILD, e);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlotDenyBucketFill(PlayerBucketFillEvent e) {
        checkPlotDenied(e.getPlayer(), e.getBlockClicked().getLocation(), PlotAction.BUILD, e);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlotDenyExplosion(EntityExplodeEvent e) {
        Player responsible = getResponsiblePlayer(e.getEntity());
        if (responsible == null) return;
        boolean removed = e.blockList().removeIf(
                block -> isPlotDenied(responsible, block.getLocation(), PlotAction.BUILD));
        if (removed) PSL.msg(responsible, PSL.PLOT_NO_ACCESS.msg());
    }

    private Player getResponsiblePlayer(Object source) {
        Object current = source;
        for (int depth = 0; depth < 3 && current != null; depth++) {
            if (current instanceof Player player) return player;
            if (current instanceof Projectile projectile) {
                current = projectile.getShooter();
            } else if (current instanceof TNTPrimed tnt) {
                current = tnt.getSource();
            } else if (current instanceof ProjectileSource projectileSource && projectileSource instanceof Entity) {
                current = (Entity) projectileSource;
            } else {
                return null;
            }
        }
        return null;
    }

    /**
     * What the player is trying to do, so a soft exclusion can be matched against the plot's own
     * public flags. BUILD has no public flag and is therefore always refused.
     */
    private enum PlotAction { BUILD, INTERACT, CONTAINER }

    /**
     * Mirrors how WorldGuard classifies a right click in RegionProtectionListener#onUseBlock:
     * inventory blocks go through chest-access, blocks that count as building go through build,
     * everything else through interact. Materials is WorldGuard's own helper, so the classification
     * stays in sync with the server's WorldGuard version instead of being duplicated here.
     */
    private PlotAction blockAction(Action action, Material clicked) {
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) return PlotAction.BUILD;
        if (Materials.isInventoryBlock(clicked)) return PlotAction.CONTAINER;
        if (Materials.isConsideredBuildingIfUsed(clicked)) return PlotAction.BUILD;
        return PlotAction.INTERACT;
    }

    /**
     * Hangings and armour stands hold player property, so they stay on BUILD even when the plot has
     * a public interact flag. Entities with an inventory follow chest-access.
     */
    private PlotAction entityAction(Entity entity) {
        if (entity instanceof org.bukkit.inventory.InventoryHolder) return PlotAction.CONTAINER;
        if (entity instanceof org.bukkit.entity.Hanging
                || entity instanceof org.bukkit.entity.ArmorStand) return PlotAction.BUILD;
        return PlotAction.INTERACT;
    }

    /** True when the plot itself grants this action to everyone, passers-by included. */
    private boolean allowedByPublicPlotFlag(ProtectedRegion plot, PlotAction action) {
        return switch (action) {
            case INTERACT -> plot.getFlag(Flags.INTERACT) == StateFlag.State.ALLOW;
            case CONTAINER -> plot.getFlag(Flags.CHEST_ACCESS) == StateFlag.State.ALLOW;
            case BUILD -> false;
        };
    }

    // Returns true if the player must be stopped at the given location for the given action
    private boolean isPlotDenied(Player p, org.bukkit.Location loc, PlotAction action) {
        RegionManager rm = WGUtils.getRegionManagerWithWorld(loc.getWorld());
        if (rm == null) return false;
        BlockVector3 bv = BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        for (ProtectedRegion r : rm.getApplicableRegions(bv).getRegions()) {
            if (r.getFlag(FlagHandler.PS_PLOT) == null) continue;

            boolean hardDenied = PlotUtils.isDenied(p.getUniqueId(), r);
            boolean softExcluded = !hardDenied && PlotUtils.isExcluded(p.getUniqueId(), r);
            if (!hardDenied && !softExcluded) continue;

            String parentId = r.getFlag(FlagHandler.PS_PLOT);
            ProtectedRegion parent = rm.getRegion(parentId);
            if (parent != null && parent.isOwner(WorldGuardPlugin.inst().wrapPlayer(p))) continue;

            // A soft exclusion only strips inherited membership; the plot's public flags still apply.
            if (softExcluded && allowedByPublicPlotFlag(r, action)) continue;
            return true;
        }
        return false;
    }

    private void checkPlotDenied(Player p, org.bukkit.Location loc, PlotAction action,
                                 org.bukkit.event.Cancellable event) {
        if (isPlotDenied(p, loc, action)) {
            denyWithMessage(p, event);
        }
    }

    private void denyWithMessage(Player player, org.bukkit.event.Cancellable event) {
        event.setCancelled(true);
        sendPlotDenyMessage(player);
    }

    // Rate limited so held clicks and repeated projectiles cannot flood the chat.
    private void sendPlotDenyMessage(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastPlotDenyMessage.get(player.getUniqueId());
        if (last != null && now - last < PLOT_DENY_MESSAGE_COOLDOWN_MS) return;
        lastPlotDenyMessage.put(player.getUniqueId(), now);
        PSL.msg(player, PSL.PLOT_NO_ACCESS.msg());
    }

    /**
     * Right clicking plain terrain does nothing even without a plot, so telling the player they have
     * no access would only be noise. Notify only when the click could actually have done something.
     */
    private boolean rightClickWouldHaveDoneSomething(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable()) return true;
        ItemStack inHand = e.getItem();
        if (inHand == null) return false;
        Material type = inHand.getType();
        return type.isBlock()
                || type == Material.WATER_BUCKET
                || type == Material.LAVA_BUCKET
                || type == Material.POWDER_SNOW_BUCKET
                || type == Material.BUCKET
                || type == Material.FLINT_AND_STEEL
                || type == Material.FIRE_CHARGE
                || type == Material.ARMOR_STAND
                || type == Material.ITEM_FRAME
                || type == Material.GLOW_ITEM_FRAME
                || type == Material.PAINTING
                || type == Material.END_CRYSTAL;
    }

    private Location getOtherHalf(Block block) {
        if (block.getBlockData() instanceof org.bukkit.block.data.Bisected bisected) {
            return block.getRelative(bisected.getHalf() == org.bukkit.block.data.Bisected.Half.TOP
                    ? BlockFace.DOWN : BlockFace.UP).getLocation();
        }
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Bed bed) {
            BlockFace direction = bed.getPart() == org.bukkit.block.data.type.Bed.Part.HEAD
                    ? bed.getFacing().getOppositeFace() : bed.getFacing();
            return block.getRelative(direction).getLocation();
        }
        return block.getLocation();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPSRemoveCascadePlots(PSRemoveEvent event) {
        if (event.isCancelled()) return;

        String removedId = event.getRegion().getId();
        World world = event.getRegion().getWorld();
        Player cause = event.getPlayer();
        Bukkit.getScheduler().runTask(ProtectionStones.getInstance(), () -> {
            RegionManager rm = WGUtils.getRegionManagerWithWorld(world);
            if (rm == null || rm.getRegion(removedId) != null) return;
            List<ProtectedRegion> children = PlotUtils.childrenOf(
                    PlotUtils.indexByParent(rm.getRegions().values()), removedId);
            for (ProtectedRegion child : children) {
                rm.removeRegion(child.getId(), RemovalStrategy.UNSET_PARENT_IN_CHILDREN);
            }
            if (cause != null && !children.isEmpty()) {
                PSL.msg(cause, PSL.PLOT_CHILD_REMOVED.msg()
                        .replace("%count%", String.valueOf(children.size())));
            }
        });
    }

    // Runs once after the server finishes loading all worlds and plugins.
    // Cleans up any orphan plots whose parent PS region no longer exists —
    // covers edge cases like /rg remove via console or data corruption.
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        int total = 0;
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            RegionManager rm = WGUtils.getRegionManagerWithWorld(w);
            if (rm == null) continue;
            total += cleanOrphanPlots(w, rm);
        }
        if (total > 0) {
            ProtectionStones.getPluginLogger().info("[Plots] Cleaned " + total + " orphan plot(s) on startup.");
        }
    }

    static int cleanOrphanPlots(org.bukkit.World world, RegionManager rm) {
        java.util.Map<String, String> toRemove = new java.util.LinkedHashMap<>();
        for (ProtectedRegion r : rm.getRegions().values()) {
            String parentId = r.getFlag(FlagHandler.PS_PLOT);
            if (parentId == null) continue;
            ProtectedRegion parent = rm.getRegion(parentId);
            String reason = null;
            if (parent == null) reason = "missing parent";
            else if (PSRegion.fromWGRegion(world, parent) == null) reason = "parent is not a ProtectionStones region";
            else if (r.getParent() == null || !parentId.equals(r.getParent().getId())) reason = "WorldGuard parent mismatch";
            else if (!(r instanceof com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion)) reason = "plot is not cuboid";
            else if (!PlotUtils.fullyContains(parent, r.getMinimumPoint(), r.getMaximumPoint())) reason = "plot is outside parent";
            if (reason != null) toRemove.put(r.getId(), reason);
        }
        for (java.util.Map.Entry<String, String> entry : toRemove.entrySet()) {
            rm.removeRegion(entry.getKey(), RemovalStrategy.UNSET_PARENT_IN_CHILDREN);
            ProtectionStones.getPluginLogger().info("[Plots] Removed invalid plot " + entry.getKey()
                    + " in world " + world.getName() + ": " + entry.getValue());
        }
        return toRemove.size();
    }

}

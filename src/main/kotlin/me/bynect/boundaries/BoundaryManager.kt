package me.bynect.boundaries

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentBuilder
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.BlockState
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import java.nio.ByteBuffer

object BoundaryManager : Listener {

    private val plugin = Bukkit.getPluginManager().getPlugin("boundaries") as Boundaries

    // This tag is used to store the serialized items taken from the inventory
    val inventoryTag = NamespacedKey(plugin, "savedInventory")
    val inventoryType = PersistentDataType.LIST.listTypeFrom(PersistentDataType.BYTE_ARRAY)

    // This tag is used to store the serialized chunk locations with guides
    val blocksTag = NamespacedKey(plugin, "guideBlocks")
    val blocksType = PersistentDataType.LIST.listTypeFrom(PersistentDataType.BYTE_ARRAY)

    private fun spawnActionBar(player: Player) {
        class ShowMode : BukkitRunnable() {
            override fun run() {
                if (player.persistentDataContainer.has(inventoryTag))
                    player.sendActionBar(
                        Component
                            .text("Boundary editor mode")
                            .color(NamedTextColor.GOLD)
                            .decorate(TextDecoration.ITALIC)
                    )
                else
                    cancel()
            }
        }
        ShowMode().runTaskTimer(plugin, 2L, 20L)
    }

    private fun isTracked(player: Player): Boolean {
        return player.persistentDataContainer.has(inventoryTag, inventoryType)
    }

    private fun trackPlayer(player: Player) {
        val items = serializeItems(player.inventory)
        player.persistentDataContainer.set(inventoryTag, inventoryType, items)

        (0..8).forEach { i -> player.inventory.setItem(i, null) }
        player.inventory.setItemInOffHand(null)

        Bukkit.getLogger().info("Track boundary mode " + player.name)
    }

    private fun untrackPlayer(player: Player) {
        player.persistentDataContainer.remove(inventoryTag)
        player.persistentDataContainer.remove(blocksTag)
        Bukkit.getLogger().info("Untrack boundary mode " + player.name)
    }

    private fun serializeItems(inventory: PlayerInventory): List<ByteArray> {
        return (0..8)
            .map { i -> inventory.getItem(i) }
            .plus(inventory.itemInOffHand)
            .map { item -> if (item == null || item.type == Material.AIR) byteArrayOf() else item.serializeAsBytes() }
    }

    private fun deserializeItems(bytes: List<ByteArray>): List<ItemStack?> {
        return bytes.map {
            data -> if (data.isNotEmpty()) ItemStack.deserializeBytes(data) else null
        }
    }

    private fun serializeLocation(location: Location): ByteArray {
        return ByteBuffer.allocate(Double.SIZE_BYTES * 3 + location.world.name.length)
            .putDouble(location.x)
            .putDouble(location.y)
            .putDouble(location.z)
            .put(location.world.name.toByteArray())
            .array()
    }

    private fun deserializeLocation(array: ByteArray): Location {
        val world = Bukkit.getWorld(String(array, Double.SIZE_BYTES * 3, array.size - Double.SIZE_BYTES * 3))
        val buffer = ByteBuffer.wrap(array)
        return Location(world, buffer.getDouble(), buffer.getDouble(), buffer.getDouble())
    }

    private fun isSelected(player: Player, location: Location): Boolean {
        val locations = player.persistentDataContainer.get(blocksTag, blocksType) ?: return false
        val serialized = serializeLocation(location)
        return locations.any { bytes -> bytes.contentEquals(serialized) }
    }

    private fun selectGuide(player: Player, location: Location) {
        val changes: MutableList<BlockState> = mutableListOf()
        for (x in (0..1)) {
            for (z in (0..1)) {
                for (y in (-64..320)) {
                    val state = location.chunk.getBlock(x * 15, y, z * 15).state
                    state.type = Material.YELLOW_CONCRETE
                    changes.add(state)
                }
            }
        }
        player.sendBlockChanges(changes)
    }

    private fun deselectGuide(player: Player, location: Location) {
        val changes: MutableList<BlockState> = mutableListOf()
        for (x in (0..1)) {
            for (z in (0..1)) {
                for (y in (-64..320)) {
                    val state = location.chunk.getBlock(x * 15, y, z * 15).state
                    state.update(true)
                    changes.add(state)
                }
            }
        }
        player.sendBlockChanges(changes)
    }

    private fun resetGuides(player: Player) {
        val chunks = player.persistentDataContainer.get(blocksTag, blocksType)
        if (chunks != null) {
            for (location in chunks)
                deselectGuide(player, deserializeLocation(location))
        }
    }

    fun enterBoundaryMode(player: Player): Boolean {
        if (isTracked(player))
            return false

        player.persistentDataContainer.remove(blocksTag)
        trackPlayer(player)

        val wand = ItemStack.of(Material.BOOK)
        val wandMeta = wand.itemMeta

        wandMeta.displayName(
            Component
                .text("Boundary editor")
                .decorate(TextDecoration.BOLD)
                .color(NamedTextColor.LIGHT_PURPLE)
        )

        wandMeta.lore(
            listOf(
                Component
                    .text("Select the boundary you want to edit or an")
                    .color(NamedTextColor.WHITE),
                Component
                    .text("unclaimed territory you want to occupy")
                    .color(NamedTextColor.WHITE),
                Component.text(""),
                Component
                    .text("Selected chunks: 0")
                    .color(NamedTextColor.GOLD),
                Component.text(""),
                Component
                    .text("Drop this item to quit boundary mode")
                    .color(NamedTextColor.LIGHT_PURPLE)
            ),
        )

        wandMeta.addEnchant(Enchantment.INFINITY, 1, true)
        wandMeta.addItemFlags(ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS)
        wand.itemMeta = wandMeta

        player.inventory.setItemInMainHand(wand)

        spawnActionBar(player)
        return true
    }

    private fun quitBoundaryMode(player: Player) {
        resetGuides(player)

        val serialized = player.persistentDataContainer.get(inventoryTag, inventoryType)!!
        val items = deserializeItems(serialized)
        items.slice(0..8).zip((0..8)).forEach { (item, slot) ->
            player.inventory.setItem(slot, item)
        }
        player.inventory.setItemInOffHand(items.last())

        untrackPlayer(player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onItemClick(event: PlayerInteractEvent) {
        val player = event.player
        if (isTracked(player)) {
            val list = player.persistentDataContainer.get(blocksTag, blocksType) ?: listOf()
            var size = list.size

            if (event.action.isRightClick) {
                if (player.isSneaking) {
                    if (size == 0) {
                        player.sendActionBar(
                            Component
                                .text("Nothing to claim")
                                .color(NamedTextColor.RED)
                        )
                    } else {
                        player.sendActionBar(
                            Component
                                .text("Claiming $size chunks")
                                .color(NamedTextColor.GOLD)
                                .decorate(TextDecoration.BOLD)
                        )

                        for (location in list)
                            ChunkManager.changeOwner(deserializeLocation(location), player.name)
                    }

                    quitBoundaryMode(player)
                } else if (event.interactionPoint != null) {
                    val chunk = event.interactionPoint!!.chunk
                    val center = chunk.getBlock(8, 0, 8).location
                    val serialized = serializeLocation(center)

                    if (isSelected(player, center)) {
                        Bukkit.getLogger().info("${player.name} deselected $center")
                        deselectGuide(player, center)
                        player.persistentDataContainer.set(blocksTag, blocksType,
                            list.filterNot { bytes -> bytes.contentEquals(serialized) } )
                        size--
                    } else {
                        Bukkit.getLogger().info("${player.name} selected $center")
                        selectGuide(player, center)
                        player.persistentDataContainer.set(blocksTag, blocksType,
                            list.plusElement(serialized))
                        size++
                    }
                }
            }

            event.isCancelled = true
        }
    }

    @EventHandler
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        if (isTracked(player)) {
            event.isCancelled = true
            quitBoundaryMode(player)
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = Bukkit.getPlayer(event.whoClicked.name)!!
        if (isTracked(player))
            quitBoundaryMode(player)
    }

    @EventHandler
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (isTracked(player)) {
            event.isCancelled = true
            quitBoundaryMode(player)
        }
    }

    @EventHandler
    fun onDrop(event: PlayerDropItemEvent) {
        val player = event.player
        if (isTracked(player)) {
            quitBoundaryMode(player)
            //event.itemDrop.remove()
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.player
        if (isTracked(player)) {
            resetGuides(player)

            val serialized = player.persistentDataContainer.get(inventoryTag, inventoryType)!!
            val items = deserializeItems(serialized)
            event.drops.addAll(items)

            untrackPlayer(player)
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (isTracked(player)) {
            val chunks = player.persistentDataContainer.get(blocksTag, blocksType) ?: return

            class ShowGuides : Runnable {
                override fun run() {
                    for (location in chunks)
                        selectGuide(player, deserializeLocation(location))
                }
            }
            Bukkit.getServer().scheduler.runTaskLater(plugin, ShowGuides(), 20L)
            spawnActionBar(player)
        }
    }
}
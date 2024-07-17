package me.bynect.boundaries

import net.kyori.adventure.text.Component
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
import java.nio.ByteBuffer

object BoundaryManager : Listener {

    private val plugin = Bukkit.getPluginManager().getPlugin("boundaries") as Boundaries

    // This tag is used to store the serialized items taken from the inventory
    val inventoryTag = NamespacedKey(plugin, "inventory")
    val inventoryType = PersistentDataType.LIST.listTypeFrom(PersistentDataType.BYTE_ARRAY)

    // This tag is used to store the serialized chunk locations with guides
    val blocksTag = NamespacedKey(plugin, "guides")
    val blocksType = PersistentDataType.LIST.listTypeFrom(PersistentDataType.BYTE_ARRAY)

    // This tag is used to store the owner of a chunk
    val chunkTag = NamespacedKey(plugin, "chunk")
    val chunkType = PersistentDataType.STRING

    // This tag is used to store the list of tracked users
    val boundaryTag = NamespacedKey(plugin, "mode")
    val boundaryType = PersistentDataType.LIST.listTypeFrom(PersistentDataType.STRING)

    // FIXME: Ugly
    private fun findWand(player: Player): ItemStack {
        var wand: ItemStack? = null
        for (i in (0..8)) {
            val item = player.inventory.getItem(i)
            if (isWand(item)) {
                wand = item
                break
            }
        }
        return wand!!
    }

    private fun isWand(item: ItemStack?): Boolean {
        return item != null && item.hasItemMeta() &&
                item.itemMeta.persistentDataContainer.has(inventoryTag, inventoryType)
    }

    private fun isTracked(player: Player): Boolean {
        val pdc = Bukkit.getServer().worlds[0].persistentDataContainer
        return pdc.get(boundaryTag, boundaryType)?.contains(player.name) ?: false
    }

    private fun trackPlayer(player: Player) {
        val pdc = Bukkit.getServer().worlds[0].persistentDataContainer
        val list = pdc.get(boundaryTag, boundaryType) ?: listOf()
        pdc.set(boundaryTag, boundaryType, list.plus(player.name))
        Bukkit.getLogger().info("Track boundary mode " + player.name)
    }

    private fun untrackPlayer(player: Player) {
        val pdc = Bukkit.getServer().worlds[0].persistentDataContainer
        val list = pdc.get(boundaryTag, boundaryType)?.minus(player.name) ?: listOf()
        pdc.set(boundaryTag, boundaryType, list)
        Bukkit.getLogger().info("Untrack boundary mode " + player.name)
    }

    private fun serializeItems(inventory: PlayerInventory): ItemStack {
        val wand = ItemStack.of(Material.BLAZE_ROD)
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
                Component
                    .text("Drop this item to quit boundary mode")
                    .color(NamedTextColor.LIGHT_PURPLE)
            ),
        )

        wandMeta.addEnchant(Enchantment.INFINITY, 1, true)
        wandMeta.addItemFlags(ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS)

        val serialized = (0..8)
            .map { i -> inventory.getItem(i) }
            .plus(inventory.itemInOffHand)
            .map { item -> if (item == null || item.type == Material.AIR) byteArrayOf() else item.serializeAsBytes() }

        wandMeta.persistentDataContainer.set(inventoryTag, inventoryType, serialized)
        wand.itemMeta = wandMeta
        return wand
    }

    private fun deserializeItems(wand: ItemStack): List<ItemStack?> {
        return wand.persistentDataContainer.get(inventoryTag, inventoryType)!!.map { data ->
            if (data.isNotEmpty()) ItemStack.deserializeBytes(data) else null
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

    private fun isSelected(wand: ItemStack, location: Location): Boolean {
        val locations = wand.itemMeta.persistentDataContainer.get(blocksTag, blocksType)
        val serialized = serializeLocation(location)
        return locations != null && locations.any { bytes -> bytes.contentEquals(serialized) }
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

    private fun resetGuides(player: Player, wand: ItemStack) {
        val chunks = wand.itemMeta.persistentDataContainer.get(blocksTag, blocksType)
        if (chunks != null) {
            for (location in chunks)
                deselectGuide(player, deserializeLocation(location))
        }
    }

    fun enterBoundaryMode(player: Player): Boolean {
        if (isTracked(player))
            return false

        val wand = serializeItems(player.inventory)
        (0..8).forEach { i -> player.inventory.setItem(i, null) }
        player.inventory.setItemInOffHand(null)
        player.inventory.setItemInMainHand(wand)

        trackPlayer(player)
        return true
    }

    private fun quitBoundaryMode(player: Player) {
        val wand = findWand(player)
        quitBoundaryMode(player, wand)
    }

    private fun quitBoundaryMode(player: Player, wand: ItemStack) {
        resetGuides(player, wand)

        val items = deserializeItems(wand)
        items.slice(0..8).zip((0..8)).forEach { (item, slot) ->
            player.inventory.setItem(slot, item)
        }
        player.inventory.setItemInOffHand(items.last())
        untrackPlayer(player)
    }

    @EventHandler
    fun onItemClick(event: PlayerInteractEvent) {
        val player = event.player
        if (isTracked(player) && isWand(event.item)) {
            event.isCancelled = true
            val wand = event.item!!

            if (event.action.isRightClick && event.interactionPoint != null) {
                val chunk = event.interactionPoint!!.chunk
                val center = chunk.getBlock(8, 0, 8).location

                val wandMeta = wand.itemMeta
                val list = wandMeta.persistentDataContainer.get(blocksTag, blocksType) ?: listOf()
                val serialized = serializeLocation(center)

                if (isSelected(wand, center)) {
                    Bukkit.getLogger().info("DESEL $list")
                    deselectGuide(player, center)
                    wandMeta.persistentDataContainer.set(blocksTag, blocksType,
                        list.filterNot { bytes -> bytes.contentEquals(serialized) } )
                } else {
                    Bukkit.getLogger().info("SEL $list")
                    selectGuide(player, center)
                    wandMeta.persistentDataContainer.set(blocksTag, blocksType,
                        list.plusElement(serialized))
                }

                // NOTE: Reapply itemMeta!!!
                wand.itemMeta = wandMeta
            }
        }
    }

    @EventHandler
    fun onPickup(event: EntityPickupItemEvent) {
        if (isWand(event.item.itemStack))
            event.item.remove()
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
            quitBoundaryMode(player, event.itemDrop.itemStack)
            event.itemDrop.remove()
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.player
        if (isTracked(player)) {
            val predicate = { item: ItemStack -> isWand(item) }
            val wand = event.drops.find(predicate)
            event.drops.removeIf(predicate)

            if (wand != null) {
                resetGuides(player, wand)

                val items = deserializeItems(wand)
                event.drops.addAll(items)
            }
            untrackPlayer(player)
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (isTracked(player)) {
            val wand = findWand(player)
            val chunks = wand.itemMeta.persistentDataContainer.get(blocksTag, blocksType) ?: return

            class ShowGuides : Runnable {
                override fun run() {
                    for (location in chunks)
                        selectGuide(player, deserializeLocation(location))
                }
            }
            Bukkit.getServer().scheduler.runTaskLaterAsynchronously(plugin, ShowGuides(), 20L)
        }
    }
}
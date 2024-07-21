@file:Suppress("UnstableApiUsage")

package me.bynect.boundaries

import me.bynect.boundaries.ChunkManager.deselectAllChunks
import me.bynect.boundaries.ChunkManager.deselectChunk
import me.bynect.boundaries.ChunkManager.deselectGuide
import me.bynect.boundaries.ChunkManager.deserializeLocation
import me.bynect.boundaries.ChunkManager.getOwner
import me.bynect.boundaries.ChunkManager.getSelector
import me.bynect.boundaries.ChunkManager.isSelectedBy
import me.bynect.boundaries.ChunkManager.selectChunk
import me.bynect.boundaries.ChunkManager.selectGuide
import me.bynect.boundaries.ChunkManager.serializeLocation
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.persistence.PersistentDataType


object BoundaryManager : Listener {

    private val plugin = Bukkit.getPluginManager().getPlugin("boundaries") as Boundaries

    // This tag is used to store the serialized items taken from the inventory
    val inventoryTag = NamespacedKey(plugin, "savedInventory")
    val inventoryType = PersistentDataType.LIST.listTypeFrom(PersistentDataType.BYTE_ARRAY)

    // This tag is used to store the serialized locations of the selected chunks
    val chunksTag = NamespacedKey(plugin, "selectChunks")
    val chunksType = PersistentDataType.LIST.listTypeFrom(PersistentDataType.BYTE_ARRAY)

    fun isTracked(player: Player): Boolean {
        return player.persistentDataContainer.has(inventoryTag, inventoryType)
    }

    private fun untrackPlayer(player: Player) {
        player.persistentDataContainer.remove(inventoryTag)
        player.persistentDataContainer.remove(chunksTag)
        Bukkit.getLogger().info("Untrack boundary mode " + player.name)
    }

    private fun serializeItems(inventory: PlayerInventory): List<ByteArray> {
        return listOf(inventory.itemInMainHand, inventory.itemInOffHand)
            .map { item -> if (item.type == Material.AIR) byteArrayOf() else item.serializeAsBytes() }
    }

    private fun deserializeItems(bytes: List<ByteArray>): List<ItemStack?> {
        return bytes.map { data ->
            if (data.isNotEmpty()) ItemStack.deserializeBytes(data) else null
        }
    }

    fun enterBoundaryMode(player: Player): Boolean {
        if (isTracked(player))
            return false

        player.persistentDataContainer.remove(chunksTag)
        val items = serializeItems(player.inventory)
        player.persistentDataContainer.set(inventoryTag, inventoryType, items)

        val wand = ItemStack.of(Material.BREEZE_ROD)
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
                    .text("Drop this item or change slot to quit boundary mode")
                    .color(NamedTextColor.LIGHT_PURPLE)
            ),
        )

        wandMeta.addEnchant(Enchantment.INFINITY, 1, true)
        wandMeta.addItemFlags(ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS)
        wand.itemMeta = wandMeta

        player.inventory.setItemInMainHand(wand)
        player.inventory.setItemInOffHand(null)

        Bukkit.getLogger().info("Track boundary mode " + player.name)
        return true
    }

    private fun quitBoundaryMode(player: Player) {
        val serialized = player.persistentDataContainer.get(inventoryTag, inventoryType)!!
        val items = deserializeItems(serialized)
        player.inventory.setItemInMainHand(items[0])
        player.inventory.setItemInOffHand(items[1])

        deselectAllChunks(player)
        untrackPlayer(player)
    }

    @EventHandler
    fun onItemClick(event: PlayerInteractEvent) {
        val player = event.player
        if (isTracked(player)) {
            val list = player.persistentDataContainer.get(chunksTag, chunksType) ?: listOf()
            val size = list.size

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
                } else if (event.hand == EquipmentSlot.HAND) {
                    val chunk = event.interactionPoint?.chunk ?: player.chunk
                    val center = chunk.getBlock(8, 0, 8).location
                    val serialized = serializeLocation(center)

                    val owner = getOwner(center)
                    val selector = getSelector(center)

                    if (owner != null && owner != player.name) {
                        player.sendActionBar(
                            Component
                                .text("Chunk is already owned by ")
                                .color(NamedTextColor.RED)
                                .append(
                                    Component
                                        .text(owner)
                                        .color(NamedTextColor.WHITE)
                                        .decorate(TextDecoration.BOLD)
                                )
                        )
                    } else if (selector != null && selector != player.name) {
                        player.sendActionBar(
                            Component
                                .text("Chunk is already selected by ")
                                .color(NamedTextColor.RED)
                                .append(
                                    Component
                                        .text(selector)
                                        .color(NamedTextColor.WHITE)
                                        .decorate(TextDecoration.BOLD)
                                )
                        )
                    } else {
                        if (isSelectedBy(player, center)) {
                            Bukkit.getLogger().info("${player.name} deselected $center")
                            player.persistentDataContainer.set(chunksTag, chunksType,
                                list.filterNot { bytes -> bytes.contentEquals(serialized) })

                            deselectChunk(center)
                            deselectGuide(player, center)
                        } else {
                            Bukkit.getLogger().info("${player.name} selected $center")
                            player.persistentDataContainer.set(
                                chunksTag, chunksType,
                                list.plusElement(serialized)
                            )

                            selectChunk(player, center)
                            selectGuide(player, center)
                        }
                    }
                }
            }

            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockDamage(event: BlockDamageEvent) {
        val player = event.player
        if (isTracked(player))
            event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        if (isTracked(player)) {
            event.isCancelled = true
            quitBoundaryMode(player)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = Bukkit.getPlayer(event.whoClicked.name)!!
        if (isTracked(player))
            quitBoundaryMode(player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (isTracked(player)) {
            event.isCancelled = true
            quitBoundaryMode(player)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        val player = event.player
        if (isTracked(player)) {
            quitBoundaryMode(player)
            event.itemDrop.remove()
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.player
        if (isTracked(player)) {
            val serialized = player.persistentDataContainer.get(inventoryTag, inventoryType)!!
            val items = deserializeItems(serialized)
            event.drops.addAll(items)
            event.drops.remove(player.inventory.itemInMainHand)

            deselectAllChunks(player)
            untrackPlayer(player)
        }
    }
}
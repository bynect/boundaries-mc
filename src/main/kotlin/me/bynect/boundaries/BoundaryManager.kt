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
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.*
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
import org.bukkit.inventory.*
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
                    .text("Select the boundary you want to")
                    .color(NamedTextColor.WHITE),
                Component
                    .text("edit or an unclaimed territory")
                    .color(NamedTextColor.WHITE),
                Component
                    .text("with ")
                    .color(NamedTextColor.WHITE)
                    .append(
                        Component
                            .text("left click")
                            .color(NamedTextColor.GOLD)
                    ),
                Component.text(""),
                Component
                    .text("Open boundary editor options with")
                    .color(NamedTextColor.WHITE),
                Component
                    .text("right click")
                    .color(NamedTextColor.GOLD),
                Component.text(""),
                Component
                    .text("Drop this item or change slot to")
                    .color(NamedTextColor.DARK_PURPLE),
                Component
                    .text("quit boundary mode")
                    .color(NamedTextColor.DARK_PURPLE),
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

    private val menuInventory = run {
        val title = Component
            .text("Boundary mode menu")
            .color(NamedTextColor.LIGHT_PURPLE)
            .decorate(TextDecoration.BOLD)
        val inventory = Bukkit.createInventory(null, 27, title)

        val claim = ItemStack.of(Material.LIME_CONCRETE)
        val claimMeta = claim.itemMeta

        claimMeta.displayName(Component.text("Claim chunks"))
        claimMeta.lore(
            listOf(Component.text("Claim ownership of the selected chunks")),
        )
        claim.itemMeta = claimMeta
        inventory.setItem(10, claim)

        val unclaim = ItemStack.of(Material.RED_CONCRETE)
        val unclaimMeta = unclaim.itemMeta

        unclaimMeta.displayName(Component.text("Release chunks"))
        unclaimMeta.lore(
            listOf(Component.text("Release ownership of the selected chunks")),
        )
        unclaim.itemMeta = unclaimMeta
        inventory.setItem(12, unclaim)

        val change = ItemStack.of(Material.GRAY_CONCRETE)
        val changeMeta = change.itemMeta

        changeMeta.displayName(Component.text("Change permissions"))
        changeMeta.lore(
            listOf(Component.text("Change the permissions for the chunks")),
        )
        change.itemMeta = changeMeta
        inventory.setItem(14, change)

        val reset = ItemStack.of(Material.WHITE_CONCRETE)
        val resetMeta = reset.itemMeta

        resetMeta.displayName(Component.text("Reset selection"))
        resetMeta.lore(
            listOf(Component.text("Deselect all chunks")),
        )
        reset.itemMeta = resetMeta
        inventory.setItem(16, reset)

        inventory
    }

    @EventHandler
    fun onItemClick(event: PlayerInteractEvent) {
        val player = event.player
        if (isTracked(player)) {
            if (event.hand == EquipmentSlot.HAND) {
                val list = player.persistentDataContainer.get(chunksTag, chunksType) ?: listOf()
                val particle = player.location
                particle.y += 1

                if (event.action.isRightClick) {
                    player.openInventory(menuInventory)
                } else if (event.action.isLeftClick) {
                    val chunk = event.clickedBlock?.chunk ?: player.chunk
                    val center = chunk.getBlock(8, 0, 8).location
                    val serialized = serializeLocation(center)

                    val owner = getOwner(center)
                    val selector = getSelector(center)

                    if (owner != null && owner != player.name) {
                        player.spawnParticle(Particle.SMOKE, particle, 5)
                        player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_SHULKER_TELEPORT, Sound.Source.PLAYER, 1f, 1f))

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
                        player.spawnParticle(Particle.SMOKE, particle, 5)
                        player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_SHULKER_TELEPORT, Sound.Source.PLAYER, 1f, 1f))

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
                        if (isSelectedBy(center, player)) {
                            Bukkit.getLogger().info("${player.name} deselected $center")

                            player.spawnParticle(Particle.FIREWORK, particle, 10)

                            player.persistentDataContainer.set(chunksTag, chunksType,
                                list.filterNot { bytes -> bytes.contentEquals(serialized) })

                            deselectChunk(center)
                            deselectGuide(player, center)
                        } else {
                            Bukkit.getLogger().info("${player.name} selected $center")

                            player.spawnParticle(Particle.HAPPY_VILLAGER, particle, 10)

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
        if (isTracked(player)) {
            if (event.clickedInventory?.equals(menuInventory) == true) {
                event.isCancelled = true

                val item = event.currentItem ?: return
                val list = player.persistentDataContainer.get(chunksTag, chunksType) ?: listOf()

                player.closeInventory()
                if (item.type == Material.LIME_CONCRETE) {
                    var size = 0
                    for (serialized in list) {
                        val location = deserializeLocation(serialized)
                        if (ChunkManager.isOwned(location))
                            continue

                        assert(ChunkManager.changeOwner(location, player.name))
                        size++
                    }

                    if (size == 0) {
                        player.sendActionBar(
                            Component
                                .text("Nothing to claim")
                                .color(NamedTextColor.RED)
                        )
                    } else {
                        player.sendActionBar(
                            Component
                                .text("Claimed $size chunks")
                                .color(NamedTextColor.GOLD)
                                .decorate(TextDecoration.BOLD)
                        )
                    }

                    quitBoundaryMode(player)
                } else if (item.type == Material.RED_CONCRETE) {
                    var size = 0
                    for (serialized in list) {
                        val location = deserializeLocation(serialized)
                        if (!ChunkManager.isOwnedBy(location, player))
                            continue

                        ChunkManager.changeOwner(location, null)
                        size++
                    }

                    if (size == 0) {
                        player.sendActionBar(
                            Component
                                .text("Nothing to release")
                                .color(NamedTextColor.RED)
                        )
                    } else {
                        player.sendActionBar(
                            Component
                                .text("Released $size chunks")
                                .color(NamedTextColor.GOLD)
                                .decorate(TextDecoration.BOLD)
                        )
                    }

                    quitBoundaryMode(player)
                } else if (item.type == Material.GRAY_CONCRETE) {

                } else if (item.type == Material.WHITE_CONCRETE) {
                    deselectAllChunks(player)
                    player.sendActionBar(
                        Component
                            .text("Cleared boundary selection")
                            .color(NamedTextColor.WHITE)
                            .decorate(TextDecoration.BOLD)
                    )
                }
            } else {
                quitBoundaryMode(player)
            }
        }
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
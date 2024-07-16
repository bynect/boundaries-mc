package me.bynect.boundaries

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.WorldBorder
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.persistence.PersistentDataType

object BoundaryManager : Listener {

    private val plugin = Bukkit.getPluginManager().getPlugin("boundaries") as Boundaries

    // This tag is used to store the serialized items taken from the inventory
    val inventoryTag = NamespacedKey(plugin, "inventory")
    val inventoryType = PersistentDataType.LIST.listTypeFrom(PersistentDataType.BYTE_ARRAY)

    // This tag is used to store the list of tracked users
    val boundaryTag = NamespacedKey(plugin, "mode")
    val boundaryType = PersistentDataType.LIST.listTypeFrom(PersistentDataType.STRING)

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
        // FIXME: Ugly
        var wand: ItemStack? = null
        for (i in (0..8)) {
            val item = player.inventory.getItem(i)
            if (isWand(item)) {
              wand = item
              break
            }
        }

        quitBoundaryMode(player, wand!!)
    }

    private fun quitBoundaryMode(player: Player, wand: ItemStack) {
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
        if (isTracked(player)) {
            event.isCancelled = isWand(event.item)
            val location = event.interactionPoint

            if (event.action.isRightClick && location != null) {
                val border = Bukkit.getServer().createWorldBorder()
                border.size = 16.0
                border.warningDistance = 0
                border.center = location.chunk.getBlock(8, 0, 8).location
                border.damageAmount = 0.0
                border.warningTime = 1000000000

                player.worldBorder = border

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
                val items = deserializeItems(wand)
                event.drops.addAll(items)
            }
            untrackPlayer(player)
        }
    }
}
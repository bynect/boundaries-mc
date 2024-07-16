package me.bynect.boundaries

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

object BoundaryManager : Listener {

    private val boundaryMode = HashMap<String, List<ItemStack?>>()

    private val plugin = Bukkit.getPluginManager().getPlugin("boundaries") as Boundaries

    var boundaryTag = NamespacedKey(plugin, "boundaryTag")

    private fun isWand(item: ItemStack?): Boolean {
        return item != null && item.hasItemMeta() &&
                item.itemMeta.persistentDataContainer.has(boundaryTag, PersistentDataType.STRING)
    }

    fun enterBoundaryMode(player: Player): Boolean {
        if (boundaryMode.containsKey(player.name))
            return false

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
        wandMeta.persistentDataContainer.set(boundaryTag, PersistentDataType.STRING, "Yep");
        wand.itemMeta = wandMeta

        boundaryMode[player.name] = (0..8)
            .map { i -> player.inventory.getItem(i) }
            .plus(player.inventory.itemInOffHand)

        (0..8).forEach { i -> player.inventory.setItem(i, null) }
        player.inventory.setItemInOffHand(null)
        player.inventory.setItemInMainHand(wand)

        return true
    }

    private fun quitBoundaryMode(player: Player) {
        val items = boundaryMode.remove(player.name)
        items!!.slice(0..8).zip((0..8)).forEach {
            (item, slot) -> player.inventory.setItem(slot, item)
        }
        player.inventory.setItemInOffHand(items.last())
    }

    @EventHandler
    fun onItemClick(event: PlayerInteractEvent) {
        val player = event.player
        if (boundaryMode.containsKey(player.name)) {
            event.isCancelled = isWand(event.item)
            quitBoundaryMode(player)
        }
    }

    @EventHandler
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        if (boundaryMode.containsKey(player.name))
            quitBoundaryMode(player)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = Bukkit.getPlayer(event.whoClicked.name)!!
        if (boundaryMode.containsKey(player.name))
            quitBoundaryMode(player)
    }

    @EventHandler
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (boundaryMode.containsKey(player.name)) {
            event.isCancelled = true
            quitBoundaryMode(player)
        }
    }

    @EventHandler
    fun onDrop(event: PlayerDropItemEvent) {
        val player = event.player
        if (boundaryMode.containsKey(player.name)) {
            quitBoundaryMode(player)
            event.itemDrop.remove()
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.player
        if (boundaryMode.containsKey(player.name))
            quitBoundaryMode(player)
    }
}
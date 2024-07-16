package me.bynect.boundaries

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack


object BoundaryManager : Listener {

    private val boundaryMode = HashMap<String, Pair<ItemStack, ItemStack>>()

    fun enterBoundaryMode(player: Player): Boolean {
        if (boundaryMode.containsKey(player.name))
            return false

        player.inventory.heldItemSlot = 1
        boundaryMode[player.name] = Pair(player.inventory.itemInMainHand, player.inventory.itemInOffHand)

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

        wand.itemMeta = wandMeta

        player.inventory.setItemInMainHand(wand)
        player.inventory.setItemInOffHand(null)

        return true
    }

    private fun quitBoundaryMode(player: Player) {
        val hands = boundaryMode.remove(player.name)
        player.inventory.setItemInMainHand(hands!!.first)
        player.inventory.setItemInOffHand(hands.second)
    }

    @EventHandler
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        if (boundaryMode.containsKey(player.name))
            quitBoundaryMode(player)
    }

    @EventHandler
    fun onItemClick(event: PlayerInteractEvent) {
        val player = event.player
        if (boundaryMode.containsKey(player.name))
            quitBoundaryMode(player)
    }

    @EventHandler
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (boundaryMode.containsKey(player.name))
            quitBoundaryMode(player)
    }

    @EventHandler
    fun onDrop(event: PlayerDropItemEvent) {
        val player = event.player
        if (boundaryMode.containsKey(player.name))
            quitBoundaryMode(player)
    }
}
package me.bynect.boundaries

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.persistence.PersistentDataType

object ChunkManager : Listener {

    private val plugin = Bukkit.getPluginManager().getPlugin("boundaries") as Boundaries

    // This tag is used to store the owner of a chunk
    val ownerTag = NamespacedKey(plugin, "owner")
    val ownerType = PersistentDataType.STRING

    fun changeOwner(location: Location, owner: String): Boolean {
        val pdc = location.chunk.persistentDataContainer
        if (pdc.has(ownerTag, ownerType))
            return false

        pdc.set(ownerTag, ownerType, owner)
        return true
    }

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val diffX = event.to.x != event.from.x
        val diffZ = event.to.z != event.from.z

        if (diffZ || diffX) {
            val chunk = event.to.chunk
            val owner = chunk.persistentDataContainer.get(ownerTag, ownerType)
            if (owner != null) {
                event.player.sendActionBar(
                    Component
                        .text("Chunk owned by ")
                        .color(NamedTextColor.WHITE)
                        .decorate(TextDecoration.ITALIC)
                        .append(
                            Component
                                .text(owner)
                                .color(NamedTextColor.LIGHT_PURPLE)
                                .decorate(TextDecoration.BOLD)
                        )
                )
            }

            if ((owner == null || event.player.name == owner) && event.player.gameMode == GameMode.ADVENTURE)
                event.player.gameMode = GameMode.SURVIVAL
            else if (event.player.gameMode == GameMode.SURVIVAL)
                event.player.gameMode = GameMode.ADVENTURE
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        // TODO Block explosions, pvp, etc
    }
}
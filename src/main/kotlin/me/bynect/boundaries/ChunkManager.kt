package me.bynect.boundaries

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.persistence.PersistentDataType
import java.nio.ByteBuffer

object ChunkManager : Listener {

    private val plugin = Bukkit.getPluginManager().getPlugin("boundaries") as Boundaries

    // This tag is used to store the owner of a chunk
    val ownerTag = NamespacedKey(plugin, "boundaryOwner")
    val ownerType = PersistentDataType.STRING

    // This tag is used to store the player who selected this chunk
    val selectTag = NamespacedKey(plugin, "selectPlayer")
    val selectType = PersistentDataType.STRING

    fun serializeLocation(location: Location): ByteArray {
        return ByteBuffer.allocate(Double.SIZE_BYTES * 3 + location.world.name.length)
            .putDouble(location.x)
            .putDouble(location.y)
            .putDouble(location.z)
            .put(location.world.name.toByteArray())
            .array()
    }

    fun deserializeLocation(array: ByteArray): Location {
        val world = Bukkit.getWorld(String(array, Double.SIZE_BYTES * 3, array.size - Double.SIZE_BYTES * 3))
        val buffer = ByteBuffer.wrap(array)
        return Location(world, buffer.getDouble(), buffer.getDouble(), buffer.getDouble())
    }

    fun changeOwner(location: Location, owner: String): Boolean {
        val pdc = location.chunk.persistentDataContainer
        if (pdc.has(ownerTag, ownerType))
            return false

        pdc.set(ownerTag, ownerType, owner)
        return true
    }

    fun isSelectedBy(player: Player, location: Location): Boolean {
        return getSelector(location) == player.name
    }

    fun isOwned(location: Location): Boolean {
        return getOwner(location) != null
    }

    fun getOwner(location: Location): String? {
        return location.chunk.persistentDataContainer.get(ownerTag, ownerType)
    }

    fun getSelector(location: Location): String? {
        return location.chunk.persistentDataContainer.get(selectTag, selectType)
    }

    fun isSelected(location: Location): Boolean {
        return getSelector(location) != null
    }

    fun selectChunk(player: Player, location: Location) {
        val pdc = location.chunk.persistentDataContainer
        assert(!pdc.has(selectTag))
        pdc.set(selectTag, selectType, player.name)
    }

    fun deselectChunk(player: Player, location: Location) {
        val pdc = location.chunk.persistentDataContainer
        assert(pdc.has(selectTag))
        pdc.remove(selectTag)
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
            } else {
                event.player.sendActionBar(Component.text(""))
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
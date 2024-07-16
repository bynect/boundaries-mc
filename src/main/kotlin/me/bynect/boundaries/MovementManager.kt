package me.bynect.boundaries

import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent

class MovementManager : Listener {
    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val diffX = event.to.x != event.from.x
        val diffZ = event.to.z != event.from.z

        if (event.player.gameMode == GameMode.SURVIVAL && (diffX || diffZ)) {
            //Bukkit.getLogger().info("Moved")
        }
    }
}
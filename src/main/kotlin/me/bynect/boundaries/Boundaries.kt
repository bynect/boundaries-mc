package me.bynect.boundaries

import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import me.bynect.boundaries.BoundaryManager.boundaryTag
import me.bynect.boundaries.BoundaryManager.boundaryType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.Bukkit

class Boundaries : JavaPlugin() {

    lateinit var protocolManager : ProtocolManager

    override fun onEnable() {
        protocolManager = ProtocolLibrary.getProtocolManager()

        // Register commands
        this.getCommand("boundary")?.setExecutor(BoundaryExecutor())

        // Register listeners
        val manager = Bukkit.getPluginManager()
        manager.registerEvents(MovementManager(), this)
        manager.registerEvents(BoundaryManager, this)

        Bukkit.getLogger().info("Boundaries enabled")
    }

    override fun onDisable() {
        Bukkit.getLogger().info("Boundaries disabled")
    }
}
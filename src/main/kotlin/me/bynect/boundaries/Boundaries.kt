package me.bynect.boundaries

import me.bynect.boundaries.BoundaryManager.boundaryTag
import me.bynect.boundaries.BoundaryManager.boundaryType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.Bukkit

class Boundaries : JavaPlugin() {

    override fun onEnable() {
        // Register commands
        this.getCommand("boundary")?.setExecutor(BoundaryExecutor())

        // Register listeners
        val manager = Bukkit.getPluginManager()
        manager.registerEvents(MovementManager(), this)
        manager.registerEvents(BoundaryManager, this)

        Bukkit.getLogger().info("Boundaries started")

        Bukkit.getLogger().info(Bukkit.getServer().worlds[0].persistentDataContainer.get(boundaryTag, boundaryType).toString())
    }

    override fun onDisable() {
        Bukkit.getLogger().info("Boundaries finished")
    }
}
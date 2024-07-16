package me.bynect.boundaries

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.Bukkit

class Boundaries : JavaPlugin() {

    val boundaries = null

    override fun onEnable() {
        // Register commands
        this.getCommand("boundary")?.setExecutor(BoundaryExecutor())

        // Register listeners
        val manager = Bukkit.getPluginManager()
        manager.registerEvents(MovementManager(), this)
        manager.registerEvents(BoundaryManager, this)

        Bukkit.getLogger().info("Boundaries started")
    }

    override fun onDisable() {
        Bukkit.getLogger().info("Boundaries finished")
    }
}

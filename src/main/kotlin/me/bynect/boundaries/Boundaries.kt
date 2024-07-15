package me.bynect.boundaries

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.Bukkit

class Boundaries : JavaPlugin() {

    val boundaries  = null

    override fun onEnable() {
        val manager = Bukkit.getPluginManager()
        manager.registerEvents(PlayerMovement(), this)

        this.getCommand("boundary")?.setExecutor(BoundaryExecutor())

        Bukkit.getLogger().info("Boundaries started")
    }

    override fun onDisable() {
        Bukkit.getLogger().info("Boundaries finished")
    }
}

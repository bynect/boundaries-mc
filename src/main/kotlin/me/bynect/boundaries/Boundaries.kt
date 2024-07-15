package me.bynect.boundaries

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin

class Boundaries : JavaPlugin() {

    override fun onEnable() {
        val manager = Bukkit.getPluginManager()
        manager.registerEvents(PlayerMovement(), this)

        Bukkit.getLogger().info("Boundaries started")
    }

    override fun onDisable() {
        Bukkit.getLogger().info("Boundaries finished")
    }
}

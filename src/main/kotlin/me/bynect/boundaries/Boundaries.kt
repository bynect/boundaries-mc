package me.bynect.boundaries

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.Bukkit

class Boundaries : JavaPlugin() {

    override fun onEnable() {
        // Plugin startup logic

        Bukkit.getLogger().info("Boundaries starting")
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}

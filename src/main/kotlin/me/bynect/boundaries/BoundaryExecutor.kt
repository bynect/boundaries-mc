package me.bynect.boundaries

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class BoundaryExecutor : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean  {
        if (sender is Player) {
            val player : Player = sender

            if (args.isNotEmpty())
                return false

            if (BoundaryManager.enterBoundaryMode(player)) {
                player.showTitle(
                    Title.title(
                        Component
                            .text("Boundary mode"),
                        Component
                            .text("Edit boundaries | Drop to exit")
                    )
                )
            } else {
                player.sendMessage(
                    Component
                        .text("Already in boundary mode")
                        .color(NamedTextColor.RED)
                )
            }
        }

        return true
    }
}
package me.bynect.boundaries

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class BoundaryExecutor : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean  {
        if (sender is Player) {
            val component = Component
                .text("[Boundary mode]")
                .color(TextColor.color(NamedTextColor.WHITE))
                .decorate(TextDecoration.BOLD)
                .hoverEvent(
                    HoverEvent.hoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.text("Select the chunk to claim")
                            .color(NamedTextColor.RED)
                    )
                )

            val player : Player = sender
            player.sendMessage(component)
        }

        return true
    }
}
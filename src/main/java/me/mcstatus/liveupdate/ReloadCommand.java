package me.mcstatus.liveupdate;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.IOException;
import java.util.logging.Level;

public class ReloadCommand extends Command {

    private final Liveupdate plugin;

    public ReloadCommand(Liveupdate plugin) {
        super("mcstatus", "mcstatus.reload", "mcstatusreload");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if(args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            try {
                plugin.loadConfig(); // Call the loadConfig method
                sender.sendMessage(new TextComponent(ChatColor.GREEN + "MCStatus configuration reloaded successfully!"));
                plugin.getLogger().log(Level.INFO, "MCStatus configuration reloaded successfully.");
            } catch (IOException e) {
                sender.sendMessage(new TextComponent(ChatColor.RED + "Failed to reload the configuration! Check console for errors."));
                plugin.getLogger().log(Level.SEVERE, "Error reloading the MCStatus configuration", e);
            }
        } else {
            sender.sendMessage(new TextComponent(ChatColor.RED + "Usage: /mcstatus reload"));
        }
    }
}
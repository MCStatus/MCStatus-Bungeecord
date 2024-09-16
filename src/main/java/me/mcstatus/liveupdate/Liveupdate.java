package me.mcstatus.liveupdate;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ConfigurationAdapter;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.config.Configuration;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class Liveupdate extends Plugin implements Listener {

    private String webhookUrl;
    private String footerText;
    private String messageID = null;
    private String iconURL = "https://cdn.mcstatus.me/default.png";
    private String customVersion = "null";
    private String motd = "";
    private boolean perServerPlayers = false;

    private static final ArrayList<String> WH_COMMENTS;

    static {
        WH_COMMENTS = new ArrayList<>();
        WH_COMMENTS.add("DO NOT MODIFY THIS unless you 100% know what you are doing!");
        WH_COMMENTS.add("Staff from the MCStatus Discord might tell you to change this to");
        WH_COMMENTS.add("debug potential issues, otherwise you should not change this.");
        WH_COMMENTS.add("This will be set automatically by the plugin.");
    }

    private boolean fasterUpdates = false;
    private boolean displayPlayerList = true;

    private ScheduledTask task = null;

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerListener(this, this);

        getProxy().getPluginManager().registerCommand(this, new ReloadCommand(this));

        // Load the configuration
        try {
            makeConfig();
            loadConfig();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.getLogger().log(Level.INFO, "Starting the MCStatus.me Live Update Plugin");

        if(webhookUrl.equals("SET_YOUR_WEBHOOK_URL_HERE")) {
            this.getLogger().log(Level.WARNING, "Set your Discord webhook URL in config.yml.");
            return;
        }
    }

    @Override
    public void onDisable() {
        this.getLogger().log(Level.INFO, "Closing the MCStatus.me Live Update Plugin");

        if(this.task != null) {
            this.task.cancel();
            this.task = null;
        }

        String version = ProxyServer.getInstance().getVersion();

        if(!customVersion.equals("null")) {
            version = customVersion;
        }

        try {
            if(messageID != null) {
                int maxPlayers = getProxy().getConfigurationAdapter().getInt("max-players", -1);
                DiscordWebhook.sendServerStatusToDiscord(messageID, iconURL, motd, footerText, false, 0, maxPlayers, version, webhookUrl);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    void loadConfig() throws IOException {
        Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
        webhookUrl = config.getString("webhook-url", "SET_YOUR_WEBHOOK_URL_HERE");
        footerText = config.getString("footer-text", "Set your footer in config.yml");
        fasterUpdates = config.getBoolean("use-faster-updates", false);
        iconURL = config.getString("server-icon-url", "https://cdn.mcstatus.me/default.png");
        displayPlayerList = config.getBoolean("display-player-list", true);
        customVersion = config.getString("set-custom-version", "null");
        messageID = config.getString("mcstatus-wh-message-id", null);
        motd = config.getString("motd", "Configure this in the Live Update config.yml");
        perServerPlayers = config.getBoolean("show-players-per-server", false);

        if(messageID != null && messageID.equals("null")) {
            messageID = null;
        }
        
        if(this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        
        if(this.messageID == null) {
            this.messageID = DiscordWebhook.initWebhook(webhookUrl);
            config.set("mcstatus-wh-message-id", messageID);
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, new File(getDataFolder(), "config.yml"));
            getLogger().info("Successfully saved the webhook message ID");
        }

        this.task = getProxy().getScheduler().schedule(this, () -> {
            StringBuilder playerList = new StringBuilder();

            if(displayPlayerList) {
                
                //disp players by their servers instead of grouping
                if(perServerPlayers) {
                    Map<String, List<String>> serverPlayersMap = new HashMap<>();

                    for(ProxiedPlayer player : getProxy().getPlayers()) {
                        String serverName = capitalizeFirstLetter(player.getServer().getInfo().getName());
                        String username = player.getName();

                        serverPlayersMap.computeIfAbsent(serverName, k -> new ArrayList<>()).add(username);
                    }

                    if(!serverPlayersMap.isEmpty()) {
                        playerList.append("## **Players**:\n\n");

                        for(Map.Entry<String, List<String>> entry : serverPlayersMap.entrySet()) {
                            String serverName = entry.getKey();
                            List<String> players = entry.getValue();

                            playerList.append("**").append(serverName).append("**:\n");

                            for(String username : players) {
                                if((playerList.length() + username.length() + 2) > 3800) {
                                    break;
                                }
                                playerList.append(username).append(", ");
                            }

                            if(playerList.toString().endsWith(", ")) {
                                playerList.setLength(playerList.length() - 2); // Remove the last comma
                            }

                            playerList.append("\n\n");
                        }
                    }
                } else {
                    if(!getProxy().getPlayers().isEmpty()) {
                        playerList.append("**Players**:\n");

                        for(ProxiedPlayer player : getProxy().getPlayers()) {
                            String username = player.getName();

                            if((playerList.length() + username.length() + 2) > 3800) {
                                break;
                            }

                            playerList.append(username).append(", ");
                        }

                        if(playerList.toString().endsWith(", ")) {
                            playerList.setLength(playerList.length() - 2);
                        }

                        playerList.append("\n");
                    }
                }
            }
            
            if(playerList.length() > 3800) {
                playerList.setLength(3800);
            }
            
            int onlinePlayers = getProxy().getOnlineCount();
            int maxPlayers = getProxy().getConfigurationAdapter().getInt("max-players", -1);

            String version = ProxyServer.getInstance().getVersion();
            if(!customVersion.equals("null")) {
                version = customVersion;
            }

            try {
                if(messageID != null) {
                    DiscordWebhook.sendServerStatusToDiscord(messageID, iconURL, motd + "\n\n" + playerList, footerText, true, onlinePlayers, maxPlayers, version, webhookUrl);
                }
            } catch (FileNotFoundException ignored) {
                config.set("mcstatus-wh-message-id", "null");
                try {
                    loadConfig();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, 0L, fasterUpdates ? 10L : 15L, TimeUnit.SECONDS);
    }

    private String capitalizeFirstLetter(String str) {
        if(str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
    
    //copied straight from spigot wiki
    public void makeConfig() throws IOException {
        // Create plugin config folder if it doesn't exist
        if (!getDataFolder().exists()) {
            getLogger().info("Created config folder: " + getDataFolder().mkdir());
        }

        File configFile = new File(getDataFolder(), "config.yml");

        // Copy default config if it doesn't exist
        if (!configFile.exists()) {
            FileOutputStream outputStream = new FileOutputStream(configFile); // Throws IOException
            InputStream in = getResourceAsStream("config.yml"); // This file must exist in the jar resources folder
            in.transferTo(outputStream); // Throws IOException
        }
    }
    
    public void makeConfigAlternative() throws IOException {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        File file = new File(getDataFolder(), "config.yml");


        if (!file.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

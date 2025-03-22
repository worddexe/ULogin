package me.worddexe.uLogin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.mindrot.jbcrypt.BCrypt;

import java.io.*;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ULoginMain extends JavaPlugin {
    private File dataFile;
    private final ConcurrentHashMap<UUID, String> playerPasswords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> autoLogin = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> loggedInPlayers = new ConcurrentHashMap<>();
    private final long MOVE_MESSAGE_COOLDOWN = 5000;
    private final HashMap<UUID, Long> moveCooldown = new HashMap<>();

    @Override
    public void onEnable() {
        this.getCommand("register").setExecutor(new RegisterCommand(this));
        this.getCommand("login").setExecutor(new LoginCommand(this));
        this.getCommand("autologin").setExecutor(new AutoLoginCommand(this));
        this.getCommand("autologindisable").setExecutor(new AutoLoginDisableCommand(this));
        dataFile = new File(getDataFolder(), "ulogin_data.txt");
        loadData();
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerMoveListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CommandListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(this), this);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    public boolean isPremium(Player player) {
        return !player.getName().equalsIgnoreCase(player.getUniqueId().toString());
    }

    public void registerPlayer(UUID uuid, String password) {
        playerPasswords.put(uuid, BCrypt.hashpw(password, BCrypt.gensalt()));
    }

    public boolean verifyPassword(UUID uuid, String password) {
        return playerPasswords.containsKey(uuid) && BCrypt.checkpw(password, playerPasswords.get(uuid));
    }

    public void resetPassword(UUID uuid) {
        playerPasswords.remove(uuid);
        autoLogin.remove(uuid);
    }

    public void setAutoLogin(UUID uuid, boolean state) {
        autoLogin.put(uuid, state);
    }

    public boolean isAutoLogin(UUID uuid) {
        return autoLogin.getOrDefault(uuid, false);
    }

    public void setLoggedIn(UUID uuid, boolean state) {
        loggedInPlayers.put(uuid, state);
    }

    public boolean isLoggedIn(UUID uuid) {
        return loggedInPlayers.getOrDefault(uuid, false);
    }

    public boolean isRegistered(UUID uuid) {
        return playerPasswords.containsKey(uuid);
    }

    private void loadData() {
        if (!dataFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 3) {
                    UUID uuid = UUID.fromString(parts[0]);
                    playerPasswords.put(uuid, parts[1]);
                    autoLogin.put(uuid, Boolean.parseBoolean(parts[2]));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveData() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(dataFile))) {
            for (UUID uuid : playerPasswords.keySet()) {
                writer.write(uuid + ":" + playerPasswords.get(uuid) + ":" + autoLogin.getOrDefault(uuid, false));
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class PlayerJoinListener implements Listener {
        private final ULoginMain plugin;

        public PlayerJoinListener(ULoginMain plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();

            if (!plugin.isRegistered(uuid)) {
                player.sendMessage("§ePlease register with: /register <password>");
            } else {
                if (plugin.isAutoLogin(uuid) && plugin.isPremium(player)) {
                    plugin.setLoggedIn(uuid, true);
                    player.sendMessage("§aWelcome back! You have been automatically logged in.");
                } else if (!plugin.isLoggedIn(uuid)) {
                    player.sendMessage("§cPlease login with: /login <password>");
                } else {
                    player.sendMessage("§aYou are logged in!");
                }
            }
        }
    }

    class PlayerMoveListener implements Listener {
        private final ULoginMain plugin;

        public PlayerMoveListener(ULoginMain plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onPlayerMove(PlayerMoveEvent event) {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();

            if (!plugin.isLoggedIn(uuid)) {
                long currentTime = System.currentTimeMillis();
                long lastMessageTime = moveCooldown.getOrDefault(uuid, 0L);

                event.setCancelled(true);

                if (currentTime - lastMessageTime >= MOVE_MESSAGE_COOLDOWN) {
                    player.sendMessage("§cYou must be logged in to move! Use /login <password> to log in or /register <password> to register.");
                    moveCooldown.put(uuid, currentTime);
                }
            }
        }
    }

    class CommandListener implements Listener {
        private final ULoginMain plugin;

        public CommandListener(ULoginMain plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();

            if (!plugin.isLoggedIn(uuid)) {
                String command = event.getMessage().toLowerCase();

                if (!command.startsWith("/login") && !command.startsWith("/register") && !command.startsWith("/autologin")) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou must log in to use commands.");
                }
            }
        }
    }

    class PlayerQuitListener implements Listener {
        private final ULoginMain plugin;

        public PlayerQuitListener(ULoginMain plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();
            plugin.setLoggedIn(uuid, false);
        }
    }

    class RegisterCommand implements CommandExecutor {
        private final ULoginMain plugin;

        public RegisterCommand(ULoginMain plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }

            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            if (plugin.isLoggedIn(uuid)) {
                player.sendMessage("§cYou are already logged in!");
                return true;
            }

            if (plugin.isRegistered(uuid)) {
                player.sendMessage("§cYou are already registered! Use /login instead.");
                return true;
            }

            if (args.length != 1) {
                player.sendMessage("§eUsage: /register <password>");
                return true;
            }

            plugin.registerPlayer(uuid, args[0]);
            plugin.setLoggedIn(uuid, true);
            player.sendMessage("§aRegistration successful! You are now logged in.");
            return true;
        }
    }

    class LoginCommand implements CommandExecutor {
        private final ULoginMain plugin;

        public LoginCommand(ULoginMain plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }

            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            if (plugin.isLoggedIn(uuid)) {
                player.sendMessage("§cYou are already logged in!");
                return true;
            }

            if (args.length != 1) {
                player.sendMessage("§eUsage: /login <password>");
                return true;
            }

            if (plugin.verifyPassword(uuid, args[0])) {
                plugin.setLoggedIn(uuid, true);
                player.sendMessage("§aLogin successful!");
            } else {
                player.sendMessage("§cIncorrect password!");
            }
            return true;
        }
    }

    class AutoLoginCommand implements CommandExecutor {
        private final ULoginMain plugin;

        public AutoLoginCommand(ULoginMain plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }

            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            if (!plugin.isLoggedIn(uuid)) {
                player.sendMessage("§cYou must be logged in to enable auto-login.");
                return true;
            }

            plugin.setAutoLogin(uuid, true);
            player.sendMessage("§aAuto-login enabled! You will be logged in automatically next time.");
            return true;
        }
    }

    class AutoLoginDisableCommand implements CommandExecutor {
        private final ULoginMain plugin;

        public AutoLoginDisableCommand(ULoginMain plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }

            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            if (!plugin.isLoggedIn(uuid)) {
                player.sendMessage("§cYou must be logged in to disable auto-login.");
                return true;
            }

            if (args.length != 1) {
                player.sendMessage("§eUsage: /autologindisable <username>");
                return true;
            }

            String username = args[0];
            Player targetPlayer = Bukkit.getPlayer(username);

            if (targetPlayer == null) {
                player.sendMessage("§cPlayer not found!");
                return true;
            }

            UUID targetUUID = targetPlayer.getUniqueId();
            plugin.setAutoLogin(targetUUID, false);
            player.sendMessage("§aAuto-login disabled for " + username + ".");
            targetPlayer.sendMessage("§cAuto-login has been disabled for your account by " + player.getName() + ".");

            return true;
        }
    }
}
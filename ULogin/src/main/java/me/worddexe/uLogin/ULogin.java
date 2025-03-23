package me.worddexe.uLogin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class ULogin extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<String, User> users = new HashMap<>();
    private Path userDataPath;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        userDataPath = Paths.get(getDataFolder().getPath(), "userdata.json");
        loadUserData();
        getCommand("register").setExecutor(this);
        getCommand("login").setExecutor(this);
        getCommand("unregister").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("uLogin has been enabled");
    }

    @Override
    public void onDisable() {
        saveUserData();
    }

    private void loadUserData() {
        try {
            if (Files.exists(userDataPath)) {
                try (BufferedReader reader = Files.newBufferedReader(userDataPath)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split(":");
                        if (parts.length == 3) {
                            users.put(parts[0], new User(parts[0], parts[1], parts[2]));
                        }
                    }
                }
            }
        } catch (IOException e) {
            getLogger().warning("Failed to load user data: " + e.getMessage());
        }
    }

    private void saveUserData() {
        try (BufferedWriter writer = Files.newBufferedWriter(userDataPath)) {
            for (User user : users.values()) {
                writer.write(user.getUsername() + ":" + user.getHashedPassword() + ":" + user.getEncryptedIp());
                writer.newLine();
            }
        } catch (IOException e) {
            getLogger().warning("Failed to save user data: " + e.getMessage());
        }
    }

    private void saveUser(User user) {
        try (BufferedWriter writer = Files.newBufferedWriter(userDataPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(user.getUsername() + ":" + user.getHashedPassword() + ":" + user.getEncryptedIp());
            writer.newLine();
        } catch (IOException e) {
            getLogger().warning("Failed to save user data immediately: " + e.getMessage());
        }
    }

    private void registerUser(Player player, String password) {
        if (!users.containsKey(player.getName())) {
            String encryptedIp = encrypt(player.getAddress().toString());
            User newUser = new User(player.getName(), encrypt(password), encryptedIp);
            users.put(player.getName(), newUser);
            newUser.setLoggedIn(true);
            saveUser(newUser);
            player.sendMessage(ChatColor.YELLOW + "You have registered and have been automatically logged in.");
        } else {
            player.sendMessage(ChatColor.RED + "You are already registered.");
        }
    }

    private boolean loginUser(Player player, String password) {
        User user = users.get(player.getName());
        if (user != null && user.getHashedPassword().equals(encrypt(password))) {
            user.setLoggedIn(true);
            player.sendMessage(ChatColor.GREEN + "You have successfully logged in.");
            return true;
        } else {
            player.sendMessage(ChatColor.RED + "Invalid password.");
            return false;
        }
    }

    private String encrypt(String input) {
        return Integer.toString(input.hashCode());
    }

    private void logoutUser(Player player) {
        User user = users.get(player.getName());
        if (user != null) {
            user.setLoggedIn(false);
            player.sendMessage(ChatColor.YELLOW + "You have been logged out.");
        }
    }

    private void unregisterUser(String username, Player admin) {
        User user = users.remove(username);
        if (user != null) {
            Player targetPlayer = Bukkit.getPlayer(username);
            if (targetPlayer != null) {
                targetPlayer.sendMessage(ChatColor.RED + "You have been unregistered by " + admin.getName() + ".");
                targetPlayer.sendMessage(ChatColor.YELLOW + "Please register again using /register <password>");
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("register")) {
            if (sender instanceof Player player) {
                if (args.length != 1) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /register <password>");
                    return true;
                }
                String password = args[0];
                registerUser(player, password);
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (sender instanceof Player player) {
                if (args.length != 1) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /login <password>");
                    return true;
                }
                String password = args[0];
                return loginUser(player, password);
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("unregister")) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("admin.unregister")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to do this.");
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /unregister <username>");
                    return true;
                }
                String username = args[0];
                unregisterUser(username, player);
                player.sendMessage(ChatColor.GREEN + "User " + username + " has been unregistered.");
            }
            return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        User user = users.get(player.getName());
        if (user == null) {
            player.sendMessage(ChatColor.YELLOW + "Please register using /register <password>");
        } else if (!user.isLoggedIn()) {
            player.sendMessage(ChatColor.YELLOW + "Please log in using /login <password>");
        } else {
            String currentIp = player.getAddress().toString();
            if (!user.getEncryptedIp().equals(encrypt(currentIp))) {
                player.kickPlayer(ChatColor.RED + "Your IP address does not match the one you registered with.");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        logoutUser(player);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isUserLoggedIn(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isUserLoggedIn(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!isUserLoggedIn(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (!isUserLoggedIn(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player && !isUserLoggedIn(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !isUserLoggedIn(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && !isUserLoggedIn(player)) {
            event.setCancelled(true);
        }
        if (event.getEntity() instanceof Player target && !isUserLoggedIn(target)) {
            event.setCancelled(true);
        }
    }

    private boolean isUserLoggedIn(Player player) {
        User user = users.get(player.getName());
        return user != null && user.isLoggedIn();
    }

    private static class User {
        private final String username;
        private final String hashedPassword;
        private final String encryptedIp;
        private boolean loggedIn;

        public User(String username, String hashedPassword, String encryptedIp) {
            this.username = username;
            this.hashedPassword = hashedPassword;
            this.encryptedIp = encryptedIp;
            this.loggedIn = false;
        }

        public String getUsername() {
            return username;
        }

        public String getHashedPassword() {
            return hashedPassword;
        }

        public String getEncryptedIp() {
            return encryptedIp;
        }

        public boolean isLoggedIn() {
            return loggedIn;
        }

        public void setLoggedIn(boolean loggedIn) {
            this.loggedIn = loggedIn;
        }
    }
}
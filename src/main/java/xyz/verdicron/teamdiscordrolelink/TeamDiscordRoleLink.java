package xyz.verdicron.teamdiscordrolelink;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class TeamDiscordRoleLink extends JavaPlugin implements Listener, TabCompleter {

    private final Map<String, TeamData> roleToTeam = new HashMap<>();
    private String discordGuildId;

    @Override
    public void onEnable() {
        getLogger().info("TeamDiscordRoleLink enabled!");

        saveDefaultConfig();
        discordGuildId = getConfig().getString("discordGuildId", "YOUR_GUILD_ID_HERE");

        loadRolesFromConfig();

        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("teamdiscordrolelink")).setTabCompleter(this);

        // Sync online players
        Bukkit.getScheduler().runTask(this, this::syncAllOnlinePlayers);
    }

    // ---------------- CONFIG LOADING ----------------

    private void loadRolesFromConfig() {
        roleToTeam.clear();

        if (!getConfig().isConfigurationSection("roles")) return;

        for (String discordRole : getConfig().getConfigurationSection("roles").getKeys(false)) {
            String teamName = getConfig().getString("roles." + discordRole + ".team");
            String colorName = getConfig().getString("roles." + discordRole + ".color");

            if (teamName == null) continue;

            ChatColor color = null;
            if (colorName != null) {
                try {
                    color = ChatColor.valueOf(colorName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid color '" + colorName + "' for role '" + discordRole + "'");
                }
            }

            roleToTeam.put(discordRole, new TeamData(teamName, color));
        }
    }

    // ---------------- PLAYER SYNC ----------------

    private void syncAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            assignTeams(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        assignTeams(event.getPlayer());
    }

    private void assignTeams(Player player) {
    String discordId = DiscordSRV.getPlugin()
            .getAccountLinkManager()
            .getDiscordId(player.getUniqueId());

    if (discordId == null) return;
    if (DiscordSRV.getPlugin().getJda() == null) return;

    var guild = DiscordSRV.getPlugin().getJda().getGuildById(discordGuildId);
    if (guild == null) return;

    Member member = guild.getMemberById(discordId);
    if (member == null) return;

    Scoreboard scoreboard = player.getScoreboard();
    if (scoreboard == null) {
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }

    for (Role role : member.getRoles()) {
        TeamData data = roleToTeam.get(role.getName());
        if (data == null) continue;

        Team team = scoreboard.getTeam(data.teamName);

        // Create team if missing
        if (team == null) {
            team = scoreboard.registerNewTeam(data.teamName);
            team.setDisplayName(data.teamName);
        }

        // ✅ ALWAYS enforce color from config (even if team already existed)
        if (data.color != null) {
            team.setColor(data.color);
        }

        // Remove from all other teams
        for (Team t : scoreboard.getTeams()) {
            if (t.hasEntry(player.getName()) && !t.getName().equals(team.getName())) {
                t.removeEntry(player.getName());
            }
        }

        team.addEntry(player.getName());
    }
}

    // ---------------- COMMAND ----------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("teamdiscordrolelink")) return false;

        if (!sender.isOp()) {
            sender.sendMessage("§cYou must be OP to use this command.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            discordGuildId = getConfig().getString("discordGuildId", "YOUR_GUILD_ID_HERE");
            loadRolesFromConfig();
            syncAllOnlinePlayers();
            sender.sendMessage("§aTeamDiscordRoleLink config reloaded!");
            return true;
        }

        sender.sendMessage("§cUsage: /teamdiscordrolelink reload");
        return true;
    }

    // ---------------- TAB COMPLETE ----------------

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("teamdiscordrolelink")) return null;
        if (!sender.isOp()) return Collections.emptyList();

        if (args.length == 1) {
            return Collections.singletonList("reload");
        }

        return Collections.emptyList();
    }

    @Override
    public void onDisable() {
        getLogger().info("TeamDiscordRoleLink disabled!");
    }

    // ---------------- DATA CLASS ----------------

    private static class TeamData {
        String teamName;
        ChatColor color;

        TeamData(String teamName, ChatColor color) {
            this.teamName = teamName;
            this.color = color;
        }
    }
}

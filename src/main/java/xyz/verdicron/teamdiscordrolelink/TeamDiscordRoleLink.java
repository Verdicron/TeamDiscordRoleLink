package xyz.verdicron.teamdiscordrolelink;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;

public class TeamDiscordRoleLink extends JavaPlugin implements Listener {

    private final Map<String, String> roleToTeam = new HashMap<>();
    private String discordGuildId;

    @Override
    public void onEnable() {
        getLogger().info("TeamDiscordRoleLink enabled!");

        saveDefaultConfig();

        // Load guild ID from config
        discordGuildId = getConfig().getString("discordGuildId", "YOUR_GUILD_ID_HERE");

        loadRolesFromConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        // Sync all online players
        Bukkit.getScheduler().runTask(this, this::syncAllOnlinePlayers);
    }

    private void loadRolesFromConfig() {
        roleToTeam.clear();
        if (getConfig().contains("roles")) {
            for (String discordRole : getConfig().getConfigurationSection("roles").getKeys(false)) {
                String teamName = getConfig().getString("roles." + discordRole);
                if (teamName != null) roleToTeam.put(discordRole, teamName);
            }
        }
    }

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
        // Get Discord ID (string)
        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId());
        if (discordId == null) return;

        if (DiscordSRV.getPlugin().getJda() == null) return;

        Member member = DiscordSRV.getPlugin().getJda()
                .getGuildById(discordGuildId)
                .getMemberById(discordId);

        if (member == null) return;

        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == null) scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        for (Role role : member.getRoles()) {
            String teamName = roleToTeam.get(role.getName());
            if (teamName == null) continue;

            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
                team.setDisplayName(teamName);
            }

            // Remove from other teams
            for (Team t : scoreboard.getTeams()) {
                if (t.hasEntry(player.getName()) && !t.getName().equals(teamName)) {
                    t.removeEntry(player.getName());
                }
            }

            team.addEntry(player.getName());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("teamdiscordrolelink") && args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            discordGuildId = getConfig().getString("discordGuildId", "YOUR_GUILD_ID_HERE");
            loadRolesFromConfig();
            sender.sendMessage("§aTeamDiscordRoleLink config reloaded!");
            syncAllOnlinePlayers();
            return true;
        }
        return false;
    }

    @Override
    public void onDisable() {
        getLogger().info("TeamDiscordRoleLink disabled!");
    }
}

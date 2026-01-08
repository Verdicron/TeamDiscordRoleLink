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
                if (teamName != null) {
                    roleToTeam.put(discordRole, teamName);
                }
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
        String discordId = DiscordSRV.getPlugin()
                .getAccountLinkManager()
                .getDiscordId(player.getUniqueId());

        if (discordId == null) return;
        if (DiscordSRV.getPlugin().getJda() == null) return;
        if (DiscordSRV.getPlugin().getJda().getGuildById(discordGuildId) == null) return;

        Member member = DiscordSRV.getPlugin().getJda()
                .getGuildById(discordGuildId)
                .getMemberById(discordId);

        if (member == null) return;

        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == null) {
            scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        }

        for (Role role : member.getRoles()) {
            String teamName = roleToTeam.get(role.getName());
            if (teamName == null) continue;

            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
                team.setDisplayName(teamName);
            }

            // Remove player from other teams
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
        if (!command.getName().equalsIgnoreCase("teamdiscordrolelink")) {
            return false;
        }

        // OP-only
        if (!sender.isOp()) {
            sender.sendMessage("§cYou must be OP to use this command.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            discordGuildId = getConfig().getString("discordGuildId", "YOUR_GUILD_ID_HERE");
            loadRolesFromConfig();
            sender.sendMessage("§aTeamDiscordRoleLink config reloaded!");
            syncAllOnlinePlayers();
            return true;
        }

        sender.sendMessage("§cUsage: /teamdiscordrolelink reload");
        return true;
    }
@Override
public java.util.List<String> onTabComplete(
        CommandSender sender,
        Command command,
        String alias,
        String[] args
) {
    if (!command.getName().equalsIgnoreCase("teamdiscordrolelink")) {
        return null;
    }

    // OP-only suggestions
    if (!sender.isOp()) {
        return java.util.Collections.emptyList();
    }

    // Suggest "reload" as the first argument
    if (args.length == 1) {
        return java.util.Collections.singletonList("reload");
    }

    return java.util.Collections.emptyList();
}

    @Override
    public void onDisable() {
        getLogger().info("TeamDiscordRoleLink disabled!");
    }
}

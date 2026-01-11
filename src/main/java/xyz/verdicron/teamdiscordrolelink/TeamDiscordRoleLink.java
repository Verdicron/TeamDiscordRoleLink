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
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

import java.util.*;

public class TeamDiscordRoleLink extends JavaPlugin implements Listener, TabCompleter {

    private final List<RoleRule> roleRules = new ArrayList<>();
    private String discordGuildId;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        discordGuildId = getConfig().getString("discordGuildId", "").trim();

        loadRolesFromConfig();

        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("teamdiscordrolelink")).setTabCompleter(this);

        Bukkit.getScheduler().runTask(this, this::syncAllOnlinePlayers);
    }

    // ---------------- CONFIG ----------------

    private void loadRolesFromConfig() {
        roleRules.clear();

        if (!getConfig().isConfigurationSection("roles")) return;

        for (String key : getConfig().getConfigurationSection("roles").getKeys(false)) {
            String base = "roles." + key;

            String typeStr = getConfig().getString(base + ".type");
            String teamName = getConfig().getString(base + ".team");

            if (typeStr == null || teamName == null) {
                getLogger().warning("Role '" + key + "' missing required fields.");
                continue;
            }

            RoleMatchType type;
            try {
                type = RoleMatchType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid type for role '" + key + "'");
                continue;
            }

            ChatColor color = parseColor(base + ".color");

            RoleRule rule = new RoleRule(
                    key,
                    type,
                    teamName,
                    color,
                    parseVisibility(base + ".collisionRule"),
                    parseVisibility(base + ".nametagVisibility"),
                    parseVisibility(base + ".deathMessageVisibility"),
                    getConfig().contains(base + ".friendlyFire") ? getConfig().getBoolean(base + ".friendlyFire") : null,
                    getConfig().contains(base + ".seeFriendlyInvisibles") ? getConfig().getBoolean(base + ".seeFriendlyInvisibles") : null,
                    colorize(getConfig().getString(base + ".displayName")),
                    colorize(getConfig().getString(base + ".prefix")),
                    colorize(getConfig().getString(base + ".suffix"))
            );

            roleRules.add(rule);
        }
    }

    // ---------------- PLAYER SYNC ----------------

    private void syncAllOnlinePlayers() {
        Bukkit.getOnlinePlayers().forEach(this::assignTeams);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        assignTeams(event.getPlayer());
    }

    private void assignTeams(Player player) {
        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId());
        if (discordId == null) return;

        var jda = DiscordSRV.getPlugin().getJda();
        if (jda == null || discordGuildId.isEmpty()) return;

        var guild = jda.getGuildById(discordGuildId);
        if (guild == null) return;

        Member member = guild.getMemberById(discordId);
        if (member == null) return;

        Scoreboard scoreboard = Optional.ofNullable(player.getScoreboard())
                .orElse(Bukkit.getScoreboardManager().getMainScoreboard());

        for (Role role : member.getRoles()) {
            for (RoleRule rule : roleRules) {
                if (!rule.matches(role)) continue;

                Team team = scoreboard.getTeam(rule.teamName);
                if (team == null) {
                    team = scoreboard.registerNewTeam(rule.teamName);
                }

                applyTeamSettings(team, rule);

                for (Team t : scoreboard.getTeams()) {
                    if (t.hasEntry(player.getName()) && !t.equals(team)) {
                        t.removeEntry(player.getName());
                    }
                }

                team.addEntry(player.getName());
            }
        }
    }

    private void applyTeamSettings(Team team, RoleRule rule) {
        if (rule.color != null) team.setColor(rule.color);
        if (rule.displayName != null) team.setDisplayName(rule.displayName);
        if (rule.prefix != null) team.setPrefix(rule.prefix);
        if (rule.suffix != null) team.setSuffix(rule.suffix);

        if (rule.friendlyFire != null) team.setAllowFriendlyFire(rule.friendlyFire);
        if (rule.seeFriendlyInvisibles != null) team.setCanSeeFriendlyInvisibles(rule.seeFriendlyInvisibles);

        if (rule.collisionRule != null)
            team.setOption(Option.COLLISION_RULE, rule.collisionRule);

        if (rule.nametagVisibility != null)
            team.setOption(Option.NAME_TAG_VISIBILITY, rule.nametagVisibility);

        if (rule.deathMessageVisibility != null)
            team.setOption(Option.DEATH_MESSAGE_VISIBILITY, rule.deathMessageVisibility);
    }

    // ---------------- COMMAND ----------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) return true;

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            discordGuildId = getConfig().getString("discordGuildId", "").trim();
            loadRolesFromConfig();
            syncAllOnlinePlayers();
            sender.sendMessage("§aConfig reloaded.");
            return true;
        }

        sender.sendMessage("§cUsage: /teamdiscordrolelink reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return sender.isOp() && args.length == 1 ? List.of("reload") : Collections.emptyList();
    }

    // ---------------- HELPERS ----------------

    private ChatColor parseColor(String path) {
        if (!getConfig().contains(path)) return null;
        try {
            return ChatColor.valueOf(getConfig().getString(path).toUpperCase());
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid color at " + path);
            return null;
        }
    }

    private OptionStatus parseVisibility(String path) {
        if (!getConfig().contains(path)) return null;

        return switch (getConfig().getString(path).toLowerCase()) {
            case "always" -> OptionStatus.ALWAYS;
            case "never" -> OptionStatus.NEVER;
            case "pushotherteams", "hideforotherteams" -> OptionStatus.FOR_OTHER_TEAMS;
            case "pushownteam", "hideforownteam" -> OptionStatus.FOR_OWN_TEAM;
            default -> null;
        };
    }

    private String colorize(String s) {
        return s == null ? null : ChatColor.translateAlternateColorCodes('&', s);
    }

    // ---------------- DATA ----------------

    private enum RoleMatchType { NAME, ID }

    private static class RoleRule {
        final String value;
        final RoleMatchType type;
        final String teamName;
        final ChatColor color;
        final OptionStatus collisionRule, nametagVisibility, deathMessageVisibility;
        final Boolean friendlyFire, seeFriendlyInvisibles;
        final String displayName, prefix, suffix;

        RoleRule(String value, RoleMatchType type, String teamName, ChatColor color,
                 OptionStatus collisionRule, OptionStatus nametagVisibility, OptionStatus deathMessageVisibility,
                 Boolean friendlyFire, Boolean seeFriendlyInvisibles,
                 String displayName, String prefix, String suffix) {

            this.value = value;
            this.type = type;
            this.teamName = teamName;
            this.color = color;
            this.collisionRule = collisionRule;
            this.nametagVisibility = nametagVisibility;
            this.deathMessageVisibility = deathMessageVisibility;
            this.friendlyFire = friendlyFire;
            this.seeFriendlyInvisibles = seeFriendlyInvisibles;
            this.displayName = displayName;
            this.prefix = prefix;
            this.suffix = suffix;
        }

        boolean matches(Role role) {
            return type == RoleMatchType.ID
                    ? role.getId().equals(value)
                    : role.getName().equalsIgnoreCase(value);
        }
    }
}

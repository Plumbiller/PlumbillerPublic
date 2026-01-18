package com.Plumbiller.publicaddon.modules;

import com.Plumbiller.publicaddon.Main;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TabFix extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> rankHierarchy = sgGeneral.add(new StringListSetting.Builder()
            .name("rank-hierarchy")
            .description("Order of ranks from highest to lowest.")
            .defaultValue(new ArrayList<>(Arrays.asList(
                    "Friends", "OWNER", "Legend", "APEX", "Elite Ultra", "Elite", "Prime Ultra", "Prime", "YouTuber",
                    "TikTok", "Bot")))
            .onChanged(this::updatePriorities)
            .build());

    private final Setting<Boolean> displayBotRole = sgGeneral.add(new BoolSetting.Builder()
            .name("custom-bot-rank")
            .description("Whether to assign a specific rank to defined bots.")
            .defaultValue(true)
            .build());

    private final Setting<List<String>> botUsernames = sgGeneral.add(new StringListSetting.Builder()
            .name("bot-usernames")
            .description("List of usernames to be treated as Bots. Use # for single digit, * for any characters.")
            .defaultValue(new ArrayList<>(Arrays.asList("NotSus####", "Saturn*", "kazwqi", "moooomoooo")))
            .visible(displayBotRole::get)
            .onChanged(this::updateBotPatterns)
            .build());

    private final Map<String, Integer> rankPriority = new HashMap<>();
    private final Map<UUID, Text> originalDisplayNames = new HashMap<>();
    private List<Pattern> botPatterns = new ArrayList<>();
    private final Pattern rankPattern = Pattern.compile("^\\[(.*?)\\]");
    private static final String TEAM_PREFIX = "TF_";

    public TabFix() {
        super(Main.CATEGORY, "tab-fix", "Fixes tab list sorting by rank.");
    }

    @Override
    public void onActivate() {
        originalDisplayNames.clear();
        updatePriorities(rankHierarchy.get());
        updateBotPatterns(botUsernames.get());
    }

    private void updatePriorities(List<String> ranks) {
        rankPriority.clear();
        for (int i = 0; i < ranks.size(); i++) {
            rankPriority.put(ranks.get(i), i + 1);
        }
    }

    private void updateBotPatterns(List<String> patterns) {
        List<Pattern> newPatterns = new ArrayList<>();
        for (String p : patterns) {
            StringBuilder sb = new StringBuilder("^");
            for (char c : p.toCharArray()) {
                if (c == '*') {
                    sb.append(".*");
                } else if (c == '#') {
                    sb.append("\\d");
                } else {
                    if (Character.isLetterOrDigit(c)) {
                        sb.append(c);
                    } else {
                        sb.append(Pattern.quote(String.valueOf(c)));
                    }
                }
            }
            sb.append("$");
            try {
                newPatterns.add(Pattern.compile(sb.toString()));
            } catch (Exception e) {
            }
        }
        this.botPatterns = newPatterns;
    }

    @Override
    public void onDeactivate() {
        if (mc.world == null)
            return;
        Scoreboard scoreboard = mc.world.getScoreboard();

        List<Team> teamsToRemove = new ArrayList<>();
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                teamsToRemove.add(team);
            }
        }

        for (Team team : teamsToRemove) {
            scoreboard.removeTeam(team);
        }

        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                if (originalDisplayNames.containsKey(entry.getProfile().getId())) {
                    entry.setDisplayName(originalDisplayNames.get(entry.getProfile().getId()));
                }
            }
        }
        originalDisplayNames.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null)
            return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        Collection<PlayerListEntry> entries = mc.getNetworkHandler().getPlayerList();

        for (PlayerListEntry entry : entries) {
            String name = entry.getProfile().getName();

            if (originalDisplayNames.containsKey(entry.getProfile().getId())) {
                boolean matchesBot = false;
                List<Pattern> currentPatterns = this.botPatterns;
                if (displayBotRole.get()) {
                    for (Pattern pattern : currentPatterns) {
                        if (pattern.matcher(name).matches()) {
                            matchesBot = true;
                            break;
                        }
                    }
                }

                if (!matchesBot) {
                    entry.setDisplayName(originalDisplayNames.remove(entry.getProfile().getId()));
                }
            }

            String displayName = entry.getDisplayName() != null ? entry.getDisplayName().getString() : null;

            if (displayName == null) {
                displayName = name;
            }

            int priority = 999;

            if (displayName != null) {
                String cleanName = displayName.trim();
                Matcher matcher = rankPattern.matcher(cleanName);
                if (matcher.find()) {
                    String rank = matcher.group(1);
                    if (rankPriority.containsKey(rank)) {
                        priority = Math.min(priority, rankPriority.get(rank));
                    }
                }
            }

            boolean isBot = false;
            List<Pattern> currentPatterns = this.botPatterns;
            if (displayBotRole.get()) {
                for (Pattern pattern : currentPatterns) {
                    if (pattern.matcher(name).matches()) {
                        isBot = true;
                        break;
                    }
                }
            }

            if (isBot) {
                if (rankPriority.containsKey("Bot")) {
                    priority = Math.min(priority, rankPriority.get("Bot"));
                }

                if (!originalDisplayNames.containsKey(entry.getProfile().getId())) {
                    originalDisplayNames.put(entry.getProfile().getId(), entry.getDisplayName());
                }

                Text currentDisplay = entry.getDisplayName();
                if (currentDisplay == null || !currentDisplay.getString().startsWith("[Bot]")) {
                    Text newDisplayName = Text.literal("[Bot] ").formatted(Formatting.DARK_GREEN)
                            .append(Text.literal(name).formatted(Formatting.GREEN));
                    entry.setDisplayName(newDisplayName);
                }
            }

            if (Friends.get().get(name) != null) {
                if (rankPriority.containsKey("Friends")) {
                    priority = Math.min(priority, rankPriority.get("Friends"));
                }
            }

            String teamName = String.format("%s%03d", TEAM_PREFIX, priority);

            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.addTeam(teamName);
            }

            if (!team.getPlayerList().contains(name)) {
                scoreboard.addScoreHolderToTeam(name, team);
            }
        }
    }
}

package com.Plumbiller.publicaddon.modules;

import com.Plumbiller.publicaddon.Main;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
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
    private final Map<String, Integer> rankPriority = new HashMap<>();
    private final List<Map.Entry<String, Integer>> sortedRanks = new ArrayList<>();
    private final Pattern rankPattern = Pattern.compile("^\\[(.*?)\\]");
    private static final String TEAM_PREFIX = "TF_";

    public TabFix() {
        super(Main.CATEGORY, "tab-fix", "Fixes tab list sorting by rank.");
    }

    @Override
    public void onActivate() {
        rankPriority.clear();
        rankPriority.put("OWNER", 1);
        rankPriority.put("Legend", 2);
        rankPriority.put("APEX", 3);
        rankPriority.put("Elite Ultra", 4);
        rankPriority.put("Elite", 5);
        rankPriority.put("Prime Ultra", 6);
        rankPriority.put("Prime", 7);
        rankPriority.put("YouTuber", 8);
        rankPriority.put("TikTok", 9);
        rankPriority.put("Bot", 10);

        sortedRanks.clear();
        sortedRanks.addAll(rankPriority.entrySet());
        sortedRanks.sort((e1, e2) -> Integer.compare(e2.getKey().length(), e1.getKey().length()));
    }

    @Override
    public void onDeactivate() {
        if (mc.world == null)
            return;
        Scoreboard scoreboard = mc.world.getScoreboard();

        // Use a list to avoid ConcurrentModificationException while iterating
        List<Team> teamsToRemove = new ArrayList<>();
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                teamsToRemove.add(team);
            }
        }

        for (Team team : teamsToRemove) {
            scoreboard.removeTeam(team);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null)
            return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        Collection<PlayerListEntry> entries = mc.getNetworkHandler().getPlayerList();

        for (PlayerListEntry entry : entries) {
            String name = entry.getProfile().getName();

            // Bot Detection: NotSus + 4 digits
            // We force set their display name to [Bot] Name so they are visually marked
            // and our rank parser picks them up automatically.
            if (name.matches("NotSus\\d{4}")) {
                Text currentDisplay = entry.getDisplayName();
                if (currentDisplay == null || !currentDisplay.getString().startsWith("[Bot]")) {
                    Text newDisplayName = Text.literal("[Bot] ").formatted(Formatting.DARK_GREEN)
                            .append(Text.literal(name).formatted(Formatting.GREEN));
                    entry.setDisplayName(newDisplayName);
                }
            }

            String displayName = entry.getDisplayName() != null ? entry.getDisplayName().getString() : null;

            if (displayName == null) {
                // If no display name, check if we should process it as unranked or skip
                // Usually server sends [Rank] Name as display name.
                // If null, it might just be the name.
                displayName = name;
            }

            int priority = 999; // Default to lowest priority (Unranked)

            if (displayName != null) {
                // Remove leading whitespace just in case
                displayName = displayName.trim();
                Matcher matcher = rankPattern.matcher(displayName);
                if (matcher.find()) {
                    String rank = matcher.group(1);
                    // Check against sorted ranks (longest first)
                    for (Map.Entry<String, Integer> rankEntry : sortedRanks) {
                        if (rank.startsWith(rankEntry.getKey())) {
                            priority = rankEntry.getValue();
                            break;
                        }
                    }
                }
            }

            // Friend Detection: Place first (priority 0)
            if (Friends.get().get(name) != null) {
                priority = 0;
            }

            // Create team name based on priority
            // Format: TF_01, TF_02, ... TF_999
            String teamName = String.format("%s%03d", TEAM_PREFIX, priority);

            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.addTeam(teamName);
            }

            // If player is not in this team, add them
            if (!team.getPlayerList().contains(name)) {
                scoreboard.addScoreHolderToTeam(name, team);
            }
        }
    }
}

package com.Plumbiller.publicaddon.modules;

import com.Plumbiller.publicaddon.util.MultiVersionCompat;

import com.Plumbiller.publicaddon.Main;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BetterTabFix extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private int lastValidTabHeight = 20;

    public final Setting<Integer> tabSize = sgGeneral.add(new IntSetting.Builder()
            .name("tablist-size")
            .description("How many players in total to display in the tablist.")
            .defaultValue(100)
            .min(1)
            .sliderRange(1, 1000)
            .onChanged(this::onTabSizeChanged)
            .build());

    public final Setting<Integer> tabHeight = sgGeneral.add(new IntSetting.Builder()
            .name("column-height")
            .description("How many players to display in each column.")
            .defaultValue(20)
            .min(1)
            .sliderRange(1, 1000)
            .onChanged(this::onTabHeightChanged)
            .build());

    private void onTabSizeChanged(Integer newSize) {
        if (newSize % tabHeight.get() != 0) {
            int currentHeight = tabHeight.get();
            int newHeight = findNearestDivisor(newSize, currentHeight);

            if (newHeight != currentHeight) {
                warning("Column height adjusted to %d to match new tab size.", newHeight);
                tabHeight.set(newHeight);
            }
        }

    }

    private void onTabHeightChanged(Integer newHeight) {
        if (tabSize.get() % newHeight != 0) {
            error("Tab size must be divisible by column height! Reverting to %d.", lastValidTabHeight);
            if (tabHeight.get() != lastValidTabHeight) {
                tabHeight.set(lastValidTabHeight);
            }
        } else {
            lastValidTabHeight = newHeight;
        }
    }

    private int findNearestDivisor(int n, int target) {
        int best = 1;
        int minDiff = Integer.MAX_VALUE;
        for (int i = 1; i <= n; i++) {
            if (n % i == 0) {
                int diff = Math.abs(i - target);
                if (diff < minDiff) {
                    minDiff = diff;
                    best = i;
                }
            }
        }
        return best;
    }

    private final Setting<Boolean> self = sgGeneral.add(new BoolSetting.Builder()
            .name("highlight-self")
            .description("Highlights yourself in the tablist.")
            .defaultValue(true)
            .build());

    private final Setting<SettingColor> selfColor = sgGeneral.add(new ColorSetting.Builder()
            .name("self-color")
            .description("The color to highlight your name with.")
            .defaultValue(new SettingColor(250, 130, 30))
            .visible(self::get)
            .build());

    private final Setting<Boolean> friends = sgGeneral.add(new BoolSetting.Builder()
            .name("highlight-friends")
            .description("Highlights friends in the tablist.")
            .defaultValue(true)
            .build());

    public final Setting<Boolean> scrollableTab = sgGeneral.add(new BoolSetting.Builder()
            .name("scrollable-tab")
            .description("Allows scrolling through the tab list.")
            .defaultValue(true)
            .build());

    public final Setting<ScrollMode> scrollMode = sgGeneral.add(new EnumSetting.Builder<ScrollMode>()
            .name("scroll-mode")
            .description("How the scrolling should function.")
            .defaultValue(ScrollMode.OneByOne)
            .visible(scrollableTab::get)
            .build());

    private final Setting<Boolean> gamemode = sgGeneral.add(new BoolSetting.Builder()
            .name("gamemode")
            .description("Display gamemode next to the nick.")
            .defaultValue(false)
            .build());

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
            .defaultValue(
                    new ArrayList<>(Arrays.asList("NotSus####", "Saturn*", "kazwqi", "moooomoooo", "*Bot*", "*bot*")))
            .visible(displayBotRole::get)
            .onChanged(this::updateBotPatterns)
            .build());

    private final Map<String, Integer> rankPriority = new HashMap<>();
    private final Map<UUID, Text> originalDisplayNames = new HashMap<>();
    private List<Pattern> botPatterns = new ArrayList<>();
    private final Pattern rankPattern = Pattern.compile("^\\[(.*?)\\]");
    private static final String TEAM_PREFIX = "TF_";

    public int scrollOffset = 0;
    public int totalPlayerCount = 0;

    public BetterTabFix() {
        super(Main.CATEGORY, "better-tab-fix", "Fixes tab list sorting and adds various improvements.");
    }

    @Override
    public void onActivate() {
        BetterTab betterTab = Modules.get().get(BetterTab.class);
        if (betterTab != null && betterTab.isActive()) {
            betterTab.toggle();
            error("BetterTab and BetterTabFix are not compatible. BetterTab module was disabled.");
        }

        originalDisplayNames.clear();
        updatePriorities(rankHierarchy.get());
        updateBotPatterns(botUsernames.get());
    }

    @Override
    public void onDeactivate() {
        scrollOffset = 0;
        totalPlayerCount = 0;

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

        if (mc.player != null && mc.player.networkHandler != null) {
            for (PlayerListEntry entry : mc.player.networkHandler.getPlayerList()) {
                if (originalDisplayNames.containsKey(MultiVersionCompat.getProfileId(entry.getProfile()))) {
                    entry.setDisplayName(originalDisplayNames.get(MultiVersionCompat.getProfileId(entry.getProfile())));
                }
            }
        }
        originalDisplayNames.clear();
    }

    @EventHandler
    private void onMouseScroll(MouseScrollEvent event) {
        if (!scrollableTab.get())
            return;

        if (mc.options.playerListKey.isPressed()) {
            int step = 1;
            switch (scrollMode.get()) {
                case ByColumns -> step = tabHeight.get();
                case FullTab -> step = tabSize.get();
                default -> step = 1;
            }

            if (event.value > 0) {
                if (scrollOffset > 0) {
                    scrollOffset -= step;
                    if (scrollOffset < 0)
                        scrollOffset = 0;
                }
            } else if (event.value < 0) {
                int maxOffset = Math.max(0, totalPlayerCount - tabSize.get());
                if (scrollOffset < maxOffset) {
                    scrollOffset += step;
                    if (scrollOffset > maxOffset)
                        scrollOffset = maxOffset;
                }
            }
            event.cancel();
        }
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

    @EventHandler
    private void onTick(TickEvent.Post event) {
        BetterTab betterTab = Modules.get().get(BetterTab.class);
        if (betterTab != null && betterTab.isActive()) {
            betterTab.toggle();
            error("BetterTab and BetterTabFix are not compatible, please disable BetterTabFix if you want to use BetterTab.");
        }

        if (mc.world == null || mc.player == null || mc.player.networkHandler == null)
            return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        Collection<PlayerListEntry> entries = mc.player.networkHandler.getPlayerList();

        for (PlayerListEntry entry : entries) {
            String name = MultiVersionCompat.getProfileName(entry.getProfile());

            if (originalDisplayNames.containsKey(MultiVersionCompat.getProfileId(entry.getProfile()))) {
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
                    entry.setDisplayName(
                            originalDisplayNames.remove(MultiVersionCompat.getProfileId(entry.getProfile())));
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
                    String capturedRank = matcher.group(1).trim();
                    for (Map.Entry<String, Integer> rankEntry : rankPriority.entrySet()) {
                        String key = rankEntry.getKey();
                        if (capturedRank.startsWith(key)) {
                            priority = Math.min(priority, rankEntry.getValue());
                        }
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

                if (!originalDisplayNames.containsKey(MultiVersionCompat.getProfileId(entry.getProfile()))) {
                    originalDisplayNames.put(MultiVersionCompat.getProfileId(entry.getProfile()),
                            entry.getDisplayName());
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

    public Text getPlayerName(PlayerListEntry playerListEntry) {
        Text name;
        Color color = null;

        name = playerListEntry.getDisplayName();
        if (name == null)
            name = Text.literal(MultiVersionCompat.getProfileName(playerListEntry.getProfile()));

        if (MultiVersionCompat.getProfileId(playerListEntry.getProfile()).toString()
                .equals(MultiVersionCompat.getProfileId(mc.player.getGameProfile()).toString())
                && self.get()) {
            color = selfColor.get();
        } else if (friends.get() && Friends.get().isFriend(playerListEntry)) {
            Friend friend = Friends.get().get(playerListEntry);
            if (friend != null)
                color = Config.get().friendColor.get();
        }

        if (color != null) {
            String nameString = name.getString();

            for (Formatting format : Formatting.values()) {
                if (format.isColor())
                    nameString = nameString.replace(format.toString(), "");
            }

            name = Text.literal(nameString).setStyle(name.getStyle().withColor(TextColor.fromRgb(color.getPacked())));
        }

        if (gamemode.get()) {
            GameMode gm = playerListEntry.getGameMode();
            String gmText = "?";
            if (gm != null) {
                gmText = switch (gm) {
                    case SPECTATOR -> "Sp";
                    case SURVIVAL -> "S";
                    case CREATIVE -> "C";
                    case ADVENTURE -> "A";
                };
            }
            MutableText text = Text.literal("");
            text.append(name);
            text.append(" [" + gmText + "]");
            name = text;
        }

        return name;
    }

    public enum ScrollMode {
        OneByOne("One by one"),
        ByColumns("By columns"),
        FullTab("Full tab");

        private final String title;

        ScrollMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}

package com.Plumbiller.publicaddon.ui;

import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.input.WDoubleEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ModInfoScreen extends WidgetScreen {
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[(.*?)\\]\\((.*?)\\)");
    private static final Pattern MODULE_PATTERN = Pattern.compile("\\[\\[(.*?)\\]\\]");

    private final Screen parent;
    private int currentTab = 0;
    private WContainer content;
    private meteordevelopment.meteorclient.gui.widgets.containers.WView view;

    private final Map<SettingGroup, List<Setting<?>>> lastVisibleSettings = new HashMap<>();

    private double savedScroll = -1;

    public ModInfoScreen(Screen parent) {
        super(GuiThemes.get(), "PlumbillerPublic Info");
        this.parent = parent;
    }

    @Override
    public void initWidgets() {
        WWindow window = super.add(theme.window("PlumbillerPublic Info")).center().minWidth(1200).widget();
        setWindowMinHeight(window, 500);

        WHorizontalList tabs = window.add(theme.horizontalList()).expandX().widget();
        addTab(tabs, "Overview", 0);
        addTab(tabs, "Features", 1);
        addTab(tabs, "Dependencies", 2);
        window.add(theme.horizontalSeparator()).expandX();

        view = window.add(theme.view()).expandX().widget();
        view.hasScrollBar = true;
        content = view.add(theme.verticalList()).expandX().widget();

        loadTab(currentTab);
        restoreScrollPosition();
    }

    private void setWindowMinHeight(WWindow window, double height) {
        try {
            Class<?> c = window.getClass();
            while (c != null) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField("minHeight");
                    f.setAccessible(true);
                    f.setDouble(window, height);
                    return;
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addTab(WHorizontalList tabs, String name, int index) {
        WButton button = tabs.add(theme.button(name)).expandX().widget();
        button.action = () -> loadTab(index);
    }

    private void loadTab(int index) {
        currentTab = index;
        if (content != null) {
            content.clear();
        }

        String filename = switch (index) {
            case 1 -> "features.md";
            case 2 -> "dependencies.md";
            default -> "overview.md";
        };

        loadMarkdownFile("/assets/publicaddon/info/" + filename);
    }

    private void loadMarkdownFile(String path) {
        if (content == null)
            return;

        try (InputStream stream = getClass().getResourceAsStream(path)) {
            if (stream == null) {
                content.add(theme.label("Error: Could not find " + path));
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                processMarkdownLine(line);
            }
        } catch (Exception e) {
            content.add(theme.label("Error loading file: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private void processMarkdownLine(String line) {
        line = line.trim();

        if (line.isEmpty()) {
            content.add(theme.label(""));
            return;
        }

        if (IMAGE_PATTERN.matcher(line).matches()) {
            return;
        }

        Matcher moduleMatcher = MODULE_PATTERN.matcher(line);
        if (moduleMatcher.find()) {
            injectModuleSettings(moduleMatcher.group(1));
            return;
        }

        String text = line;
        if (line.startsWith("# ")) {
            text = "&l&n" + line.substring(2);
            addFormattedLabel(text);
            content.add(theme.horizontalSeparator()).expandX();
        } else if (line.startsWith("## ")) {
            text = "&l" + line.substring(3);
            addFormattedLabel(text);
        } else if (line.startsWith("### ")) {
            text = "&l" + line.substring(4);
            addFormattedLabel(text);
        } else if (line.startsWith("* ")) {
            text = "• " + line.substring(2);
            addFormattedLabel(text);
        } else {
            addFormattedLabel(text);
        }
    }

    private void addFormattedLabel(String textString) {
        int maxWidth = 1100;
        List<Text> wrappedLines = wrapTextWithFormatting(textString, maxWidth);

        for (Text line : wrappedLines) {
            content.add(new WFormattedLabel(line.asOrderedText(), maxWidth)).expandX();
        }
    }

    private List<Text> wrapTextWithFormatting(String textString, int maxWidth) {
        if (client == null || client.textRenderer == null) {
            return List.of(parseText(textString));
        }

        List<Text> lines = new java.util.ArrayList<>();
        String[] words = textString.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            Text testText = parseText(testLine);
            int lineWidth = client.textRenderer.getWidth(testText);

            if (lineWidth <= maxWidth) {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(parseText(currentLine.toString()));
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(parseText(word));
                }
            }
        }

        if (currentLine.length() > 0) {
            lines.add(parseText(currentLine.toString()));
        }

        return lines.isEmpty() ? List.of(Text.literal("")) : lines;
    }

    private Text parseText(String text) {
        MutableText root = Text.literal("");
        StringBuilder buffer = new StringBuilder();
        Style style = Style.EMPTY;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                Formatting formatting = Formatting.byCode(code);
                if (formatting != null) {
                    if (buffer.length() > 0) {
                        root.append(Text.literal(buffer.toString()).setStyle(style));
                        buffer.setLength(0);
                    }
                    if (formatting == Formatting.RESET) {
                        style = Style.EMPTY;
                    } else if (formatting.isColor()) {
                        style = style.withColor(formatting);
                    } else if (formatting.isModifier()) {
                        style = style.withFormatting(formatting);
                    }
                    i++;
                    continue;
                }
            }
            buffer.append(c);
        }
        if (buffer.length() > 0) {
            root.append(Text.literal(buffer.toString()).setStyle(style));
        }
        return root;
    }

    private class WFormattedLabel extends WWidget {
        private final OrderedText text;
        private final int maxWidth;

        public WFormattedLabel(OrderedText text, int maxWidth) {
            this.text = text;
            this.maxWidth = maxWidth;
        }

        @Override
        protected void onCalculateSize() {
            double w = 0;
            double h = 12;
            if (mc.textRenderer != null) {
                w = Math.min(mc.textRenderer.getWidth(text), maxWidth);
                h = mc.textRenderer.fontHeight + 3;
            }
            width = w;
            height = h;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            final double[] currentX = { x };
            final StringBuilder buffer = new StringBuilder();
            class RenderState {
                meteordevelopment.meteorclient.utils.render.color.Color color = new meteordevelopment.meteorclient.utils.render.color.Color(
                        255, 255, 255);
                boolean empty = true;
            }
            final RenderState state = new RenderState();

            text.accept((index, style, codePoint) -> {
                meteordevelopment.meteorclient.utils.render.color.Color styleColor = new meteordevelopment.meteorclient.utils.render.color.Color(
                        255, 255, 255);
                if (style != null && style.getColor() != null) {
                    styleColor = new meteordevelopment.meteorclient.utils.render.color.Color(
                            style.getColor().getRgb() | 0xFF000000);
                }

                if (!state.empty && !styleColor.equals(state.color)) {
                    String segment = buffer.toString();
                    renderer.text(segment, currentX[0], y, state.color, false);
                    currentX[0] += renderer.theme.textRenderer().getWidth(segment);
                    buffer.setLength(0);
                    state.empty = true;
                }

                state.color = styleColor;
                buffer.append(Character.toChars(codePoint));
                state.empty = false;
                return true;
            });

            if (!state.empty) {
                String segment = buffer.toString();
                renderer.text(segment, currentX[0], y, state.color, false);
            }
        }
    }

    private void injectModuleSettings(String moduleName) {
        moduleName = moduleName.replace(".java", "").replace(".class", "");

        for (Module module : Modules.get().getAll()) {
            if (module.getClass().getSimpleName().equalsIgnoreCase(moduleName)) {
                buildModuleSettings(module);
                return;
            }
        }

        content.add(theme.label("Module not found: " + moduleName));
    }

    private void buildModuleSettings(Module module) {
        lastVisibleSettings.clear();

        content.add(theme.horizontalSeparator()).expandX();
        content.add(theme.label(module.name + " Configuration")).expandX();

        for (SettingGroup group : module.settings) {
            WSection section = content.add(theme.section(group.name)).expandX().widget();
            section.setExpanded(true);

            List<Setting<?>> visibleSettings = new java.util.ArrayList<>();
            for (Setting<?> setting : group) {
                if (setting.isVisible()) {
                    addSettingWidget(section, setting, group);
                    visibleSettings.add(setting);
                }
            }
            lastVisibleSettings.put(group, visibleSettings);
        }

        content.add(theme.horizontalSeparator()).expandX();
    }

    private void addSettingWidget(WContainer parent, Setting<?> setting, SettingGroup group) {
        WHorizontalList row = parent.add(theme.horizontalList()).expandX().widget();
        row.add(theme.label(formatSettingName(setting.name)));

        if (setting instanceof BoolSetting boolSetting) {
            addBoolSetting(row, boolSetting, group);
        } else if (setting instanceof IntSetting intSetting) {
            addIntSetting(row, intSetting, group);
        } else if (setting instanceof DoubleSetting doubleSetting) {
            addDoubleSetting(row, doubleSetting, group);
        } else if (setting instanceof StringSetting stringSetting) {
            addStringSetting(row, stringSetting, group);
        } else if (setting instanceof EnumSetting<?> enumSetting) {
            addEnumSetting(row, enumSetting, group);
        } else if (setting instanceof ColorSetting colorSetting) {
            addColorSetting(row, colorSetting, group);
        } else if (setting instanceof StringListSetting stringListSetting) {
            addStringListSetting(row, parent, stringListSetting, group);
        } else if (setting instanceof ItemListSetting itemListSetting) {
            addItemListSetting(row, itemListSetting, group);
        } else {
            row.add(theme.label(setting.get().toString())).expandCellX();
            row.add(theme.label("")).expandX();
            addResetButton(row, setting, group);
        }
    }

    private void addBoolSetting(WHorizontalList row, BoolSetting setting, SettingGroup group) {
        WCheckbox checkbox = row.add(theme.checkbox(setting.get())).expandCellX().widget();
        checkbox.action = () -> {
            setting.set(checkbox.checked);
            reloadWithScrollPreservation();
        };
        row.add(theme.label("")).expandX();
        addResetButton(row, setting, group);
    }

    private void addIntSetting(WHorizontalList row, IntSetting setting, SettingGroup group) {
        boolean noSlider = (setting.sliderMin == 0 && setting.sliderMax == 0);

        WIntEdit edit = row.add(theme.intEdit(
                setting.get(),
                setting.min,
                setting.max,
                setting.sliderMin,
                setting.sliderMax,
                noSlider)).minWidth(100).expandCellX().widget();

        edit.action = () -> setting.set(edit.get());
        edit.actionOnRelease = () -> {
            setting.set(edit.get());
            reloadWithScrollPreservation();
        };

        row.add(theme.label("")).expandX();
        addResetButton(row, setting, group);
    }

    private void addDoubleSetting(WHorizontalList row, DoubleSetting setting, SettingGroup group) {
        boolean noSlider = (setting.sliderMin == 0 && setting.sliderMax == 0);

        WDoubleEdit edit = row.add(theme.doubleEdit(
                setting.get(),
                setting.min,
                setting.max,
                setting.sliderMin,
                setting.sliderMax,
                setting.decimalPlaces,
                noSlider)).minWidth(100).expandCellX().widget();

        edit.action = () -> setting.set(edit.get());
        edit.actionOnRelease = () -> {
            setting.set(edit.get());
            reloadWithScrollPreservation();
        };

        row.add(theme.label("")).expandX();
        addResetButton(row, setting, group);
    }

    private void addStringSetting(WHorizontalList row, StringSetting setting, SettingGroup group) {
        WTextBox textBox = row.add(theme.textBox(setting.get())).minWidth(100).expandCellX().widget();
        textBox.action = () -> setting.set(textBox.get());
        textBox.actionOnUnfocused = () -> {
            setting.set(textBox.get());
            reloadWithScrollPreservation();
        };

        row.add(theme.label("")).expandX();
        addResetButton(row, setting, group);
    }

    private void addEnumSetting(WHorizontalList row, EnumSetting<?> setting, SettingGroup group) {
        Object[] enumValues = setting.get().getClass().getEnumConstants();
        if (enumValues != null) {
            WDropdown<Object> dropdown = row.add(theme.dropdown(enumValues, setting.get())).expandCellX().widget();
            dropdown.action = () -> {
                setEnumValue(setting, dropdown.get());
                reloadWithScrollPreservation();
            };
        }

        row.add(theme.label("")).expandX();
        addResetButton(row, setting, group);
    }

    private void addColorSetting(WHorizontalList row, ColorSetting setting, SettingGroup group) {
        row.add(theme.quad(setting.get())).widget();

        WButton editButton = row.add(theme.button("Edit")).widget();
        editButton.action = () -> {
            saveScrollPosition();
            mc.setScreen(new meteordevelopment.meteorclient.gui.screens.settings.ColorSettingScreen(theme, setting));
        };

        row.add(theme.label("")).expandX();
        addResetButton(row, setting, group);
    }

    private void addStringListSetting(WHorizontalList row, WContainer parent, StringListSetting setting,
            SettingGroup group) {
        row.add(theme.label("")).expandX();
        addResetButton(row, setting, group);

        WVerticalList list = parent.add(theme.verticalList()).expandX().widget();

        for (int i = 0; i < setting.get().size(); i++) {
            int index = i;
            WHorizontalList itemRow = list.add(theme.horizontalList()).expandX().widget();

            WTextBox textBox = itemRow.add(theme.textBox(setting.get().get(i))).minWidth(100).expandX().widget();
            textBox.action = () -> {
                if (index < setting.get().size()) {
                    setting.get().set(index, textBox.get());
                }
            };

            WMinus removeButton = itemRow.add(theme.minus()).widget();
            removeButton.action = () -> {
                if (index < setting.get().size()) {
                    setting.get().remove(index);
                    reloadWithScrollPreservation();
                }
            };
        }

        WHorizontalList addRow = list.add(theme.horizontalList()).expandX().widget();
        WPlus addButton = addRow.add(theme.plus()).widget();
        addButton.action = () -> {
            setting.get().add("");
            reloadWithScrollPreservation();
        };
    }

    private void addItemListSetting(WHorizontalList row, ItemListSetting setting, SettingGroup group) {
        WButton selectButton = row.add(theme.button("Select")).expandCellX().widget();
        selectButton.action = () -> {
            saveScrollPosition();
            mc.setScreen(new meteordevelopment.meteorclient.gui.screens.settings.ItemListSettingScreen(theme, setting));
        };

        row.add(theme.label("")).expandX();
        addResetButton(row, setting, group);
    }

    private void addResetButton(WHorizontalList row, Setting<?> setting, SettingGroup group) {
        WButton resetButton = row.add(theme.button("Reset")).widget();
        resetButton.action = () -> {
            setting.reset();
            reloadWithScrollPreservation();
        };
        resetButton.tooltip = "Reset to default";
    }

    private String formatSettingName(String name) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : name.toCharArray()) {
            if (c == '-' || c == '_') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void setEnumValue(EnumSetting setting, Object value) {
        if (value != null) {
            setting.set((Enum) value);
        }
    }

    private void reloadWithScrollPreservation() {
        if (view == null)
            return;

        double scroll = getScrollPosition(view);
        loadTab(currentTab);
        setScrollPosition(view, scroll);
    }

    private void saveScrollPosition() {
        if (view != null) {
            savedScroll = getScrollPosition(view);
        }
    }

    private void restoreScrollPosition() {
        if (savedScroll != -1 && view != null) {
            setScrollPosition(view, savedScroll);
            savedScroll = -1;
        }
    }

    private double getScrollPosition(Object view) {
        try {
            Class<?> clazz = view.getClass();
            while (clazz != null) {
                try {
                    java.lang.reflect.Field field = clazz.getDeclaredField("scroll");
                    field.setAccessible(true);
                    return field.getDouble(view);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private void setScrollPosition(Object view, double scroll) {
        try {
            Class<?> clazz = view.getClass();
            while (clazz != null) {
                try {
                    java.lang.reflect.Field field = clazz.getDeclaredField("scroll");
                    field.setAccessible(true);
                    field.setDouble(view, scroll);

                    if (view instanceof meteordevelopment.meteorclient.gui.widgets.containers.WView wView) {
                        wView.invalidate();
                    }
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void tick() {
        super.tick();

        for (Map.Entry<SettingGroup, List<Setting<?>>> entry : lastVisibleSettings.entrySet()) {
            SettingGroup group = entry.getKey();
            List<Setting<?>> lastVisible = entry.getValue();

            List<Setting<?>> currentVisible = new java.util.ArrayList<>();
            for (Setting<?> setting : group) {
                if (setting.isVisible()) {
                    currentVisible.add(setting);
                }
            }

            if (!lastVisible.equals(currentVisible)) {
                reloadWithScrollPreservation();
                return;
            }
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}

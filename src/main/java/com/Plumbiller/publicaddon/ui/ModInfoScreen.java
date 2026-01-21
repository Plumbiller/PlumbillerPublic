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
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.screen.Screen;

import static meteordevelopment.meteorclient.MeteorClient.mc;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModInfoScreen extends WidgetScreen {
    private final Screen parent;
    private int currentTab = 0;
    private WContainer content;
    private meteordevelopment.meteorclient.gui.widgets.containers.WView view;

    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[(.*?)\\]\\((.*?)\\)");
    private static final Pattern MODULE_PATTERN = Pattern.compile("\\[\\[(.*?)\\]\\]");

    // Track visible settings to detect changes
    private final java.util.Map<SettingGroup, java.util.List<Setting<?>>> lastVisibleSettings = new java.util.HashMap<>();

    // Saved state for returning from sub-screens
    private double savedScroll = -1;

    public ModInfoScreen(Screen parent) {
        super(GuiThemes.get(), "PlumbillerPublic Info");
        this.parent = parent;
    }

    @Override
    public void initWidgets() {
        WWindow window = super.add(theme.window("PlumbillerPublic Info")).center().minWidth(600).widget();

        window.clear();

        // Tabs
        WHorizontalList tabs = window.add(theme.horizontalList()).expandX().widget();

        addTab(tabs, "Overview", 0);
        addTab(tabs, "Features", 1);
        addTab(tabs, "Dependencies", 2);

        window.add(theme.horizontalSeparator()).expandX();

        // Scrollable View
        view = window.add(theme.view()).expandX().widget();
        view.hasScrollBar = true;
        content = view.add(theme.verticalList()).expandX().widget();

        loadTab(currentTab);
        restoreState();
    }

    private void addTab(WHorizontalList list, String name, int index) {
        WButton b = list.add(theme.button(name)).expandX().widget();
        b.action = () -> loadTab(index);
    }

    private void loadTab(int index) {
        this.currentTab = index;
        if (content != null) {
            content.clear();
        }

        String filename = switch (index) {
            case 1 -> "features.md";
            case 2 -> "dependencies.md";
            default -> "overview.md";
        };

        loadAndProcessMarkdown("/assets/publicaddon/info/" + filename);
    }

    private void loadAndProcessMarkdown(String path) {
        if (content == null)
            return;
        try {
            InputStream stream = getClass().getResourceAsStream(path);
            if (stream == null) {
                content.add(theme.label("Error: Could not find " + path));
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                processMarkdownLine(line);
            }
            reader.close();
        } catch (Exception e) {
            content.add(theme.label("Error loading file: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private void processMarkdownLine(String line) {
        line = line.trim();
        if (line.isEmpty()) {
            content.add(theme.label("")); // Empty line spacer
            return;
        }

        Matcher imageMatcher = IMAGE_PATTERN.matcher(line);
        if (imageMatcher.matches()) {
            // Skip images
            return;
        }

        String text = line;

        // Check for dynamic module injection [[ModuleName]]
        Matcher moduleMatcher = MODULE_PATTERN.matcher(text);
        if (moduleMatcher.find()) {
            String moduleName = moduleMatcher.group(1);
            injectSettingsForModule(moduleName);
            return;
        }

        // Standard markdown rendering
        int maxWidth = maxWidth();

        if (text.startsWith("# ")) {
            content.add(theme.label(text.substring(2))).expandX();
            content.add(theme.horizontalSeparator()).expandX();
        } else if (text.startsWith("## ")) {
            addWrappedLabel(text.substring(3), maxWidth);
        } else if (text.startsWith("### ")) {
            addWrappedLabel(text.substring(4), maxWidth);
        } else if (text.startsWith("* ") || text.startsWith("- ")) {
            addWrappedLabel("• " + text.substring(2), maxWidth);
        } else {
            addWrappedLabel(text, maxWidth);
        }
    }

    private int maxWidth() {
        return Math.min(this.width - 80, 1000);
    }

    private void addWrappedLabel(String text, int maxWidth) {
        if (client == null || client.textRenderer == null) {
            content.add(theme.label(text)).expandX();
            return;
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;
            if (client.textRenderer.getWidth(testLine) > maxWidth) {
                if (currentLine.length() > 0) {
                    content.add(theme.label(currentLine.toString())).expandX();
                }
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }

        if (currentLine.length() > 0) {
            content.add(theme.label(currentLine.toString())).expandX();
        }
    }

    private void injectSettingsForModule(String moduleName) {
        // Clean up name (remove potential file extensions if user included them)
        if (moduleName.endsWith(".java"))
            moduleName = moduleName.substring(0, moduleName.length() - 5);
        if (moduleName.endsWith(".class"))
            moduleName = moduleName.substring(0, moduleName.length() - 6);

        // Find module by simple name
        for (Module module : Modules.get().getAll()) {
            if (module.getClass().getSimpleName().equalsIgnoreCase(moduleName)) {
                injectModuleSettings(module.getClass());
                return;
            }
        }
        content.add(theme.label("Module not found: " + moduleName));
    }

    // Track sections if needed
    private final Map<SettingGroup, WSection> sectionMap = new HashMap<>();

    private void injectModuleSettings(Class<? extends Module> moduleClass) {
        Module module = Modules.get().get(moduleClass);
        if (module != null) {
            sectionMap.clear();
            lastVisibleSettings.clear();

            content.add(theme.horizontalSeparator()).expandX();
            content.add(theme.label(module.name + " Configuration")).expandX();

            for (SettingGroup group : module.settings) {
                // Create the section
                WSection section = content.add(theme.section(group.name)).expandX().widget();
                section.setExpanded(true);
                sectionMap.put(group, section);

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
        } else {
            content.add(theme.label("Module not found: " + moduleClass.getSimpleName()));
        }
    }

    private void triggerReload() {
        if (view == null)
            return;

        // Save scroll position using reflection
        double storedScroll = 0;
        try {
            Class<?> clazz = view.getClass();
            while (clazz != null) {
                try {
                    java.lang.reflect.Field f = clazz.getDeclaredField("scroll");
                    f.setAccessible(true);
                    storedScroll = f.getDouble(view);
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        loadTab(currentTab);

        // Restore scroll position using reflection
        try {
            Class<?> clazz = view.getClass();
            while (clazz != null) {
                try {
                    java.lang.reflect.Field f = clazz.getDeclaredField("scroll");
                    f.setAccessible(true);
                    f.setDouble(view, storedScroll);
                    view.invalidate(); // Force layout update
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void storeState() {
        if (view != null) {
            try {
                Class<?> clazz = view.getClass();
                while (clazz != null) {
                    try {
                        java.lang.reflect.Field f = clazz.getDeclaredField("scroll");
                        f.setAccessible(true);
                        savedScroll = f.getDouble(view);
                        return;
                    } catch (NoSuchFieldException e) {
                        clazz = clazz.getSuperclass();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void restoreState() {
        if (savedScroll != -1 && view != null) {
            try {
                Class<?> clazz = view.getClass();
                while (clazz != null) {
                    try {
                        java.lang.reflect.Field f = clazz.getDeclaredField("scroll");
                        f.setAccessible(true);
                        f.setDouble(view, savedScroll);
                        view.invalidate();
                        savedScroll = -1; // Reset after restoring
                        return;
                    } catch (NoSuchFieldException e) {
                        clazz = clazz.getSuperclass();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Callback adapter
    private void triggerReload(SettingGroup group) {
        triggerReload();
    }

    @Override
    public void tick() {
        super.tick();

        boolean needsRefresh = false;

        // Check for visibility changes
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
                needsRefresh = true;
                break;
            }
        }

        if (needsRefresh) {
            triggerReload();
        }
    }

    private void addSettingWidget(WContainer parent, Setting<?> setting, SettingGroup group) {
        WHorizontalList row = parent.add(theme.horizontalList()).expandX().widget();
        row.add(theme.label(formatSettingName(setting.name)));

        if (setting instanceof BoolSetting bs) {
            WCheckbox checkbox = row.add(theme.checkbox(bs.get())).expandCellX().widget();
            checkbox.action = () -> {
                bs.set(checkbox.checked);
                triggerReload(group);
            };
            row.add(theme.label("")).expandX();
            addResetButton(row, setting, group);

        } else if (setting instanceof IntSetting is) {
            // Use the setting's sliderMin/sliderMax for the slider range
            int sliderMin = is.sliderMin;
            int sliderMax = is.sliderMax;
            boolean noSlider = (sliderMin == 0 && sliderMax == 0);

            WIntEdit edit = row.add(theme.intEdit(
                    is.get(), // current value
                    is.min, // min value (for validation)
                    is.max, // max value (for validation)
                    sliderMin, // slider min (from .sliderRange())
                    sliderMax, // slider max (from .sliderRange())
                    noSlider // noSlider flag
            )).minWidth(100).expandCellX().widget();

            edit.action = () -> is.set(edit.get());
            edit.actionOnRelease = () -> {
                is.set(edit.get());
                triggerReload(group);
            };
            row.add(theme.label("")).expandX();
            addResetButton(row, setting, group);

        } else if (setting instanceof DoubleSetting ds) {
            // Use the setting's sliderMin/sliderMax for the slider range
            double sliderMin = ds.sliderMin;
            double sliderMax = ds.sliderMax;
            boolean noSlider = (sliderMin == 0 && sliderMax == 0);

            WDoubleEdit edit = row.add(theme.doubleEdit(
                    ds.get(), // current value
                    ds.min, // min value (for validation)
                    ds.max, // max value (for validation)
                    sliderMin, // slider min (from .sliderRange())
                    sliderMax, // slider max (from .sliderRange())
                    ds.decimalPlaces, // decimal places
                    noSlider // noSlider flag
            )).minWidth(100).expandCellX().widget();

            edit.action = () -> ds.set(edit.get());
            edit.actionOnRelease = () -> {
                ds.set(edit.get());
                triggerReload(group);
            };
            row.add(theme.label("")).expandX();
            addResetButton(row, setting, group);

        } else if (setting instanceof StringSetting ss) {
            WTextBox textBox = row.add(theme.textBox(ss.get())).minWidth(100).expandCellX().widget();
            textBox.action = () -> ss.set(textBox.get());
            textBox.actionOnUnfocused = () -> {
                ss.set(textBox.get());
                triggerReload(group);
            };
            row.add(theme.label("")).expandX();
            addResetButton(row, setting, group);

        } else if (setting instanceof EnumSetting<?> es) {
            Object[] enumValues = es.get().getClass().getEnumConstants();
            if (enumValues != null) {
                WDropdown<Object> dropdown = row.add(theme.dropdown(enumValues, es.get())).expandCellX().widget();
                dropdown.action = () -> {
                    setEnumUntyped(es, dropdown.get());
                    triggerReload(group);
                };
            }
            row.add(theme.label("")).expandX();
            addResetButton(row, setting, group);

        } else if (setting instanceof ColorSetting cs) {
            // Add color preview quad
            row.add(theme.quad(cs.get())).widget();

            // Add edit button to open color picker
            WButton edit = row.add(theme.button("Edit")).widget();
            edit.action = () -> {
                storeState();
                mc.setScreen(new meteordevelopment.meteorclient.gui.screens.settings.ColorSettingScreen(theme, cs));
            };

            row.add(theme.label("")).expandX();
            addResetButton(row, setting, group);

        } else if (setting instanceof StringListSetting sls) {
            row.add(theme.label("")).expandX();
            addResetButton(row, setting, group);

            // Nested list for items
            WVerticalList list = parent.add(theme.verticalList()).expandX().widget();
            for (int i = 0; i < sls.get().size(); i++) {
                int index = i;
                WHorizontalList itemRow = list.add(theme.horizontalList()).expandX().widget();
                WTextBox box = itemRow.add(theme.textBox(sls.get().get(i))).minWidth(100).expandX().widget();
                box.action = () -> {
                    if (index < sls.get().size()) {
                        sls.get().set(index, box.get());
                    }
                };

                WMinus remove = itemRow.add(theme.minus()).widget();
                remove.action = () -> {
                    if (index < sls.get().size()) {
                        sls.get().remove(index);
                    }
                };
            }
            WHorizontalList addRow = list.add(theme.horizontalList()).expandX().widget();
            WPlus add = addRow.add(theme.plus()).widget();
            add.action = () -> sls.get().add("");

        } else if (setting instanceof ItemListSetting ils) {
            WButton select = row.add(theme.button("Select")).expandCellX().widget();
            select.action = () -> {
                storeState();
                mc.setScreen(new meteordevelopment.meteorclient.gui.screens.settings.ItemListSettingScreen(theme, ils));
            };
            row.add(theme.label("")).expandX();
            addResetButton(row, setting, group);

        } else {
            row.add(theme.label(setting.get().toString())).expandCellX();
            row.add(theme.label("")).expandX();
            addResetButton(row, setting, group);
        }
    }

    private void addResetButton(WHorizontalList row, Setting<?> setting, SettingGroup group) {
        WButton reset = row.add(theme.button("Reset")).widget();
        reset.action = () -> {
            setting.reset();
            triggerReload(group);
        };
        reset.tooltip = "Reset to default";
    }

    private String formatSettingName(String name) {
        // Convert "some-setting-name" to "Some Setting Name"
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
    private void setEnumUntyped(EnumSetting setting, Object value) {
        if (value != null)
            setting.set((Enum) value);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}

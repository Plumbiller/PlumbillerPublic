package com.Plumbiller.publicaddon.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.AbstractTexture;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class ModInfoScreen extends Screen {
    private final Screen parent;
    private int currentTab = 0;

    private static final Map<Integer, List<FormattedLine>> contentCache = new HashMap<>();

    private static final Map<String, Object> globalImageCache = new HashMap<>();

    private record PixelRect(int x, int y, int width, int height, int color) {
    }

    private record ImageInfo(Identifier textureId, List<PixelRect> pixels, int width, int height) {
    }

    public static void preload() {
        String[] files = { "overview.md", "features.md", "dependencies.md" };
        for (String file : files) {
            preloadImagesFrom("/assets/publicaddon/info/" + file);
        }
    }

    private static void preloadImagesFrom(String path) {
        try (InputStream stream = ModInfoScreen.class.getResourceAsStream(path)) {
            if (stream == null)
                return;
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = IMAGE_PATTERN.matcher(line);
                if (m.matches()) {
                    String cleanPath = m.group(2).toLowerCase(java.util.Locale.ROOT);
                    if (cleanPath.contains(":"))
                        cleanPath = cleanPath.split(":")[1];
                    if (cleanPath.startsWith("/"))
                        cleanPath = cleanPath.substring(1);
                    String resourcePath = "/assets/publicaddon/" + cleanPath;

                    getOrLoadImage(resourcePath);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ImageInfo getOrLoadImage(String path) {
        Object cached = globalImageCache.get(path);

        if (cached instanceof ImageInfo) {
            return (ImageInfo) cached;
        }

        if (cached instanceof BufferedImage) {
            return uploadOrConvertImage(path, (BufferedImage) cached);
        }

        try (InputStream stream = ModInfoScreen.class.getResourceAsStream(path)) {
            if (stream == null)
                return null;
            BufferedImage original = ImageIO.read(stream);
            if (original == null)
                return null;

            int origW = original.getWidth();
            int origH = original.getHeight();
            int newW = Math.max(origW / 2, Math.min(50, origW));
            int newH = Math.max(origH / 2, Math.min(50, origH));

            java.awt.Image scaled = original.getScaledInstance(newW, newH, java.awt.Image.SCALE_SMOOTH);
            BufferedImage result = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = result.createGraphics();
            g.drawImage(scaled, 0, 0, null);
            g.dispose();

            globalImageCache.put(path, result);
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static ImageInfo uploadOrConvertImage(String path, BufferedImage image) {
        Identifier gpuId = null;
        try {
            NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), true);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    nativeImage.setColor(x, y, image.getRGB(x, y));
                }
            }

            Object dynamicTex = null;

            Class<?> textureClass = null;
            try {
                textureClass = Class.forName("net.minecraft.client.texture.NativeImageBackedTexture");
            } catch (ClassNotFoundException e) {
                try {
                    textureClass = Class.forName("net.minecraft.client.texture.DynamicTexture");
                } catch (ClassNotFoundException ex) {
                }
            }

            if (textureClass != null) {
                Constructor<?> candidate = null;
                for (Constructor<?> c : textureClass.getConstructors()) {
                    Class<?>[] types = c.getParameterTypes();
                    if (types.length == 1 && types[0].isAssignableFrom(NativeImage.class)) {
                        candidate = c;
                        break;
                    }
                }

                if (candidate != null) {
                    dynamicTex = candidate.newInstance(nativeImage);

                    try {
                        Method uploadMethod = textureClass.getMethod("upload");
                        uploadMethod.invoke(dynamicTex);
                    } catch (Exception e) {
                    }

                    Identifier id = Identifier.of("publicaddon", "modinfo_img_" + Math.abs(path.hashCode()));

                    Object textureManager = net.minecraft.client.MinecraftClient.getInstance().getTextureManager();
                    Method registerMethod = null;

                    for (Method m : textureManager.getClass().getMethods()) {
                        if (m.getName().equals("registerTexture") || m.getName().equals("method_4616")) {
                            Class<?>[] pts = m.getParameterTypes();
                            if (pts.length == 2 && pts[0] == Identifier.class
                                    && AbstractTexture.class.isAssignableFrom(pts[1])) {
                                registerMethod = m;
                                break;
                            }
                        }
                    }

                    if (registerMethod != null) {
                        registerMethod.invoke(textureManager, id, dynamicTex);
                        gpuId = id;
                        System.out.println("ModInfoScreen: GPU texture uploaded: " + id);
                    }
                }
            }

        } catch (Throwable e) {
            System.out.println("ModInfoScreen: GPU upload failed, falling back to CPU.");
            e.printStackTrace();
        }

        if (gpuId != null) {
            ImageInfo info = new ImageInfo(gpuId, null, image.getWidth(), image.getHeight());
            globalImageCache.put(path, info);
            return info;
        }

        List<PixelRect> optimizedRects = optimizePixels(image);
        ImageInfo info = new ImageInfo(null, optimizedRects, image.getWidth(), image.getHeight());
        globalImageCache.put(path, info);
        return info;
    }

    private static List<PixelRect> optimizePixels(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        List<List<PixelRect>> rows = new ArrayList<>(height);

        for (int y = 0; y < height; y++) {
            List<PixelRect> rowSegments = new ArrayList<>();
            int startX = 0;
            int currentColor = image.getRGB(0, y);

            for (int x = 1; x < width; x++) {
                int pixel = image.getRGB(x, y);
                if (pixel != currentColor) {
                    if ((currentColor >>> 24) != 0) {
                        rowSegments.add(new PixelRect(startX, y, x - startX, 1, currentColor));
                    }
                    startX = x;
                    currentColor = pixel;
                }
            }
            if ((currentColor >>> 24) != 0) {
                rowSegments.add(new PixelRect(startX, y, width - startX, 1, currentColor));
            }
            rows.add(rowSegments);
        }

        List<PixelRect> finalRects = new ArrayList<>();

        for (List<PixelRect> row : rows) {
            for (PixelRect seg : row) {
                boolean merged = false;
                for (int i = finalRects.size() - 1; i >= 0; i--) {
                    PixelRect candidate = finalRects.get(i);
                    if (candidate.y + candidate.height == seg.y) {
                        if (candidate.x == seg.x && candidate.width == seg.width && candidate.color == seg.color) {
                            finalRects.set(i, new PixelRect(candidate.x, candidate.y, candidate.width,
                                    candidate.height + 1, candidate.color));
                            merged = true;
                            break;
                        }
                    }
                    if (candidate.y + candidate.height < seg.y - 1)
                        break;
                }

                if (!merged) {
                    finalRects.add(seg);
                }
            }
        }

        return finalRects;
    }

    private double scrollOffset = 0;
    private double maxScroll = 0;
    private static final int SCROLL_SPEED = 20;
    private static final int PADDING = 20;

    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[(.*?)\\]\\((.*?)\\)");

    public ModInfoScreen(Screen parent) {
        super(Text.literal("PlumbillerPublic Info"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 100;
        int buttonHeight = 20;
        int spacing = 10;
        int totalWidth = (buttonWidth * 3) + (spacing * 2);
        int startX = (this.width - totalWidth) / 2;
        int startY = PADDING;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Overview"), button -> setTab(0))
                .dimensions(startX, startY, buttonWidth, buttonHeight)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Features"), button -> setTab(1))
                .dimensions(startX + buttonWidth + spacing, startY, buttonWidth, buttonHeight)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Dependencies"), button -> setTab(2))
                .dimensions(startX + (buttonWidth + spacing) * 2, startY, buttonWidth, buttonHeight)
                .build());

        setTab(currentTab);
    }

    private void setTab(int index) {
        this.currentTab = index;
        this.scrollOffset = 0;

        if (!contentCache.containsKey(index)) {
            String filename = switch (index) {
                case 1 -> "features.md";
                case 2 -> "dependencies.md";
                default -> "overview.md";
            };
            loadAndProcessMarkdown(index, "/assets/publicaddon/info/" + filename);
        }

        recalculateMaxScroll();
    }

    private void loadAndProcessMarkdown(int tabIndex, String path) {
        List<FormattedLine> lines = new ArrayList<>();
        int maxWidth = this.width - (PADDING * 2) - 20;

        try {
            InputStream stream = getClass().getResourceAsStream(path);
            if (stream == null) {
                lines.add(new FormattedLine(Text.literal("Error: Could not find " + path).asOrderedText(), null,
                        0xFFFF0000, 10, 5));
            } else {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    processMarkdownLine(line, lines, maxWidth);
                }
                reader.close();
            }
        } catch (Exception e) {
            lines.add(new FormattedLine(Text.literal("Error loading file: " + e.getMessage()).asOrderedText(), null,
                    0xFFFF0000, 10, 5));
            e.printStackTrace();
        }

        contentCache.put(tabIndex, lines);
    }

    private void processMarkdownLine(String line, List<FormattedLine> outputLines, int maxWidth) {
        line = line.trim();
        if (line.isEmpty()) {
            outputLines.add(new FormattedLine(OrderedText.EMPTY, null, 0, 10, 5));
            return;
        }

        Matcher imageMatcher = IMAGE_PATTERN.matcher(line);
        if (imageMatcher.matches()) {
            String path = imageMatcher.group(2).toLowerCase(java.util.Locale.ROOT);
            String cleanPath = path;
            if (cleanPath.contains(":"))
                cleanPath = cleanPath.split(":")[1];
            if (cleanPath.startsWith("/"))
                cleanPath = cleanPath.substring(1);
            String resourcePath = "/assets/publicaddon/" + cleanPath;

            ImageInfo info = getOrLoadImage(resourcePath);

            if (info != null) {
                int displayWidth = Math.min(maxWidth > 50 ? maxWidth : 350, info.width);
                float aspectRatio = (float) info.height / info.width;
                int displayHeight = (int) (displayWidth * aspectRatio);

                outputLines.add(new FormattedLine(null, resourcePath, 0xFFFFFFFF, displayHeight, 10));
            } else {
                outputLines.add(new FormattedLine(Text.literal("[Image pending: " + resourcePath + "]").asOrderedText(),
                        null, 0xFFFF0000, 20, 5));
            }
            return;
        }

        int color = 0xFF000000;
        boolean bold = false;
        int extraSpacing = 2;
        String text = line;

        if (text.startsWith("# ")) {
            color = 0xFF2222DD;
            bold = true;
            text = text.substring(2);
            extraSpacing = 10;
        } else if (text.startsWith("## ")) {
            color = 0xFF444444;
            bold = true;
            text = text.substring(3);
            extraSpacing = 5;
        } else if (text.startsWith("### ")) {
            color = 0xFF666666;
            bold = true;
            text = text.substring(4);
            extraSpacing = 3;
        } else if (text.startsWith("* ") || text.startsWith("- ")) {
            text = "• " + text.substring(2);
        }

        text = text.replace("**", "");

        net.minecraft.text.MutableText mutableText = Text.literal(text);
        if (bold)
            mutableText.formatted(Formatting.BOLD);

        List<OrderedText> wrapped = this.textRenderer.wrapLines(mutableText, maxWidth);

        for (int i = 0; i < wrapped.size(); i++) {
            outputLines.add(new FormattedLine(wrapped.get(i), null, color, this.textRenderer.fontHeight,
                    i == wrapped.size() - 1 ? extraSpacing : 2));
        }
    }

    private void recalculateMaxScroll() {
        if (!contentCache.containsKey(currentTab))
            return;
        List<FormattedLine> lines = contentCache.get(currentTab);

        int totalHeight = 0;
        for (FormattedLine line : lines) {
            totalHeight += line.height + line.spacing;
        }

        int viewportHeight = this.height - (PADDING + 40 + PADDING) - 20;
        this.maxScroll = Math.max(0, totalHeight - viewportHeight);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int contentTop = PADDING + 40;
        int contentBottom = this.height - PADDING;
        int contentLeft = PADDING;
        int contentRight = this.width - PADDING;

        context.fill(contentLeft, contentTop, contentRight, contentBottom, 0xFFFFFFFF);
        context.drawBorder(contentLeft, contentTop, contentRight - contentLeft, contentBottom - contentTop, 0xFF000000);

        context.enableScissor(contentLeft + 2, contentTop + 2, contentRight - 2, contentBottom - 2);

        List<FormattedLine> lines = contentCache.get(currentTab);
        if (lines != null) {
            int y = (int) (contentTop + 10 - scrollOffset);

            for (FormattedLine line : lines) {
                if (y + line.height > contentTop && y < contentBottom) {
                    if (line.imagePath != null) {
                        ImageInfo info = getOrLoadImage(line.imagePath);
                        if (info != null) {
                            int displayWidth = Math.min(contentRight - contentLeft - 20, info.width);
                            int displayHeight = line.height;
                            int startX = contentLeft + (contentRight - contentLeft - displayWidth) / 2;

                            if (info.textureId != null) {
                                context.drawTexturedQuad(info.textureId, startX, y, displayWidth, displayHeight, 0f, 0f,
                                        1f, 1f);
                            } else if (info.pixels != null) {
                                float scaleX = (float) displayWidth / info.width;
                                float scaleY = (float) displayHeight / info.height;

                                for (PixelRect r : info.pixels) {
                                    int px = startX + (int) (r.x * scaleX);
                                    int py = y + (int) (r.y * scaleY);
                                    int pw = (int) Math.max(1, r.width * scaleX);
                                    int ph = (int) Math.max(1, r.height * scaleY);

                                    if (py + ph < contentTop || py > contentBottom)
                                        continue;

                                    context.fill(px, py, px + pw, py + ph, r.color);
                                }
                            }
                        }
                    } else if (line.text != null) {
                        context.drawText(this.textRenderer, line.text, contentLeft + 10, y, line.color, false);
                    }
                }

                y += line.height + line.spacing;
            }
        }

        context.disableScissor();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (verticalAmount * SCROLL_SPEED)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_E) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private record FormattedLine(OrderedText text, String imagePath, int color, int height, int spacing) {
    }
}

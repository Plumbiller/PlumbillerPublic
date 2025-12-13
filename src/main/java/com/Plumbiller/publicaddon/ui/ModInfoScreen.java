package com.Plumbiller.publicaddon.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
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

public class ModInfoScreen extends Screen {
    private final Screen parent;
    private int currentTab = 0; // 0=Overview, 1=Features, 2=Dependencies

    // Content Cache: Tab Index -> List of formatted lines (Static for persistence)
    private static final Map<Integer, List<FormattedLine>> contentCache = new HashMap<>();

    // Image Cache: Path -> Object (BufferedImage [pending] OR ImageInfo [ready])
    private static final Map<String, Object> globalImageCache = new HashMap<>();

    // RLE Rect for manual rendering
    // x, y are relative to image top-left
    private record PixelRect(int x, int y, int width, int height, int color) {
    }

    // Image Handle (wraps either a GPU Texture or CPU Pixel Data)
    private record ImageInfo(Identifier textureId, List<PixelRect> pixels, int width, int height) {
    }

    // Preload images only (safe to call from Main)
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

    // Helper to load/get image info
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

            // Downscale by 2x (Safe compromise for size/quality)
            // For fallback compatibility, we ensure it's not HUGE.
            int origW = original.getWidth();
            int origH = original.getHeight();

            // 2x downscale standard
            int newW = Math.max(origW / 2, Math.min(50, origW));
            int newH = Math.max(origH / 2, Math.min(50, origH));

            // Use AWT smooth scaling
            java.awt.Image scaled = original.getScaledInstance(newW, newH, java.awt.Image.SCALE_SMOOTH);
            BufferedImage result = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = result.createGraphics();
            g.drawImage(scaled, 0, 0, null);
            g.dispose();

            globalImageCache.put(path, result);
            return null; // Pending upload/conversion
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Attempt to upload to GPU, fallback to RLE pixels if failed
    private static ImageInfo uploadOrConvertImage(String path, BufferedImage image) {
        // 1. Try GPU Upload via Reflection
        Identifier gpuId = null;
        try (NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), true)) {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    nativeImage.setColor(x, y, image.getRGB(x, y));
                }
            }

            net.minecraft.client.texture.AbstractTexture dynamicTex = null;

            // Try matching constructor for NativeImageBackedTexture
            try {
                Class<?> clazz = NativeImageBackedTexture.class;
                java.lang.reflect.Constructor<?>[] constructors = clazz.getConstructors();
                for (java.lang.reflect.Constructor<?> c : constructors) {
                    Class<?>[] types = c.getParameterTypes();
                    if (types.length >= 1 && types[0].isAssignableFrom(NativeImage.class)) {
                        Object[] args = new Object[types.length];
                        args[0] = nativeImage;
                        for (int i = 1; i < types.length; i++) {
                            if (types[i] == boolean.class)
                                args[i] = false;
                        }
                        dynamicTex = (net.minecraft.client.texture.AbstractTexture) c.newInstance(args);
                        break;
                    }
                }
            } catch (Exception e) {
                /* Ignore */ }

            if (dynamicTex != null) {
                // Try upload
                try {
                    java.lang.reflect.Method uploadMethod = dynamicTex.getClass().getMethod("upload");
                    uploadMethod.invoke(dynamicTex);
                } catch (Exception e) {
                    /* Ignore */ }

                gpuId = Identifier.of("publicaddon", "modinfo_img_" + Math.abs(path.hashCode()));
                net.minecraft.client.MinecraftClient.getInstance().getTextureManager().registerTexture(gpuId,
                        dynamicTex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (gpuId != null) {
            ImageInfo info = new ImageInfo(gpuId, null, image.getWidth(), image.getHeight());
            globalImageCache.put(path, info);
            return info;
        }

        // 2. FALLBACK: Optimized 2D Greedy Meshing (CPU Rendering)
        // Consolidate Rels to reduce draw calls
        List<PixelRect> optimizedRects = optimizePixels(image);

        System.out.println("ModInfoScreen: Fallback to optimized pixel rendering for " + path + " ("
                + optimizedRects.size() + " rects)");

        ImageInfo info = new ImageInfo(null, optimizedRects, image.getWidth(), image.getHeight());
        globalImageCache.put(path, info);
        return info;
    }

    private static List<PixelRect> optimizePixels(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        List<PixelRect> rects = new ArrayList<>();

        // Pass 1: Horizontal RLE
        // We store this as a list of rows, where each row has a list of segments
        List<List<PixelRect>> rows = new ArrayList<>(height);

        for (int y = 0; y < height; y++) {
            List<PixelRect> rowSegments = new ArrayList<>();
            int startX = 0;
            int currentColor = image.getRGB(0, y);

            for (int x = 1; x < width; x++) {
                int pixel = image.getRGB(x, y);
                if (pixel != currentColor) {
                    if ((currentColor >>> 24) != 0) { // If not transparent
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

        // Pass 2: Vertical Merge (Greedy)
        // We compare current row segments with the "active" list of rectangles we are
        // building.
        // But since we built full rows, we can just iterate Y and try to merge with
        // Y-1.
        // Actually, simpler: Iterate rows. For each segment in Row Y, check if it
        // extends a segment in Row Y-1.

        // Final list structure is tricky to build in one pass if we want optimal
        // merging.
        // Simplified Vertical Merge:
        // We keep a 'pending' list of rects that are "open" at the bottom.

        // Let's just do a naive N^2 merge on the finished list logic, effectively.
        // Actually, iterating Y then X aligns with the data.
        // We will flatten the 1D RLE into a single list for now, but check previous
        // rects.

        // To keep it strictly simpler and performant (O(N)):
        // Just add the 1D RLE rects to the final list, but check if the LAST added rect
        // (if on y-1)
        // has same x, width, color.

        // Better: Comparison Map? Too complex.
        // Let's stick to Horizontal RLE + Vertical adjacency check for perfectly
        // aligned blocks.

        List<PixelRect> finalRects = new ArrayList<>();
        // Helper to find merge candidate in the *previous row's added rects* is hard
        // because indices shift.
        // Let's just dump Horizontal RLE because it's already 50x better than pixels.
        // To improve further:

        for (List<PixelRect> row : rows) {
            for (PixelRect seg : row) {
                // Try to find a rect in 'finalRects' that ends at y = seg.y and matches x, w,
                // color
                boolean merged = false;
                // Optimization: Search backwards from end of list, since we add in order.
                // We only need to check rects that "ended" at seg.y - 1.
                for (int i = finalRects.size() - 1; i >= 0; i--) {
                    PixelRect candidate = finalRects.get(i);
                    if (candidate.y + candidate.height == seg.y) {
                        if (candidate.x == seg.x && candidate.width == seg.width && candidate.color == seg.color) {
                            // Merge!
                            finalRects.set(i, new PixelRect(candidate.x, candidate.y, candidate.width,
                                    candidate.height + 1, candidate.color));
                            merged = true;
                            break;
                        }
                    }
                    // If candidate ended way before (y < seg.y - 1), we can stop searching?
                    // No, because we might have skip lines. But here we scan Y sequentially.
                    // So yes, if candidate.y + h < seg.y - 1, we stop.
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

    // Regex for markdown images: ![alt text](path)
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

        // Tab Buttons
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Overview"), button -> setTab(0))
                .dimensions(startX, startY, buttonWidth, buttonHeight)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Features"), button -> setTab(1))
                .dimensions(startX + buttonWidth + spacing, startY, buttonWidth, buttonHeight)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Dependencies"), button -> setTab(2))
                .dimensions(startX + (buttonWidth + spacing) * 2, startY, buttonWidth, buttonHeight)
                .build());

        // Initial load
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
        int maxWidth = this.width - (PADDING * 2) - 20; // -20 for internal padding

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
            outputLines.add(new FormattedLine(OrderedText.EMPTY, null, 0, 10, 5)); // Spacer
            return;
        }

        // Check for Image
        Matcher imageMatcher = IMAGE_PATTERN.matcher(line);
        if (imageMatcher.matches()) {
            String path = imageMatcher.group(2).toLowerCase(java.util.Locale.ROOT);
            String cleanPath = path;
            if (cleanPath.contains(":"))
                cleanPath = cleanPath.split(":")[1];
            if (cleanPath.startsWith("/"))
                cleanPath = cleanPath.substring(1);
            String resourcePath = "/assets/publicaddon/" + cleanPath;

            // Load texture info (layout only)
            ImageInfo info = getOrLoadImage(resourcePath);

            if (info != null) {
                // Improved Sizing: maximize width up to image native width
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
                                // Render via GPU Texture
                                context.drawTexturedQuad(info.textureId, startX, y, displayWidth, displayHeight, 0f, 0f,
                                        1f, 1f);
                            } else if (info.pixels != null) {
                                // Fallback: Render via optimized Rects using context.fill (Safe & Compatible)
                                // We use the standard DrawContext fill which is slower than batching but
                                // our rect optimization (Horizontal+Vertical RLE) reduces the count
                                // significantly.
                                float scaleX = (float) displayWidth / info.width;
                                float scaleY = (float) displayHeight / info.height;

                                for (PixelRect r : info.pixels) {
                                    int px = startX + (int) (r.x * scaleX);
                                    int py = y + (int) (r.y * scaleY);
                                    int pw = (int) Math.max(1, r.width * scaleX);
                                    int ph = (int) Math.max(1, r.height * scaleY);

                                    // Bounding box check to save even more calls
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

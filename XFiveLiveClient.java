package com.xfive.live;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

/** X Five Live HUD. Data is supplied by a future local bridge / companion app. */
public final class XFiveLiveClient implements ClientModInitializer {
    private static boolean visible = true;
    private static KeyBinding toggleKey;
    private static String platform = "Spotify / YouTube";
    private static String song = "Belum ada lagu";
    private static String artist = "Hubungkan companion app";
    private static String lyric = "♪ Lirik akan tampil di sini ♪";
    private static String nextLyric = "Menunggu data lirik tersinkron";
    private static String gift = "Belum ada gift";
    private static final Deque<String> chats = new ArrayDeque<>();

    @Override public void onInitializeClient() {
        chats.add("Chat live belum terhubung");
        chats.add("Bridge companion diperlukan");
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.xfive_live.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8,
                "category.xfive_live"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) visible = !visible;
        });
        HudRenderCallback.EVENT.register((draw, tickCounter) -> {
            if (visible && Minecraft.getInstance().player != null) renderHud(draw);
        });
    }

    private static void renderHud(DrawContext draw) {
        Minecraft mc = Minecraft.getInstance();
        int x = 10, y = 10, w = 220;
        // Faux glass: translucent base + soft edge + subtle top highlight. True blur needs shader support.
        glass(draw, x, y, w, 106);
        text(draw, "X FIVE LIVE", x + 10, y + 7, 0xFF9BE7FF);
        text(draw, clip(platform, 28), x + w - 94, y + 7, 0xFFB8C8D8);
        text(draw, "♫ " + clip(song, 28), x + 10, y + 22, 0xFFFFFFFF);
        text(draw, clip(artist, 30), x + 10, y + 34, 0xFFB8C8D8);
        draw.fill(x + 9, y + 48, x + w - 9, y + 49, 0x557FFFFF);
        text(draw, clip(lyric, 31), x + 10, y + 53, 0xFFFFFFFF);
        text(draw, clip(nextLyric, 31), x + 10, y + 65, 0xFF9AAAB9);
        text(draw, "♥ " + clip(gift, 30), x + 10, y + 80, 0xFFFF9FB5);

        int chatY = y + 112;
        glass(draw, x, chatY, w, 72);
        text(draw, "LIVE CHAT  •  POP-UP", x + 10, chatY + 7, 0xFFFFD58A);
        int row = 0;
        for (String line : chats) {
            if (row >= 3) break;
            text(draw, clip(line, 33), x + 10, chatY + 21 + row * 12, 0xFFEAF2F8);
            row++;
        }
        text(draw, "F8: tampil/sembunyi", x + 10, chatY + 57, 0xFF9AAAB9);
    }

    private static void glass(DrawContext draw, int x, int y, int w, int h) {
        draw.fill(x, y, x + w, y + h, 0xB815202B);
        draw.fill(x, y, x + w, y + 1, 0x667BE6FF);
        draw.fill(x, y, x + 1, y + h, 0x447BE6FF);
        draw.fill(x + w - 1, y, x + w, y + h, 0x337BE6FF);
        draw.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
    }

    private static void text(DrawContext draw, String value, int x, int y, int color) {
        draw.drawText(Minecraft.getInstance().font, Text.literal(value), x, y, color, false);
    }
    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
    private static String safe(String s) { return s == null ? "" : s.replaceAll("[\\r\\n]", " "); }

    /** Companion bridge hook: call on Minecraft client thread. */
    public static void updateMusic(String source, String title, String artistName, String currentLyric, String followingLyric) {
        platform = safe(source); song = safe(title); artist = safe(artistName);
        lyric = safe(currentLyric); nextLyric = safe(followingLyric);
    }
    /** Companion bridge hook: call on Minecraft client thread. */
    public static void updateGift(String username, String giftName, int count) {
        gift = safe(username) + " • " + safe(giftName) + " x" + Math.max(1, count);
    }
    /** Companion bridge hook: call on Minecraft client thread; newest chat appears first. */
    public static void pushChat(String username, String message) {
        chats.addFirst(safe(username) + ": " + safe(message));
        while (chats.size() > 3) chats.removeLast();
    }
}

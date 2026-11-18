package com.gta.launcher.util;

import org.ini4j.Ini;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;

/**
 * Reads and writes the settings.ini consumed by the native client
 * (see app/src/main/cpp/samp/settings.cpp).
 *
 * <p>Only the values exposed by the launcher are rewritten; every other key in
 * an existing settings.ini is preserved as-is.</p>
 */
public class GameSettings {

    public static final String GAME_DIR = "/storage/emulated/0/GTA";
    public static final String SAMP_DIR = GAME_DIR + "/SAMP";
    public static final String FILE_PATH = SAMP_DIR + "/settings.ini";

    public static final String DEFAULT_HOST = "141.95.234.17";
    public static final int DEFAULT_PORT = 1417;
    public static final String DEFAULT_VERSION = "0.3.7";
    public static final float DEFAULT_FONT_SIZE = 30f;
    public static final int DEFAULT_FPS_LIMIT = 60;
    public static final int DEFAULT_CHAT_LINES = 6;

    // client
    public String nickname;
    public String host;
    public int port;
    public String password;
    public String version;

    // gameplay
    public boolean autoAim;
    public boolean firstPerson;
    public boolean fpsCounter;
    public boolean cutout;
    public boolean voiceChat;
    public boolean androidKeyboard;
    public boolean dialogs;

    // interface
    public boolean hpArmourText;
    public boolean outfitGuns;
    public boolean pcMoney;
    public boolean radarRect;
    public boolean skybox;
    public boolean snow;

    public float fontSize;
    public int fpsLimit;
    public int chatLines;

    public GameSettings() {
        resetToDefaults();
    }

    public void resetToDefaults() {
        nickname = generateNickname();
        host = DEFAULT_HOST;
        port = DEFAULT_PORT;
        password = "";
        version = DEFAULT_VERSION;

        autoAim = false;
        firstPerson = true;
        fpsCounter = false;
        cutout = false;
        voiceChat = false;
        androidKeyboard = false;
        dialogs = true;

        hpArmourText = false;
        outfitGuns = false;
        pcMoney = false;
        radarRect = false;
        skybox = false;
        snow = false;

        fontSize = DEFAULT_FONT_SIZE;
        fpsLimit = DEFAULT_FPS_LIMIT;
        chatLines = DEFAULT_CHAT_LINES;
    }

    public static String generateNickname() {
        Random random = new Random();
        return String.format(Locale.US, "__android_%04d", random.nextInt(10000));
    }

    public static GameSettings load() {
        GameSettings settings = new GameSettings();
        settings.read();
        return settings;
    }

    public static boolean fileExists() {
        return new File(FILE_PATH).isFile();
    }

    /** Reads settings.ini if present. Missing or malformed files keep the defaults. */
    public boolean read() {
        File file = new File(FILE_PATH);
        if (!file.isFile()) {
            return false;
        }

        try {
            Ini ini = new Ini(file);
            nickname = readString(ini, "client", "name", nickname);
            host = readString(ini, "client", "host", host);
            port = readInt(ini, "client", "port", port);
            password = readString(ini, "client", "password", password);
            version = readString(ini, "client", "version", version);

            autoAim = readBoolean(ini, "client", "autoaim", autoAim);

            firstPerson = readBoolean(ini, "gui", "firstperson", firstPerson);
            fpsCounter = readBoolean(ini, "gui", "fps", fpsCounter);
            cutout = readBoolean(ini, "gui", "cutout", cutout);
            voiceChat = readBoolean(ini, "gui", "VoiceChatEnable", voiceChat);
            androidKeyboard = readBoolean(ini, "gui", "androidkeyboard", androidKeyboard);
            dialogs = readBoolean(ini, "gui", "Dialog", dialogs);

            hpArmourText = readBoolean(ini, "gui", "hparmourtext", hpArmourText);
            outfitGuns = readBoolean(ini, "gui", "outfitguns", outfitGuns);
            pcMoney = readBoolean(ini, "gui", "pcmoney", pcMoney);
            radarRect = readBoolean(ini, "gui", "radarrect", radarRect);
            skybox = readBoolean(ini, "gui", "skybox", skybox);
            snow = readBoolean(ini, "gui", "snow", snow);

            fontSize = readFloat(ini, "gui", "FontSize", fontSize);
            fpsLimit = readInt(ini, "gui", "FPSLimit", fpsLimit);
            chatLines = readInt(ini, "gui", "ChatMaxMessages", chatLines);

            if (nickname == null || nickname.trim().isEmpty()) {
                nickname = generateNickname();
            }
            if (host == null || host.trim().isEmpty()) {
                host = DEFAULT_HOST;
            }
            if (port < 1 || port > 65535) {
                port = DEFAULT_PORT;
            }
            if (fontSize < 12f || fontSize > 60f) {
                fontSize = DEFAULT_FONT_SIZE;
            }
            if (fpsLimit < 20 || fpsLimit > 144) {
                fpsLimit = DEFAULT_FPS_LIMIT;
            }
            if (chatLines < 1 || chatLines > 20) {
                chatLines = DEFAULT_CHAT_LINES;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Writes the launcher-managed values into settings.ini. */
    public void save() throws IOException {
        File dir = new File(SAMP_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + SAMP_DIR);
        }

        File file = new File(FILE_PATH);
        Ini ini;
        if (file.isFile()) {
            try {
                ini = new Ini(file);
            } catch (Exception e) {
                ini = new Ini();
            }
        } else {
            ini = new Ini();
        }

        put(ini, "client", "name", nickname);
        put(ini, "client", "host", host);
        put(ini, "client", "port", String.valueOf(port));
        put(ini, "client", "password", password);
        put(ini, "client", "version", version);
        put(ini, "client", "autoaim", autoAim);

        put(ini, "gui", "firstperson", firstPerson);
        put(ini, "gui", "fps", fpsCounter);
        put(ini, "gui", "cutout", cutout);
        put(ini, "gui", "VoiceChatEnable", voiceChat);
        put(ini, "gui", "androidkeyboard", androidKeyboard);
        put(ini, "gui", "Dialog", dialogs);

        put(ini, "gui", "hparmourtext", hpArmourText);
        put(ini, "gui", "outfitguns", outfitGuns);
        put(ini, "gui", "pcmoney", pcMoney);
        put(ini, "gui", "radarrect", radarRect);
        put(ini, "gui", "skybox", skybox);
        put(ini, "gui", "snow", snow);

        put(ini, "gui", "FontSize", formatFloat(fontSize));
        put(ini, "gui", "FPSLimit", String.valueOf(fpsLimit));
        put(ini, "gui", "ChatMaxMessages", String.valueOf(chatLines));

        // Write through a temporary file so a failure cannot corrupt the
        // settings the native client loads on the next launch.
        File temp = new File(FILE_PATH + ".tmp");
        ini.store(temp);
        if (!temp.renameTo(file)) {
            // Some Android storage providers refuse rename(); fall back to a
            // direct write and clean the temporary file up.
            ini.store(file);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private static String readString(Ini ini, String section, String key, String fallback) {
        try {
            String value = ini.get(section, key);
            return value == null ? fallback : value;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int readInt(Ini ini, String section, String key, int fallback) {
        try {
            String value = ini.get(section, key);
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float readFloat(Ini ini, String section, String key, float fallback) {
        try {
            String value = ini.get(section, key);
            return value == null ? fallback : Float.parseFloat(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean readBoolean(Ini ini, String section, String key, boolean fallback) {
        String value = readString(ini, section, key, null);
        if (value == null) {
            return fallback;
        }

        value = value.trim().toLowerCase(Locale.US);
        if (value.equals("true") || value.equals("yes") || value.equals("on") || value.equals("1")) {
            return true;
        }
        if (value.equals("false") || value.equals("no") || value.equals("off") || value.equals("0")) {
            return false;
        }
        return fallback;
    }

    private static void put(Ini ini, String section, String key, String value) {
        ini.put(section, key, value == null ? "" : value);
    }

    private static void put(Ini ini, String section, String key, boolean value) {
        ini.put(section, key, value ? "true" : "false");
    }

    private static String formatFloat(float value) {
        if (value == Math.round(value)) {
            return String.valueOf((int) value);
        }
        return String.format(Locale.US, "%.1f", value);
    }
}

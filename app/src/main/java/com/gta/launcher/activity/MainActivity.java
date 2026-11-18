package com.gta.launcher.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.gta.game.R;
import com.gta.game.SAMP;
import com.gta.launcher.ui.GtaButton;
import com.gta.launcher.ui.GtaTabView;
import com.gta.launcher.util.GameSettings;
import com.gta.launcher.util.MenuSounds;

import java.io.File;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int ACCENT = 0xFFA9C62F;
    private static final int TEXT = 0xFFF2F3F5;
    private static final int TEXT_DIM = 0xFF9BA1A8;
    private static final int TEXT_FAINT = 0xFF6E747B;
    private static final int WARN = 0xFFFFC857;
    private static final int DANGER = 0xFFFF6B6B;
    private static final int LINE = 0x1FFFFFFF;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // Fonts are loaded once instead of once per settings row.
    private Typeface tfBold, tfMedium, tfRegular;

    private GameSettings settings;
    private boolean storagePermissionGranted = false;
    private boolean pendingStart = false;
    private boolean pendingSave = false;
    private boolean binding = false;
    private boolean dirty = false;

    private Tab currentTab = Tab.PLAY;

    private GtaTabView navPlay, navSettings, navAbout, navExit;
    private View homePanel, settingsPanel, aboutPanel;
    private TextView homeNickname, homeServerValue, homePlayerValue, homeClientValue;
    private TextView homeCacheStatus, homeCacheHint, settingsState;
    private View homeCacheDot;

    private EditText inputNickname, inputHost, inputPort, inputPassword;
    private SwitchCompat switchFirstPerson, switchAutoAim, switchFpsCounter, switchCutout;
    private SwitchCompat switchVoiceChat, switchAndroidKeyboard, switchDialogs;
    private SwitchCompat switchHpArmour, switchOutfitGuns, switchPcMoney;
    private SwitchCompat switchRadarRect, switchSkybox, switchSnow;
    private SeekBar barFps, barFont, barChat;

    private enum Tab {
        PLAY, SETTINGS, ABOUT
    }

    private final ActivityResultLauncher<String[]> requestStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = true;
                for (Boolean granted : permissions.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }

                if (allGranted) {
                    storagePermissionGranted = true;
                    Log.d(TAG, "All storage permissions granted");
                    maybeReloadSettings();
                    runPendingActions();
                } else {
                    storagePermissionGranted = false;
                    pendingStart = false;
                    pendingSave = false;
                    Log.e(TAG, "Storage permissions denied");
                    Toast.makeText(this, R.string.permission_storage_required, Toast.LENGTH_LONG).show();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        requestManageStoragePermission();
                    }
                }
            });

    private final ActivityResultLauncher<Intent> manageStorageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        storagePermissionGranted = true;
                        Log.d(TAG, "Manage storage permission granted");
                        maybeReloadSettings();
                        runPendingActions();
                    } else {
                        storagePermissionGranted = false;
                        pendingStart = false;
                        pendingSave = false;
                        Log.e(TAG, "Manage storage permission denied");
                        Toast.makeText(this, R.string.permission_manage_storage, Toast.LENGTH_LONG).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate started");

        try {
            setFullScreenMode();
            setContentView(R.layout.main_activity);

            MenuSounds.init(this);
            loadFonts();
            applyWindowInsets();

            settings = GameSettings.load();

            initViews();
            setupTabs();
            buildSettingsUi();
            updateHome();
            setupClickListeners();
            playIntro();

            if (GameSettings.fileExists()) {
                setSettingsState(R.string.settings_state_saved, ACCENT);
            }

            checkAndRequestStoragePermission();

            Log.d(TAG, "onCreate completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Critical error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Launcher error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------------
    // Permissions
    // ---------------------------------------------------------------------

    private void checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                storagePermissionGranted = true;
                Log.d(TAG, "Already have manage storage permission");
                maybeReloadSettings();
            } else {
                requestManageStoragePermission();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions = new String[]{
                        android.Manifest.permission.READ_MEDIA_IMAGES,
                        android.Manifest.permission.READ_MEDIA_VIDEO,
                        android.Manifest.permission.READ_MEDIA_AUDIO
                };
            } else {
                permissions = new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                };
            }

            boolean allGranted = true;
            for (String perm : permissions) {
                if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                storagePermissionGranted = true;
                Log.d(TAG, "Already have storage permissions");
                maybeReloadSettings();
            } else {
                requestStoragePermissionLauncher.launch(permissions);
            }
        } else {
            storagePermissionGranted = true;
        }
    }

    private void requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                manageStorageLauncher.launch(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                manageStorageLauncher.launch(intent);
            }
        }
    }

    private void runPendingActions() {
        if (pendingSave) {
            pendingSave = false;
            saveSettings(true);
        }
        if (pendingStart) {
            pendingStart = false;
            startGameIfReady();
        }
    }

    // ---------------------------------------------------------------------
    // Window / lifecycle
    // ---------------------------------------------------------------------

    private void setFullScreenMode() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );

            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            );

            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams params = getWindow().getAttributes();
                params.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(params);
            }

            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in setFullScreenMode: " + e.getMessage());
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            try {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
            } catch (Exception e) {
                Log.e(TAG, "Error in onWindowFocusChanged: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCacheStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Never lose edits when the launcher goes to the background.
        if (dirty && storagePermissionGranted && inputPort != null) {
            saveSettings(false);
        }
    }

    @Override
    public void onBackPressed() {
        if (currentTab != Tab.PLAY) {
            MenuSounds.play(MenuSounds.BACK);
            showTab(Tab.PLAY);
        } else {
            super.onBackPressed();
        }
    }

    // ---------------------------------------------------------------------
    // View setup
    // ---------------------------------------------------------------------

    private void initViews() {
        navPlay = findViewById(R.id.navPlay);
        navSettings = findViewById(R.id.navSettings);
        navAbout = findViewById(R.id.navAbout);
        navExit = findViewById(R.id.navExit);

        homePanel = findViewById(R.id.homePanel);
        settingsPanel = findViewById(R.id.settingsPanel);
        aboutPanel = findViewById(R.id.aboutPanel);

        homeNickname = findViewById(R.id.homeNickname);
        homeServerValue = findViewById(R.id.homeServerValue);
        homePlayerValue = findViewById(R.id.homePlayerValue);
        homeClientValue = findViewById(R.id.homeClientValue);
        homeCacheStatus = findViewById(R.id.homeCacheStatus);
        homeCacheHint = findViewById(R.id.homeCacheHint);
        homeCacheDot = findViewById(R.id.homeCacheDot);

        settingsState = findViewById(R.id.settingsState);
    }

    private void setupTabs() {
        if (navPlay == null) {
            return;
        }

        navPlay.setOnClickListener(v -> showTab(Tab.PLAY));
        navSettings.setOnClickListener(v -> showTab(Tab.SETTINGS));
        navAbout.setOnClickListener(v -> showTab(Tab.ABOUT));
        navExit.setClickSound(MenuSounds.BACK);
        // Small delay so the back sound is audible before the activity closes.
        navExit.setOnClickListener(v -> handler.postDelayed(this::finish, 140));

        showTab(Tab.PLAY);
    }

    private void showTab(Tab tab) {
        if (tab != Tab.SETTINGS && currentTab == Tab.SETTINGS && dirty && storagePermissionGranted) {
            // Keep the settings file in sync when leaving the panel.
            saveSettings(false);
        }
        if (tab != Tab.SETTINGS) {
            hideKeyboard(this);
        }

        final boolean changed = tab != currentTab;
        final int direction = tab.ordinal() >= currentTab.ordinal() ? 1 : -1;
        currentTab = tab;

        navPlay.setActive(tab == Tab.PLAY);
        navSettings.setActive(tab == Tab.SETTINGS);
        navAbout.setActive(tab == Tab.ABOUT);

        showPanel(homePanel, tab == Tab.PLAY, changed, direction);
        showPanel(settingsPanel, tab == Tab.SETTINGS, changed, direction);
        showPanel(aboutPanel, tab == Tab.ABOUT, changed, direction);

        if (tab == Tab.PLAY) {
            updateHome();
        }
    }

    private void showPanel(View panel, boolean visible, boolean animate, int direction) {
        if (panel == null) {
            return;
        }

        panel.animate().cancel();

        if (!visible) {
            panel.setVisibility(View.GONE);
            return;
        }

        panel.setVisibility(View.VISIBLE);
        if (!animate) {
            panel.setAlpha(1f);
            panel.setTranslationX(0f);
            return;
        }

        // Slide in from the side the tab sits on, like a GTA V pause menu page.
        panel.setAlpha(0f);
        panel.setTranslationX(direction * dp(18));
        panel.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .withLayer()
                .start();
    }

    /** Staggered slide-in of the menu when the launcher opens. */
    private void playIntro() {
        View[] navs = {navPlay, navSettings, navAbout, navExit};
        float shift = dp(36);
        for (int i = 0; i < navs.length; i++) {
            View v = navs[i];
            if (v == null) {
                continue;
            }
            float end = (v instanceof GtaTabView && ((GtaTabView) v).isActive()) ? dp(6) : 0f;
            v.setAlpha(0f);
            v.setTranslationX(end - shift);
            v.animate()
                    .alpha(1f)
                    .translationX(end)
                    .setStartDelay(60L + 70L * i)
                    .setDuration(260)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .withLayer()
                    .start();
        }

        View host = findViewById(R.id.panelHost);
        if (host != null) {
            host.setAlpha(0f);
            host.setTranslationX(dp(28));
            host.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setStartDelay(160)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .withLayer()
                    .start();
        }
    }

    private void setupClickListeners() {
        View startButton = findViewById(R.id.startButton);
        if (startButton != null) {
            if (startButton instanceof GtaButton) {
                ((GtaButton) startButton).setClickSound(MenuSounds.LAUNCH);
            }
            startButton.setOnClickListener(v -> {
                if (storagePermissionGranted) {
                    startGame();
                } else {
                    pendingStart = true;
                    checkAndRequestStoragePermission();
                }
            });
        }

        View editSettings = findViewById(R.id.btnHomeSettings);
        if (editSettings != null) {
            editSettings.setOnClickListener(v -> showTab(Tab.SETTINGS));
        }

        View save = findViewById(R.id.btnSaveSettings);
        if (save != null) {
            save.setOnClickListener(v -> saveSettings(true));
        }

        View reset = findViewById(R.id.btnResetSettings);
        if (reset != null) {
            reset.setOnClickListener(v -> confirmReset());
        }

        View telegram = findViewById(R.id.btnTelegram);
        if (telegram != null) {
            telegram.setOnClickListener(v -> openUrl("https://t.me/kuzia15"));
        }

        View report = findViewById(R.id.btnReport);
        if (report != null) {
            report.setOnClickListener(v -> openUrl("https://t.me/kuzia15"));
        }

        View author = findViewById(R.id.authorText);
        if (author != null) {
            author.setOnClickListener(v -> openUrl("https://t.me/kuzia15"));
        }
    }

    // ---------------------------------------------------------------------
    // Settings UI
    // ---------------------------------------------------------------------

    private void buildSettingsUi() {
        LinearLayout container = findViewById(R.id.settingsContainer);
        if (container == null) {
            return;
        }

        binding = true;
        container.removeAllViews();

        addSection(getString(R.string.section_client));
        inputNickname = addTextRow(getString(R.string.row_nickname), getString(R.string.hint_nickname),
                settings.nickname, InputType.TYPE_CLASS_TEXT);
        inputNickname.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        inputHost = addTextRow(getString(R.string.row_host), getString(R.string.hint_host),
                settings.host, InputType.TYPE_CLASS_TEXT);
        inputPort = addTextRow(getString(R.string.row_port), getString(R.string.hint_port),
                String.valueOf(settings.port), InputType.TYPE_CLASS_NUMBER);
        inputPort.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        inputPassword = addTextRow(getString(R.string.row_password), getString(R.string.hint_password),
                settings.password, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        addSection(getString(R.string.section_gameplay));
        switchFirstPerson = addToggleRow(getString(R.string.row_first_person),
                getString(R.string.hint_first_person), settings.firstPerson);
        switchAutoAim = addToggleRow(getString(R.string.row_autoaim),
                getString(R.string.hint_autoaim), settings.autoAim);
        barFps = addSliderRow(getString(R.string.row_fps_limit), getString(R.string.hint_fps_limit),
                30, 144, 6, nearestMultiple(clamp(settings.fpsLimit, 30, 144), 30, 6), "%d FPS");
        switchFpsCounter = addToggleRow(getString(R.string.row_fps_counter),
                getString(R.string.hint_fps_counter), settings.fpsCounter);
        switchCutout = addToggleRow(getString(R.string.row_cutout),
                getString(R.string.hint_cutout), settings.cutout);

        addSection(getString(R.string.section_interface));
        barFont = addSliderRow(getString(R.string.row_font_size), getString(R.string.hint_font_size),
                20, 46, 2, clamp(Math.round(settings.fontSize), 20, 46), "%d");
        barChat = addSliderRow(getString(R.string.row_chat_lines), getString(R.string.hint_chat_lines),
                3, 12, 1, clamp(settings.chatLines, 3, 12), "%d");
        switchAndroidKeyboard = addToggleRow(getString(R.string.row_android_keyboard),
                getString(R.string.hint_android_keyboard), settings.androidKeyboard);
        switchDialogs = addToggleRow(getString(R.string.row_dialogs),
                getString(R.string.hint_dialogs), settings.dialogs);
        switchHpArmour = addToggleRow(getString(R.string.row_hp_armour),
                getString(R.string.hint_hp_armour), settings.hpArmourText);
        switchOutfitGuns = addToggleRow(getString(R.string.row_outfit_guns),
                getString(R.string.hint_outfit_guns), settings.outfitGuns);
        switchPcMoney = addToggleRow(getString(R.string.row_pc_money),
                getString(R.string.hint_pc_money), settings.pcMoney);
        switchRadarRect = addToggleRow(getString(R.string.row_radar_rect),
                getString(R.string.hint_radar_rect), settings.radarRect);
        switchSkybox = addToggleRow(getString(R.string.row_skybox),
                getString(R.string.hint_skybox), settings.skybox);
        switchSnow = addToggleRow(getString(R.string.row_snow),
                getString(R.string.hint_snow), settings.snow);

        addSection(getString(R.string.section_voice));
        switchVoiceChat = addToggleRow(getString(R.string.row_voice_chat),
                getString(R.string.hint_voice_chat), settings.voiceChat);

        binding = false;
        dirty = false;
    }

    private void addSection(String title) {
        LinearLayout container = findViewById(R.id.settingsContainer);
        if (container == null) {
            return;
        }

        if (container.getChildCount() > 0) {
            View spacer = new View(this);
            container.addView(spacer, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));
        }

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        View bar = new View(this);
        bar.setBackgroundColor(ACCENT);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(3), dp(13));
        header.addView(bar, barParams);

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(ACCENT);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        label.setLetterSpacing(0.3f);
        label.setIncludeFontPadding(false);
        label.setTypeface(tfBold);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = dp(8);
        header.addView(label, labelParams);

        View line = new View(this);
        line.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(0, dp(1), 1f);
        lineParams.leftMargin = dp(10);
        header.addView(line, lineParams);

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.bottomMargin = dp(8);
        container.addView(header, headerParams);
    }

    private LinearLayout addRow() {
        LinearLayout container = findViewById(R.id.settingsContainer);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.launcher_row_bg);
        row.setPadding(dp(13), dp(9), dp(13), dp(9));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(6);
        container.addView(row, params);
        return row;
    }

    private SwitchCompat addToggleRow(String title, String hint, boolean value) {
        LinearLayout row = addRow();
        row.setClickable(true);
        row.setFocusable(false);
        row.setSoundEffectsEnabled(false);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = createRowLabel(title);
        top.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        SwitchCompat toggle = new SwitchCompat(this);
        toggle.setChecked(value);
        toggle.setThumbTintList(ColorStateList.valueOf(0xFFF4F5F7));
        toggle.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{ACCENT, 0x4DFFFFFF}));
        top.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.addView(top);
        addHint(row, hint);

        row.setOnClickListener(v -> toggle.toggle());
        toggle.setSoundEffectsEnabled(false);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!binding) {
                markDirty();
                MenuSounds.play(MenuSounds.TOGGLE);
                buttonView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        });

        return toggle;
    }

    private EditText addTextRow(String title, String hint, String value, int inputType) {
        LinearLayout row = addRow();

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = createRowLabel(title);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.rightMargin = dp(10);
        top.addView(label, labelParams);

        EditText input = new EditText(this);
        input.setText(value);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        input.setTextColor(TEXT);
        input.setHintTextColor(0x66FFFFFF);
        input.setBackgroundResource(R.drawable.launcher_input_bg);
        input.setPadding(dp(10), dp(6), dp(10), dp(6));
        input.setMinHeight(dp(38));
        input.setIncludeFontPadding(false);
        input.setTypeface(tfMedium);

        top.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f));

        row.addView(top);
        addHint(row, hint);

        input.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!binding) {
                    markDirty();
                    updateHomePreview();
                }
            }
        });

        return input;
    }

    private SeekBar addSliderRow(String title, String hint, int min, int max, int step,
                                 int value, final String format) {
        LinearLayout row = addRow();

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = createRowLabel(title);
        top.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView valueView = new TextView(this);
        valueView.setTextColor(ACCENT);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        valueView.setLetterSpacing(0.08f);
        valueView.setIncludeFontPadding(false);
        valueView.setTypeface(tfBold);
        top.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.addView(top);

        SeekBar bar = new SeekBar(this);
        bar.setMax((max - min) / step);
        bar.setProgress((value - min) / step);
        bar.setSplitTrack(false);
        bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        bar.setThumbTintList(ColorStateList.valueOf(0xFFF4F5F7));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        row.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addHint(row, hint);

        final Runnable refresh = () -> valueView.setText(
                String.format(Locale.US, format, min + bar.getProgress() * step));
        refresh.run();

        final int[] last = {bar.getProgress()};
        bar.setSoundEffectsEnabled(false);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                refresh.run();
                if (fromUser && !binding) {
                    markDirty();
                    if (progress != last[0]) {
                        MenuSounds.play(MenuSounds.NAV);
                        seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                }
                last[0] = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        return bar;
    }

    private TextView createRowLabel(String title) {
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(TEXT);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        label.setIncludeFontPadding(false);
        label.setTypeface(tfMedium);
        return label;
    }

    private void addHint(LinearLayout row, String hint) {
        if (hint == null || hint.isEmpty()) {
            return;
        }
        TextView hintView = new TextView(this);
        hintView.setText(hint);
        hintView.setTextColor(TEXT_FAINT);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        hintView.setLineSpacing(dp(1), 1f);
        hintView.setTypeface(tfRegular);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(2);
        row.addView(hintView, params);
    }

    private void markDirty() {
        dirty = true;
        setSettingsState(R.string.settings_state_unsaved, WARN);
    }

    private void setSettingsState(int textRes, int color) {
        if (settingsState != null) {
            settingsState.setText(textRes);
            settingsState.setTextColor(color);
        }
    }

    private void confirmReset() {
        new AlertDialog.Builder(this, R.style.GtaAlertDialog)
                .setTitle(R.string.settings_reset_title)
                .setMessage(R.string.settings_reset_message)
                .setNegativeButton(R.string.settings_cancel, (dialog, which) -> MenuSounds.play(MenuSounds.BACK))
                .setPositiveButton(R.string.settings_reset_confirm, (dialog, which) -> {
                    MenuSounds.play(MenuSounds.SELECT);
                    settings.resetToDefaults();
                    buildSettingsUi();
                    updateHome();
                    saveSettings(true);
                })
                .show();
    }

    // ---------------------------------------------------------------------
    // Save / load
    // ---------------------------------------------------------------------

    /**
     * Storage access can be granted after the first layout pass. Reload the
     * real settings.ini then, so the launcher never shows (and later saves)
     * defaults over an existing configuration.
     */
    private void maybeReloadSettings() {
        if (dirty || pendingSave || pendingStart) {
            return;
        }

        settings = GameSettings.load();
        buildSettingsUi();
        updateHome();

        if (GameSettings.fileExists()) {
            setSettingsState(R.string.settings_state_saved, ACCENT);
        } else if (settingsState != null) {
            settingsState.setText("");
        }
    }

    private boolean saveSettings(boolean userInitiated) {
        if (!storagePermissionGranted) {
            if (userInitiated) {
                pendingSave = true;
                Toast.makeText(this, R.string.settings_need_storage, Toast.LENGTH_LONG).show();
                checkAndRequestStoragePermission();
            }
            return false;
        }

        int port;
        try {
            port = Integer.parseInt(inputPort.getText().toString().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("port out of range");
            }
        } catch (Exception e) {
            MenuSounds.play(MenuSounds.BACK);
            Toast.makeText(this, R.string.settings_invalid_port, Toast.LENGTH_LONG).show();
            setSettingsState(R.string.settings_state_failed, DANGER);
            return false;
        }

        String nickname = inputNickname.getText().toString().trim();
        if (nickname.isEmpty()) {
            nickname = GameSettings.generateNickname();
            inputNickname.setText(nickname);
        }
        if (nickname.length() > 24) {
            nickname = nickname.substring(0, 24);
            inputNickname.setText(nickname);
        }

        String host = inputHost.getText().toString().trim();
        if (host.isEmpty()) {
            host = GameSettings.DEFAULT_HOST;
            inputHost.setText(host);
        }

        settings.nickname = nickname;
        settings.host = host;
        settings.port = port;
        settings.password = inputPassword.getText().toString();

        settings.firstPerson = switchFirstPerson.isChecked();
        settings.autoAim = switchAutoAim.isChecked();
        settings.fpsCounter = switchFpsCounter.isChecked();
        settings.cutout = switchCutout.isChecked();
        settings.voiceChat = switchVoiceChat.isChecked();
        settings.androidKeyboard = switchAndroidKeyboard.isChecked();
        settings.dialogs = switchDialogs.isChecked();
        settings.hpArmourText = switchHpArmour.isChecked();
        settings.outfitGuns = switchOutfitGuns.isChecked();
        settings.pcMoney = switchPcMoney.isChecked();
        settings.radarRect = switchRadarRect.isChecked();
        settings.skybox = switchSkybox.isChecked();
        settings.snow = switchSnow.isChecked();

        settings.fpsLimit = 30 + barFps.getProgress() * 6;
        settings.fontSize = 20 + barFont.getProgress() * 2;
        settings.chatLines = 3 + barChat.getProgress();

        try {
            settings.save();
            dirty = false;
            setSettingsState(R.string.settings_state_saved, ACCENT);
            updateHome();
            if (userInitiated) {
                Toast.makeText(this, R.string.settings_saved_toast, Toast.LENGTH_SHORT).show();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save settings: " + e.getMessage(), e);
            setSettingsState(R.string.settings_state_failed, DANGER);
            if (userInitiated) {
                Toast.makeText(this, "Could not write settings.ini: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // Home
    // ---------------------------------------------------------------------

    private void updateHome() {
        if (homeNickname == null) {
            return;
        }

        String nickname = settings.nickname == null ? "" : settings.nickname.trim();
        homeNickname.setText(heroName(nickname));
        homePlayerValue.setText(nickname.isEmpty() ? "PLAYER" : nickname);
        homeServerValue.setText(String.format(Locale.US, "%s:%d", settings.host, settings.port));
        homeClientValue.setText(settings.version == null || settings.version.isEmpty()
                ? GameSettings.DEFAULT_VERSION : settings.version);
        updateCacheStatus();
    }

    private void updateHomePreview() {
        if (homeNickname == null || inputNickname == null) {
            return;
        }

        String nickname = inputNickname.getText().toString().trim();
        homeNickname.setText(heroName(nickname));
        homePlayerValue.setText(nickname.isEmpty() ? "PLAYER" : nickname);

        String host = inputHost.getText().toString().trim();
        if (host.isEmpty()) {
            host = GameSettings.DEFAULT_HOST;
        }
        String port = inputPort.getText().toString().trim();
        if (port.isEmpty()) {
            port = String.valueOf(GameSettings.DEFAULT_PORT);
        }
        homeServerValue.setText(host + ":" + port);
    }

    private void updateCacheStatus() {
        if (homeCacheStatus == null) {
            return;
        }

        boolean ready = new File(GameSettings.GAME_DIR).isDirectory()
                || new File(GameSettings.SAMP_DIR).isDirectory();

        if (ready) {
            homeCacheStatus.setText(R.string.home_cache_ready);
            homeCacheStatus.setTextColor(TEXT_DIM);
            homeCacheHint.setVisibility(View.GONE);
        } else {
            homeCacheStatus.setText(R.string.home_cache_missing);
            homeCacheStatus.setTextColor(WARN);
            homeCacheHint.setVisibility(View.VISIBLE);
        }

        if (homeCacheDot != null) {
            homeCacheDot.setBackgroundTintList(ColorStateList.valueOf(
                    ready ? ACCENT : WARN));
        }
    }

    // ---------------------------------------------------------------------
    // Game launch
    // ---------------------------------------------------------------------

    private void startGameIfReady() {
        if (storagePermissionGranted) {
            startGame();
        } else {
            pendingStart = true;
            Toast.makeText(this, R.string.permission_start_denied, Toast.LENGTH_LONG).show();
            checkAndRequestStoragePermission();
        }
    }

    private void startGame() {
        try {
            // Best effort: persist any pending edits before the game reads
            // /storage/emulated/0/GTA/SAMP/settings.ini. Also create the file
            // when it is missing: the native client terminates without it.
            if (dirty || !GameSettings.fileExists()) {
                saveSettings(false);
            }

            Log.d(TAG, "Starting game");
            Intent gameIntent = new Intent(MainActivity.this, SAMP.class);
            startActivity(gameIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting game: " + e.getMessage(), e);
            Toast.makeText(this, "Could not launch the game: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void loadFonts() {
        tfBold = font(R.font.montserrat_bold);
        tfMedium = font(R.font.montserrat_medium);
        tfRegular = font(R.font.montserrat_regular);
    }

    private Typeface font(int resId) {
        try {
            Typeface tf = ResourcesCompat.getFont(this, resId);
            return tf != null ? tf : Typeface.DEFAULT;
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    /**
     * The window draws edge to edge, so keep the menu clear of the camera
     * cutout / system bars (the nav tabs used to slide under the notch).
     */
    @SuppressWarnings("deprecation")
    private void applyWindowInsets() {
        final View content = findViewById(R.id.launcherContent);
        if (content == null) {
            return;
        }
        final int base = getResources().getDimensionPixelSize(R.dimen.launcher_margin);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    left = Math.max(left, cutout.getSafeInsetLeft());
                    top = Math.max(top, cutout.getSafeInsetTop());
                    right = Math.max(right, cutout.getSafeInsetRight());
                    bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
            }
            v.setPadding(base + left, base + top, base + right, base + bottom);
            return insets;
        });
        content.requestApplyInsets();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Log.e(TAG, "Error opening URL: " + e.getMessage());
            Toast.makeText(this, "No app available to open this link.", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }

    private String heroName(String nickname) {
        if (nickname == null || nickname.isEmpty() || nickname.startsWith("__android_")) {
            return getString(R.string.home_hero_default);
        }
        return nickname;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int nearestMultiple(int value, int min, int step) {
        int steps = Math.round((value - min) / (float) step);
        return min + steps * step;
    }

    public static void hideKeyboard(Activity activity) {
        try {
            InputMethodManager inputManager = (InputMethodManager) activity
                    .getSystemService(Context.INPUT_METHOD_SERVICE);

            View currentFocusedView = activity.getCurrentFocus();
            if (currentFocusedView != null) {
                inputManager.hideSoftInputFromWindow(currentFocusedView.getWindowToken(),
                        InputMethodManager.HIDE_NOT_ALWAYS);
                currentFocusedView.clearFocus();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error hiding keyboard: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            handler.removeCallbacksAndMessages(null);
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy: " + e.getMessage());
        }
        MenuSounds.release();
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}

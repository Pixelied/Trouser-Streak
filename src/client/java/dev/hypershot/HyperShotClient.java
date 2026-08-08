package dev.hypershot;

import com.mojang.blaze3d.platform.InputConstants;
import dev.hypershot.capture.CaptureListenerHub;
import dev.hypershot.capture.CaptureManager;
import dev.hypershot.capture.CaptureRequest;
import dev.hypershot.config.HyperShotConfig;
import dev.hypershot.gallery.CaptureGroupAnnotator;
import dev.hypershot.gallery.CaptureGroupContext;
import dev.hypershot.gallery.CaptureRecord;
import dev.hypershot.gallery.GalleryFileService;
import dev.hypershot.gallery.GalleryIndex;
import dev.hypershot.gallery.ThumbnailTextureCache;
import dev.hypershot.input.F2GestureController;
import dev.hypershot.notification.CaptureNotificationManager;
import dev.hypershot.platform.PlatformIntegration;
import dev.hypershot.shot.CameraLockController;
import dev.hypershot.shot.CinematicSceneController;
import dev.hypershot.shot.ShotCoordinator;
import dev.hypershot.shot.TimeSceneController;
import dev.hypershot.ui.CameraControlScreen;
import dev.hypershot.ui.CameraViewfinderOverlay;
import dev.hypershot.ui.CaptureGroupScreen;
import dev.hypershot.ui.GalleryScreen;
import dev.hypershot.ui.ImageViewerScreen;
import dev.hypershot.ui.QuickCaptureScreen;
import dev.hypershot.ui.SettingsScreen;
import dev.hypershot.util.HyperShotPaths;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HyperShotClient implements ClientModInitializer, AutoCloseable {
    public static final String MOD_ID = "hypershot";
    private static final Logger LOGGER = LoggerFactory.getLogger("HyperShot");
    private static HyperShotClient instance;

    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "HyperShot Background I/O");
        thread.setDaemon(true);
        return thread;
    });
    private HyperShotPaths paths;
    private HyperShotConfig config;
    private CaptureManager captureManager;
    private CaptureListenerHub listenerHub;
    private CaptureGroupContext captureGroupContext;
    private CaptureGroupAnnotator captureGroupAnnotator;
    private TimeSceneController timeSceneController;
    private CinematicSceneController cinematicSceneController;
    private CameraLockController cameraLockController;
    private ShotCoordinator shotCoordinator;
    private F2GestureController f2GestureController;
    private CameraViewfinderOverlay cameraViewfinderOverlay;
    private boolean cameraViewfinderOpen;
    private GalleryIndex galleryIndex;
    private GalleryFileService galleryFiles;
    private ThumbnailTextureCache thumbnailTextures;
    private CaptureNotificationManager notifications;
    private PlatformIntegration platform;
    private KeyMapping captureKey;
    private KeyMapping galleryKey;
    private KeyMapping quickPanelKey;
    private KeyMapping cancelKey;

    @Override
    public void onInitializeClient() {
        if (instance != null) throw new IllegalStateException("HyperShot initialized twice");
        instance = this;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            paths = HyperShotPaths.create(FabricLoader.getInstance().getGameDir());
        } catch (IOException error) {
            throw new IllegalStateException("Unable to create HyperShot data directories", error);
        }
        config = HyperShotConfig.load(paths.config(), LOGGER);
        platform = new PlatformIntegration();
        galleryIndex = new GalleryIndex(paths.index(), LOGGER);
        galleryFiles = new GalleryFileService(paths);
        thumbnailTextures = new ThumbnailTextureCache(minecraft, LOGGER, ioExecutor, 128);
        captureManager = new CaptureManager(LOGGER, paths, config.freeDiskMarginBytes, config.metadataPrivacy);
        listenerHub = new CaptureListenerHub(LOGGER);
        captureManager.setListener(listenerHub);

        captureGroupContext = new CaptureGroupContext();
        captureGroupAnnotator = new CaptureGroupAnnotator(captureGroupContext, galleryIndex);
        timeSceneController = new TimeSceneController();
        cinematicSceneController = new CinematicSceneController(timeSceneController);
        cameraLockController = new CameraLockController();
        notifications = new CaptureNotificationManager(minecraft, config, galleryIndex, thumbnailTextures, platform);
        shotCoordinator = new ShotCoordinator(captureManager, config, captureGroupContext, captureGroupAnnotator,
                timeSceneController, cinematicSceneController, cameraLockController);

        listenerHub.add(notifications);
        listenerHub.add(captureGroupAnnotator);
        listenerHub.add(shotCoordinator);

        f2GestureController = new F2GestureController(config, HyperShotClient::handleF2Tap,
                HyperShotClient::openCameraViewfinder, HyperShotClient::toggleCameraViewfinder);
        cameraViewfinderOverlay = new CameraViewfinderOverlay(minecraft);

        HudElementRegistry.addLast(id("camera_viewfinder"), (graphics, deltaTracker) -> cameraViewfinderOverlay.extractRenderState(graphics));
        HudElementRegistry.addLast(id("capture_notifications"), (graphics, deltaTracker) -> notifications.extractRenderState(graphics));
        registerKeyMappings();
        registerMenuButtons();
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
        galleryIndex.reconcileAsync(paths.captures(), ioExecutor, ignored -> {});
        LOGGER.info("HyperShot initialized with {} presets; output directory {}", config.presets.size(), paths.captures());
    }

    private void registerKeyMappings() {
        KeyMapping.Category category = KeyMapping.Category.register(id("capture"));
        captureKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hypershot.capture", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, category));
        galleryKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hypershot.gallery", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, category));
        quickPanelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hypershot.quick_panel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, category));
        cancelKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hypershot.cancel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, category));
    }

    private void registerMenuButtons() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!config.showMenuButton) return;
            if (screen instanceof TitleScreen || screen instanceof PauseScreen) {
                Screens.getWidgets(screen).add(Button.builder(Component.translatable("menu.hypershot.open"), button ->
                                client.gui.setScreen(new QuickCaptureScreen(screen)))
                        .bounds(Math.max(8, scaledWidth - 108), Math.max(8, scaledHeight - 28), 100, 20).build());
            }
        });
    }

    private void onEndTick(Minecraft client) {
        long nowNanos = System.nanoTime();
        if (client.level == null) {
            if (shotCoordinator.hasQueuedShot()) shotCoordinator.cancel("World closed");
            cameraLockController.unlock();
            cameraViewfinderOpen = false;
            f2GestureController.reset();
        } else {
            f2GestureController.tick(client, nowNanos);
            cameraLockController.tick(client);
        }
        shotCoordinator.tick(client, nowNanos);
        while (captureKey.consumeClick()) captureActivePreset();
        while (galleryKey.consumeClick()) openGallery(client.gui.screen());
        while (quickPanelKey.consumeClick()) {
            if (cameraViewfinderOpen) openCameraControls();
            else openQuickCapture();
        }
        while (cancelKey.consumeClick()) {
            cameraLockController.unlock();
            shotCoordinator.cancel("Emergency cancel key pressed");
            captureManager.cancel("Emergency cancel key pressed");
        }
    }

    public static void captureActivePreset() {
        HyperShotClient self = get();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            reportUiError("Capture unavailable", new IllegalStateException("Enter a world before taking a HyperShot capture"));
            return;
        }
        if (self.captureManager.isActive() || self.shotCoordinator.hasQueuedShot()) {
            reportUiError("Capture already active", new IllegalStateException("Cancel or finish the current capture first"));
            return;
        }
        var target = minecraft.gameRenderer.mainRenderTarget();
        CaptureRequest request = CaptureRequest.from(self.config.activePreset(), target.width, target.height);
        self.captureManager.start(minecraft, request);
    }

    private static void handleF2Tap() {
        if (isCameraViewfinderOpen()) queueCameraShot();
        else queueCameraPhoto();
    }

    public static void queueCameraPhoto() {
        get().shotCoordinator.queuePhoto(Minecraft.getInstance());
    }

    public static void queueCameraShot() {
        get().shotCoordinator.queueActiveMode(Minecraft.getInstance());
    }

    public static void onVanillaScreenshotKeyPressed() {
        get().f2GestureController.onScreenshotKeyPressed(System.nanoTime());
    }

    public static void openCameraViewfinder() {
        HyperShotClient self = get();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            reportUiError("Camera unavailable", new IllegalStateException("Enter a world before opening the HyperShot camera"));
            return;
        }
        self.cameraViewfinderOpen = true;
    }

    public static void closeCameraViewfinder() {
        HyperShotClient self = get();
        if (!self.captureManager.isActive() && self.shotCoordinator.hasQueuedShot()) self.shotCoordinator.cancel("Camera viewfinder closed");
        self.cameraLockController.unlock();
        self.cameraViewfinderOpen = false;
    }

    public static void toggleCameraViewfinder() {
        if (isCameraViewfinderOpen()) closeCameraViewfinder();
        else openCameraViewfinder();
    }

    public static boolean isCameraViewfinderOpen() {
        return instance != null && instance.cameraViewfinderOpen;
    }

    public static void openCameraControls() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isCameraViewfinderOpen()) openCameraViewfinder();
        if (isCameraViewfinderOpen()) minecraft.gui.setScreen(new CameraControlScreen());
    }

    public static void openQuickCapture() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setScreen(new QuickCaptureScreen(minecraft.gui.screen()));
    }

    public static void openSettings(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new SettingsScreen(parent));
    }

    public static void openGallery(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new GalleryScreen(parent));
    }

    public static void openCaptureGroup(String groupId) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setScreen(new CaptureGroupScreen(minecraft.gui.screen(), groupId));
    }

    public static void openViewer(CaptureRecord record) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setScreen(new ImageViewerScreen(minecraft.gui.screen(), record));
    }

    public static void saveConfig() {
        HyperShotClient self = get();
        self.config.save(self.paths.config(), LOGGER);
        self.captureManager.configure(self.config.freeDiskMarginBytes, self.config.metadataPrivacy);
    }

    public static void reportUiError(String title, Throwable error) {
        LOGGER.error(title, error);
        HyperShotClient self = instance;
        if (self != null && self.paths != null) {
            try {
                Files.writeString(self.paths.logs().resolve("recent-errors.log"), OffsetDateTime.now() + " " + title + ": " + error + System.lineSeparator(),
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException ignored) {}
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("HyperShot: " + title + " — " + error.getMessage()));
    }

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }
    public static boolean isInitialized() { return instance != null && instance.captureManager != null; }
    public static HyperShotClient get() { return Objects.requireNonNull(instance, "HyperShot has not initialized"); }
    public static CaptureManager captureManager() { return get().captureManager; }
    public static CaptureListenerHub listenerHub() { return get().listenerHub; }
    public static ShotCoordinator shotCoordinator() { return get().shotCoordinator; }
    public static CameraLockController cameraLockController() { return get().cameraLockController; }
    public static HyperShotConfig config() { return get().config; }
    public static HyperShotPaths paths() { return get().paths; }
    public static GalleryIndex galleryIndex() { return get().galleryIndex; }
    public static GalleryFileService galleryFiles() { return get().galleryFiles; }
    public static ThumbnailTextureCache thumbnailTextures() { return get().thumbnailTextures; }
    public static CaptureNotificationManager notifications() { return get().notifications; }
    public static PlatformIntegration platform() { return get().platform; }

    @Override
    public void close() {
        if (shotCoordinator != null && shotCoordinator.hasQueuedShot()) shotCoordinator.cancel("Client stopping");
        if (cameraLockController != null) cameraLockController.unlock();
        if (cinematicSceneController != null && cinematicSceneController.active()) cinematicSceneController.restore();
        if (timeSceneController != null && timeSceneController.active()) timeSceneController.restore();
        if (captureGroupContext != null) captureGroupContext.clear();
        if (f2GestureController != null) f2GestureController.reset();
        cameraViewfinderOpen = false;
        if (thumbnailTextures != null) thumbnailTextures.close();
        if (captureManager != null) captureManager.close();
        ioExecutor.shutdown();
        LOGGER.info("HyperShot stopped");
    }
}

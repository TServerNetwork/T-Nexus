package network.tserver.tnexus.gui;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.config.MessageConfig;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Opens and validates anvil-based text input flows.
 */
public final class AnvilGuiManager {

    private final TNexus plugin;
    private final MessageConfig messageConfig;
    private final BuilderFactory builderFactory;

    /**
     * Creates a new anvil GUI manager.
     *
     * @param plugin plugin instance
     */
    public AnvilGuiManager(TNexus plugin) {
        this(plugin, AnvilBuilderAdapter::new);
    }

    AnvilGuiManager(TNexus plugin, BuilderFactory builderFactory) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.messageConfig = plugin.getMessageConfig();
        this.builderFactory = Objects.requireNonNull(builderFactory, "builderFactory");
    }

    /**
     * Opens a generic string input prompt.
     *
     * @param player target player
     * @param title anvil title
     * @param placeholder initial input text
     * @param onComplete completion callback
     */
    public void openInput(Player player, String title, String placeholder, Consumer<String> onComplete) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(onComplete, "onComplete");

        AtomicBoolean completed = new AtomicBoolean(false);
        this.builderFactory.create()
                .plugin(this.plugin)
                .title(colorize(title))
                .text(placeholder == null ? "" : placeholder)
                .onClose(() -> handleCancellation(player, completed))
                .onOutputClick(text -> createTextCompletionActions(text, completed, onComplete))
                .open(player);
    }

    /**
     * Opens a numeric input prompt with validation.
     *
     * @param player target player
     * @param title anvil title
     * @param onComplete completion callback
     */
    public void openNumberInput(Player player, String title, Consumer<Double> onComplete) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(onComplete, "onComplete");

        AtomicBoolean completed = new AtomicBoolean(false);
        String placeholder = this.messageConfig.getMessage("gui.anvil.number.placeholder");
        this.builderFactory.create()
                .plugin(this.plugin)
                .title(colorize(title))
                .text(placeholder)
                .onClose(() -> handleCancellation(player, completed))
                .onOutputClick(text -> createNumberCompletionActions(player, text, placeholder, completed, onComplete))
                .open(player);
    }

    private void handleCancellation(Player player, AtomicBoolean completed) {
        if (!completed.get() && player.isOnline()) {
            this.messageConfig.sendMessage(player, "gui.anvil.cancelled");
        }
    }

    private List<BuilderAction> createTextCompletionActions(
            String text,
            AtomicBoolean completed,
            Consumer<String> onComplete) {
        completed.set(true);
        String value = text == null ? "" : text;
        return List.of(
                BuilderAction.close(),
                BuilderAction.run(() -> onComplete.accept(value)));
    }

    private List<BuilderAction> createNumberCompletionActions(
            Player player,
            String text,
            String placeholder,
            AtomicBoolean completed,
            Consumer<Double> onComplete) {
        String normalized = text == null ? "" : text.trim();
        try {
            double value = Double.parseDouble(normalized);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("Non-finite value");
            }

            completed.set(true);
            return List.of(
                    BuilderAction.close(),
                    BuilderAction.run(() -> onComplete.accept(value)));
        } catch (NumberFormatException exception) {
            return List.of(
                    BuilderAction.run(() -> this.messageConfig.sendMessage(player, "gui.anvil.number.invalid")),
                    BuilderAction.replaceInputText(placeholder));
        }
    }

    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    interface BuilderFactory {

        BuilderAdapter create();
    }

    interface BuilderAdapter {

        BuilderAdapter plugin(TNexus plugin);

        BuilderAdapter title(String title);

        BuilderAdapter text(String text);

        BuilderAdapter onClose(Runnable onClose);

        BuilderAdapter onOutputClick(Function<String, List<BuilderAction>> onOutputClick);

        void open(Player player);
    }

    enum BuilderActionType {
        CLOSE,
        REPLACE_INPUT_TEXT,
        RUN
    }

    static final class BuilderAction {

        private final BuilderActionType type;
        private final String replacementText;
        private final Runnable runnable;

        private BuilderAction(BuilderActionType type, String replacementText, Runnable runnable) {
            this.type = type;
            this.replacementText = replacementText;
            this.runnable = runnable;
        }

        static BuilderAction close() {
            return new BuilderAction(BuilderActionType.CLOSE, null, null);
        }

        static BuilderAction replaceInputText(String replacementText) {
            return new BuilderAction(BuilderActionType.REPLACE_INPUT_TEXT, replacementText, null);
        }

        static BuilderAction run(Runnable runnable) {
            return new BuilderAction(BuilderActionType.RUN, null, runnable);
        }

        BuilderActionType type() {
            return this.type;
        }

        String replacementText() {
            return this.replacementText;
        }

        Runnable runnable() {
            return this.runnable;
        }
    }

    private static final class AnvilBuilderAdapter implements BuilderAdapter {

        private final AnvilGUI.Builder builder;

        private AnvilBuilderAdapter() {
            this.builder = new AnvilGUI.Builder();
        }

        @Override
        public BuilderAdapter plugin(TNexus plugin) {
            this.builder.plugin(plugin);
            return this;
        }

        @Override
        public BuilderAdapter title(String title) {
            this.builder.title(title);
            return this;
        }

        @Override
        public BuilderAdapter text(String text) {
            this.builder.text(text);
            return this;
        }

        @Override
        public BuilderAdapter onClose(Runnable onClose) {
            this.builder.onClose(stateSnapshot -> onClose.run());
            return this;
        }

        @Override
        public BuilderAdapter onOutputClick(Function<String, List<BuilderAction>> onOutputClick) {
            this.builder.onClick((slot, stateSnapshot) -> {
                if (slot != AnvilGUI.Slot.OUTPUT) {
                    return Collections.emptyList();
                }
                return onOutputClick.apply(stateSnapshot.getText()).stream()
                        .map(AnvilBuilderAdapter::toResponseAction)
                        .toList();
            });
            return this;
        }

        @Override
        public void open(Player player) {
            this.builder.open(player);
        }

        private static AnvilGUI.ResponseAction toResponseAction(BuilderAction action) {
            return switch (action.type()) {
                case CLOSE -> AnvilGUI.ResponseAction.close();
                case REPLACE_INPUT_TEXT -> AnvilGUI.ResponseAction.replaceInputText(action.replacementText());
                case RUN -> AnvilGUI.ResponseAction.run(action.runnable());
            };
        }
    }
}

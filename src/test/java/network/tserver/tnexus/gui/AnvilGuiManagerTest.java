package network.tserver.tnexus.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import network.tserver.tnexus.TNexus;
import network.tserver.tnexus.TestPluginSupport;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnvilGuiManagerTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldOpenTextInputAndInvokeCompletionCallback() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();
        FakeBuilderFactory builderFactory = new FakeBuilderFactory();
        AnvilGuiManager manager = new AnvilGuiManager(plugin, builderFactory);
        AtomicReference<String> completedValue = new AtomicReference<>();

        manager.openInput(player, "&6Input", "placeholder", completedValue::set);

        assertEquals("§6Input", builderFactory.builder.title);
        assertEquals("placeholder", builderFactory.builder.text);
        assertEquals(player, builderFactory.builder.openedPlayer);

        runActions(builderFactory.builder.outputClickHandler.apply("hello"));

        assertEquals("hello", completedValue.get());
        assertNull(player.nextMessage());
    }

    @Test
    void shouldRejectInvalidNumberInputAndKeepSessionOpen() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();
        FakeBuilderFactory builderFactory = new FakeBuilderFactory();
        AnvilGuiManager manager = new AnvilGuiManager(plugin, builderFactory);
        AtomicReference<Double> completedValue = new AtomicReference<>();

        manager.openNumberInput(player, "&6Amount", completedValue::set);

        List<AnvilGuiManager.BuilderAction> actions = builderFactory.builder.outputClickHandler.apply("abc");
        runActions(actions);

        assertNull(completedValue.get());
        assertEquals("§8[§6T-Nexus§8] §cEnter a valid number.", player.nextMessage());
        assertEquals(AnvilGuiManager.BuilderActionType.RUN, actions.get(0).type());
        assertEquals(AnvilGuiManager.BuilderActionType.REPLACE_INPUT_TEXT, actions.get(1).type());
        assertEquals("0", actions.get(1).replacementText());
    }

    @Test
    void shouldHandleCancellationWithoutInvokingCompletion() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();
        FakeBuilderFactory builderFactory = new FakeBuilderFactory();
        AnvilGuiManager manager = new AnvilGuiManager(plugin, builderFactory);
        AtomicReference<Double> completedValue = new AtomicReference<>();

        manager.openNumberInput(player, "&6Amount", completedValue::set);
        builderFactory.builder.onClose.run();

        assertNull(completedValue.get());
        assertEquals("§8[§6T-Nexus§8] §7Input cancelled.", player.nextMessage());
    }

    @Test
    void shouldCompleteValidNumberInput() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        TNexus plugin = TestPluginSupport.loadPlugin(this.server, TestPluginSupport.TestTNexus.class);
        PlayerMock player = this.server.addPlayer();
        FakeBuilderFactory builderFactory = new FakeBuilderFactory();
        AnvilGuiManager manager = new AnvilGuiManager(plugin, builderFactory);
        AtomicReference<Double> completedValue = new AtomicReference<>();

        manager.openNumberInput(player, "&6Amount", completedValue::set);

        List<AnvilGuiManager.BuilderAction> actions = builderFactory.builder.outputClickHandler.apply("12.5");
        runActions(actions);
        builderFactory.builder.onClose.run();

        assertEquals(12.5D, completedValue.get());
        assertEquals(List.of(
                AnvilGuiManager.BuilderActionType.CLOSE,
                AnvilGuiManager.BuilderActionType.RUN), actionTypes(actions));
        assertNull(player.nextMessage());
    }

    private void runActions(List<AnvilGuiManager.BuilderAction> actions) {
        for (AnvilGuiManager.BuilderAction action : actions) {
            if (action.type() == AnvilGuiManager.BuilderActionType.RUN) {
                assertNotNull(action.runnable());
                action.runnable().run();
            }
        }
    }

    private List<AnvilGuiManager.BuilderActionType> actionTypes(List<AnvilGuiManager.BuilderAction> actions) {
        List<AnvilGuiManager.BuilderActionType> types = new ArrayList<>();
        for (AnvilGuiManager.BuilderAction action : actions) {
            types.add(action.type());
        }
        return types;
    }

    private static final class FakeBuilderFactory implements AnvilGuiManager.BuilderFactory {

        private final FakeBuilder builder = new FakeBuilder();

        @Override
        public AnvilGuiManager.BuilderAdapter create() {
            return this.builder;
        }
    }

    private static final class FakeBuilder implements AnvilGuiManager.BuilderAdapter {

        private String title;
        private String text;
        private Runnable onClose;
        private Function<String, List<AnvilGuiManager.BuilderAction>> outputClickHandler;
        private Player openedPlayer;

        @Override
        public AnvilGuiManager.BuilderAdapter plugin(TNexus plugin) {
            return this;
        }

        @Override
        public AnvilGuiManager.BuilderAdapter title(String title) {
            this.title = title;
            return this;
        }

        @Override
        public AnvilGuiManager.BuilderAdapter text(String text) {
            this.text = text;
            return this;
        }

        @Override
        public AnvilGuiManager.BuilderAdapter onClose(Runnable onClose) {
            this.onClose = onClose;
            return this;
        }

        @Override
        public AnvilGuiManager.BuilderAdapter onOutputClick(
                Function<String, List<AnvilGuiManager.BuilderAction>> onOutputClick) {
            this.outputClickHandler = onOutputClick;
            return this;
        }

        @Override
        public void open(Player player) {
            this.openedPlayer = player;
            assertNotNull(this.onClose);
            assertNotNull(this.outputClickHandler);
        }
    }
}

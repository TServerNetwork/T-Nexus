package network.tserver.tnexus.util;

import java.util.List;
import network.tserver.tnexus.TestPluginSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class TabCompleterUtilTest {

    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void shouldFilterCandidatesByPrefix() {
        assertIterableEquals(
                List.of("add"),
                TabCompleterUtil.filter(List.of("add", "set", "take"), "a"));
        assertIterableEquals(
                List.of(),
                TabCompleterUtil.filter(List.of("add", "set", "take"), "z"));
    }

    @Test
    void shouldFilterOnlinePlayersByPrefix() {
        this.server = TestPluginSupport.mockServerWithRequiredPlugins();
        this.server.addPlayer("Alice");
        this.server.addPlayer("Bob");

        assertIterableEquals(List.of("Alice"), TabCompleterUtil.filterPlayers("Al"));
        assertIterableEquals(List.of(), TabCompleterUtil.filterPlayers("Zo"));
    }
}

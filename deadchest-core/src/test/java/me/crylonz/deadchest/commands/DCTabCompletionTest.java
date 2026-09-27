package me.crylonz.deadchest.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.crylonz.deadchest.Permission;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DCTabCompletionTest {

    private ServerMock server;
    private DCTabCompletion tabCompletion;
    private Command dcCommand;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        tabCompletion = new DCTabCompletion();
        dcCommand = mock(Command.class);
        when(dcCommand.getName()).thenReturn("dc");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void adminGetsMainSuggestions() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.ADMIN.label, true);

        List<String> suggestions = tabCompletion.onTabComplete(admin, dcCommand, "dc", new String[]{""});

        assertTrue(suggestions.contains("reload"));
        assertTrue(suggestions.contains("removeinfinite"));
        assertTrue(suggestions.contains("removeall"));
        assertTrue(suggestions.contains("repair"));
        assertTrue(suggestions.contains("ignore"));
        assertTrue(suggestions.contains("config"));
    }

    @Test
    void removeSecondArgSuggestsOnlinePlayersForAuthorizedUser() {
        PlayerMock moderator = server.addPlayer("Mod");
        moderator.addAttachment(MockBukkit.createMockPlugin(), Permission.REMOVE_OTHER.label, true);
        server.addPlayer("Steve");
        server.addPlayer("Alex");

        List<String> suggestions = tabCompletion.onTabComplete(moderator, dcCommand, "dc", new String[]{"remove", ""});

        assertTrue(suggestions.contains("Steve"));
        assertTrue(suggestions.contains("Alex"));
    }

    @Test
    void listSecondArgContainsAllAndPlayerNames() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.ADMIN.label, true);
        server.addPlayer("Steve");

        List<String> suggestions = tabCompletion.onTabComplete(admin, dcCommand, "dc", new String[]{"list", ""});

        assertTrue(suggestions.contains("all"));
        assertTrue(suggestions.contains("Steve"));
    }

    @Test
    void configActionSuggestionsExposeStructuredCommands() {
        PlayerMock admin = server.addPlayer("ConfigMod");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.CONFIG.label, true);

        List<String> suggestions = tabCompletion.onTabComplete(admin, dcCommand, "dc", new String[]{"config", ""});

        assertTrue(suggestions.contains("get"));
        assertTrue(suggestions.contains("set"));
        assertTrue(suggestions.contains("reset"));
        assertTrue(suggestions.contains("edit"));
    }

    @Test
    void configValueSuggestionsFollowConfigKeyMetadata() {
        PlayerMock admin = server.addPlayer("ConfigMod");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.CONFIG.label, true);

        List<String> suggestions = tabCompletion.onTabComplete(
                admin,
                dcCommand,
                "dc",
                new String[]{"config", "set", "localization.language", ""}
        );

        assertTrue(suggestions.contains("en"));
        assertTrue(suggestions.contains("fr"));
    }

    @Test
    void configEditOnlySuggestsInteractiveKeys() {
        PlayerMock admin = server.addPlayer("ConfigMod");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.CONFIG.label, true);

        List<String> suggestions = tabCompletion.onTabComplete(
                admin,
                dcCommand,
                "dc",
                new String[]{"config", "edit", ""}
        );

        assertEquals(List.of("filters.ignored-items"), suggestions);
        assertFalse(suggestions.contains("localization.language"));
    }

    @Test
    void giveBackSuggestionsExposeSelectorsAndPlayers() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.GIVEBACK.label, true);
        server.addPlayer("Steve");

        List<String> suggestions = tabCompletion.onTabComplete(admin, dcCommand, "dc", new String[]{"giveback", ""});

        assertTrue(suggestions.contains("list"));
        assertTrue(suggestions.contains("latest"));
        assertTrue(suggestions.contains("oldest"));
        assertTrue(suggestions.contains("all"));
        assertTrue(suggestions.contains("id"));
        assertTrue(suggestions.contains("Steve"));
    }

    @Test
    void giveBackTargetSelectorsAreSuggestedAfterPlayerName() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.GIVEBACK.label, true);

        List<String> suggestions = tabCompletion.onTabComplete(admin, dcCommand, "dc", new String[]{"giveback", "Steve", ""});

        assertTrue(suggestions.contains("latest"));
        assertTrue(suggestions.contains("oldest"));
        assertTrue(suggestions.contains("all"));
        assertTrue(suggestions.contains("id"));
    }

    @Test
    void giveBackPreviewSuggestionsExposePlayersAndSelectors() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(MockBukkit.createMockPlugin(), Permission.GIVEBACK.label, true);
        server.addPlayer("Steve");

        List<String> suggestions = tabCompletion.onTabComplete(admin, dcCommand, "dc", new String[]{"giveback", "preview", ""});

        assertTrue(suggestions.contains("list"));
        assertTrue(suggestions.contains("Steve"));
    }
}

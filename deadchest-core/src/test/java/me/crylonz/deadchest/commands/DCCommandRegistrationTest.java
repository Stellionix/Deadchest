package me.crylonz.deadchest.commands;

import me.crylonz.deadchest.DeadChestLoader;
import me.crylonz.deadchest.Localization;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class DCCommandRegistrationTest {

    private TestRegistration registration;

    @BeforeEach
    void setUp() {
        Localization localization = new Localization();
        Map<String, Object> values = new HashMap<>();
        values.put("common.prefix", "[DeadChest] ");
        values.put("commands.error.no-permission", "You need permission");
        values.put("commands.error.bad-args", "Bad argument(s) for /dc {0}");
        localization.set(values);
        DeadChestLoader.local = localization;
        registration = new TestRegistration();
    }

    @Test
    void registerCommandRunsRunnableOnExactMatch() {
        Player sender = mock(Player.class);
        when(sender.hasPermission("deadchest.admin")).thenReturn(true);

        AtomicBoolean ran = new AtomicBoolean(false);
        registration.register(sender, new String[]{"reload"});
        registration.registerCommand("dc reload", "deadchest.admin", () -> ran.set(true));

        assertTrue(ran.get());
        assertTrue(registration.isCommandSucceed());
    }

    @Test
    void registerCommandRejectsWhenPermissionMissing() {
        Player sender = mock(Player.class);
        when(sender.hasPermission("deadchest.admin")).thenReturn(false);

        AtomicBoolean ran = new AtomicBoolean(false);
        registration.register(sender, new String[]{"reload"});
        registration.registerCommand("dc reload", "deadchest.admin", () -> ran.set(true));

        assertFalse(ran.get());
        assertFalse(registration.isCommandSucceed());
    }

    @Test
    void registerCommandReportsBadArgumentsForMissingPlaceholder() {
        CommandSender sender = mock(CommandSender.class);

        registration.register(sender, new String[]{"remove"});
        registration.registerCommand("dc remove {0}", "deadchest.remove.other", () -> {
        });

        assertFalse(registration.isCommandSucceed());
        assertTrue(registration.hasBadArguments());
        registration.sendBadArgumentsFeedback();
        verify(sender, times(1)).sendMessage(contains("Bad argument(s) for /dc remove"));
    }

    @Test
    void registerCommandDoesNotReportBadArgumentsForDifferentSubcommand() {
        CommandSender sender = mock(CommandSender.class);

        registration.register(sender, new String[]{"config", "edit", "filters.ignored-items"});
        registration.registerCommand("dc config set {1} {2}", null, () -> {
        });

        assertFalse(registration.isCommandSucceed());
        assertFalse(registration.hasBadArguments());
        verify(sender, never()).sendMessage(contains("Bad argument(s) for /dc config"));
    }

    private static final class TestRegistration extends DCCommandRegistration {
        TestRegistration() {
            super(mock(DeadChestLoader.class));
        }
    }
}

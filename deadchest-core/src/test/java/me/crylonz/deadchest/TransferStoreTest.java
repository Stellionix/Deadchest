package me.crylonz.deadchest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TransferStoreTest {
    @TempDir Path directory;
    Properties record() {
        Properties p=new Properties();p.setProperty("id",UUID.randomUUID().toString());p.setProperty("chest",UUID.randomUUID().toString());
        p.setProperty("recipient",UUID.randomUUID().toString());p.setProperty("kind","RECOVER");p.setProperty("items","snapshot");return p;
    }
    @Test void pendingIntentSurvivesRestartAndAcknowledgementIsIdempotent() {
        Path db=directory.resolve("data.db");TransferStore s=new TransferStore(db);Properties p=record();s.insert(p);
        TransferStore reopened=new TransferStore(db);assertEquals(Collections.singletonList(p),reopened.pending());
        reopened.complete(p.getProperty("id"));reopened.complete(p.getProperty("id"));assertTrue(new TransferStore(db).pending().isEmpty());
    }
    @Test void twoClaimantsCannotPrepareSameChestOrOverwriteItsSnapshot() {
        TransferStore s=new TransferStore(directory.resolve("data.db"));Properties first=record();s.insert(first);
        Properties second=record();second.setProperty("chest",first.getProperty("chest"));
        assertThrows(IllegalStateException.class,()->s.insert(second));assertEquals(Collections.singletonList(first),s.pending());
    }
}

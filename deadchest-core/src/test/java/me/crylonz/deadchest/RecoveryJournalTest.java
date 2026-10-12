package me.crylonz.deadchest;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryJournalTest {
    @TempDir Path directory;
    @Test void interruptedOperationIsRetainedAndBlockedOnRestart() throws Exception {
        UUID id=UUID.randomUUID(); Properties snapshot=new Properties();
        snapshot.setProperty("items","saved-items"); snapshot.setProperty("xp","100");
        RecoveryJournal first=new RecoveryJournal(directory);
        first.write(id,snapshot);
        assertFalse(first.requiresReview(id));
        RecoveryJournal restarted=new RecoveryJournal(directory);
        assertTrue(restarted.requiresReview(id));
        assertThrows(IllegalStateException.class,()->restarted.complete(id));
        Properties recovered=new Properties();
        try(java.io.InputStream in=Files.newInputStream(directory.resolve(id+".properties"))){recovered.load(in);}
        assertEquals(snapshot,recovered);
    }
    @Test void acknowledgedOperationIsNotReplayedOrBlocked() {
        UUID id=UUID.randomUUID(); RecoveryJournal journal=new RecoveryJournal(directory);
        journal.write(id,new Properties()); journal.complete(id);
        assertFalse(new RecoveryJournal(directory).requiresReview(id));
        assertFalse(journal.pending(id));
    }
    @Test void unresolvedSnapshotCannotBeOverwritten() throws Exception {
        UUID id=UUID.randomUUID(); RecoveryJournal journal=new RecoveryJournal(directory);
        Properties data=new Properties();data.setProperty("items","original");journal.write(id,data);
        byte[] before=Files.readAllBytes(directory.resolve(id+".properties"));
        assertThrows(IllegalStateException.class,()->journal.write(id,new Properties()));
        assertArrayEquals(before,Files.readAllBytes(directory.resolve(id+".properties")));
    }
    @Test void unfinishedTemporaryWriteDoesNotPublishAnOperation() throws Exception {
        UUID id=UUID.randomUUID();Files.write(directory.resolve(id+".tmp"),new byte[]{1});
        assertFalse(new RecoveryJournal(directory).requiresReview(id));
    }
    @Test void writeFailureDoesNotAcknowledgeIntent() throws Exception {
        Path file=directory.resolve("not-a-directory");Files.write(file,new byte[]{1});
        RecoveryJournal journal=new RecoveryJournal(directory.resolve("absent"));
        Files.write(directory.resolve("absent"),new byte[]{1});
        assertThrows(IllegalStateException.class,()->journal.write(UUID.randomUUID(),new Properties()));
    }
}

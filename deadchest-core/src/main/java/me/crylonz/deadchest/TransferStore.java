package me.crylonz.deadchest;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Durable transfer intents. Each call owns its connection; never shares the core JDBC connection. */
public final class TransferStore {
    private final String url;

    public TransferStore(Path file) {
        try { Files.createDirectories(file.toAbsolutePath().getParent()); }
        catch (IOException e) { throw new IllegalStateException(e); }
        url = "jdbc:sqlite:" + file.toAbsolutePath();
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS player_transfers (id TEXT PRIMARY KEY, chest TEXT NOT NULL UNIQUE, recipient TEXT NOT NULL, kind TEXT NOT NULL, payload BLOB NOT NULL)");
        } catch (SQLException e) { throw new IllegalStateException("Cannot initialize player transfers", e); }
    }

    private Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA busy_timeout=10000");
            s.execute("PRAGMA synchronous=FULL");
        }
        return c;
    }

    public synchronized void insert(Properties data) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try (PreparedStatement s = c.prepareStatement("INSERT INTO player_transfers VALUES(?,?,?,?,?)")) {
                s.setString(1, data.getProperty("id")); s.setString(2, data.getProperty("chest"));
                s.setString(3, data.getProperty("recipient")); s.setString(4, data.getProperty("kind"));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); data.store(bytes, "DeadChest transfer v1");
                s.setBytes(5, bytes.toByteArray()); s.executeUpdate(); c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        } catch (Exception e) { throw new IllegalStateException("Cannot prepare player transfer", e); }
    }

    public synchronized List<Properties> pending() {
        List<Properties> result = new ArrayList<>();
        try (Connection c = open(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT payload FROM player_transfers ORDER BY rowid")) {
            while (r.next()) {
                Properties p = new Properties(); p.load(new ByteArrayInputStream(r.getBytes(1))); result.add(p);
            }
        } catch (Exception e) { throw new IllegalStateException("Cannot read player transfers", e); }
        return result;
    }

    public synchronized void complete(String id) {
        try (Connection c = open(); PreparedStatement s = c.prepareStatement("DELETE FROM player_transfers WHERE id=?")) {
            s.setString(1, id); s.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("Cannot acknowledge player transfer", e); }
    }
}

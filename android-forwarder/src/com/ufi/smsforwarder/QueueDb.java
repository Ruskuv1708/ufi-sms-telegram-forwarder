package com.ufi.smsforwarder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.Charset;
import java.security.MessageDigest;

final class QueueDb extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "forward_queue.db";
    private static final int DATABASE_VERSION = 2;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final long DUPLICATE_WINDOW_MS = 60L * 1000L;
    private static final long SEEN_RETENTION_MS = 7L * 24L * 60L * 60L * 1000L;

    QueueDb(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE queue (" +
                        "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "fingerprint TEXT NOT NULL UNIQUE," +
                        "sender TEXT NOT NULL," +
                        "received_at INTEGER NOT NULL," +
                        "body TEXT NOT NULL," +
                        "attempts INTEGER NOT NULL DEFAULT 0," +
                        "last_error TEXT" +
                        ")"
        );
        createSeenTable(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createSeenTable(database);
        }
    }

    boolean enqueue(String sender, long receivedAt, String body) {
        String safeSender = sender == null || sender.length() == 0 ? "unknown" : sender;
        String safeBody = body == null ? "" : body;
        String bodyHash = digest(safeBody);
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            database.delete(
                    "seen",
                    "created_at<?",
                    new String[]{Long.toString(System.currentTimeMillis() - SEEN_RETENTION_MS)}
            );
            Cursor duplicate = database.query(
                    "seen",
                    new String[]{"_id"},
                    "sender=? AND body_hash=? AND received_at BETWEEN ? AND ?",
                    new String[]{
                            safeSender,
                            bodyHash,
                            Long.toString(receivedAt - DUPLICATE_WINDOW_MS),
                            Long.toString(receivedAt + DUPLICATE_WINDOW_MS)
                    },
                    null,
                    null,
                    null,
                    "1"
            );
            try {
                if (duplicate.moveToFirst()) {
                    database.setTransactionSuccessful();
                    return false;
                }
            } finally {
                duplicate.close();
            }

            ContentValues values = new ContentValues();
            values.put("fingerprint", fingerprint(safeSender, receivedAt, safeBody));
            values.put("sender", safeSender);
            values.put("received_at", receivedAt);
            values.put("body", safeBody);
            long result = database.insertWithOnConflict(
                    "queue",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
            );
            if (result != -1L) {
                ContentValues seen = new ContentValues();
                seen.put("sender", safeSender);
                seen.put("received_at", receivedAt);
                seen.put("body_hash", bodyHash);
                seen.put("created_at", System.currentTimeMillis());
                database.insertOrThrow("seen", null, seen);
            }
            database.setTransactionSuccessful();
            return result != -1L;
        } finally {
            database.endTransaction();
        }
    }

    QueuedSms next() {
        Cursor cursor = getReadableDatabase().query(
                "queue",
                new String[]{"_id", "sender", "received_at", "body"},
                null,
                null,
                null,
                null,
                "_id ASC",
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new QueuedSms(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getLong(2),
                    cursor.getString(3)
            );
        } finally {
            cursor.close();
        }
    }

    void delete(long id) {
        getWritableDatabase().delete("queue", "_id=?", new String[]{Long.toString(id)});
    }

    void markFailed(long id, String error) {
        ContentValues values = new ContentValues();
        values.put("last_error", error == null ? "unknown error" : error);
        getWritableDatabase().update("queue", values, "_id=?", new String[]{Long.toString(id)});
        getWritableDatabase().execSQL(
                "UPDATE queue SET attempts=attempts+1 WHERE _id=?",
                new Object[]{Long.valueOf(id)}
        );
    }

    long count() {
        return DatabaseUtils.longForQuery(getReadableDatabase(), "SELECT COUNT(*) FROM queue", null);
    }

    private static String fingerprint(String sender, long receivedAt, String body) {
        return digest(sender + "\u0000" + Long.toString(receivedAt) + "\u0000" + body);
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte part : hashed) {
                hex.append(String.format("%02x", part & 0xff));
            }
            return hex.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static void createSeenTable(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS seen (" +
                        "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "sender TEXT NOT NULL," +
                        "received_at INTEGER NOT NULL," +
                        "body_hash TEXT NOT NULL," +
                        "created_at INTEGER NOT NULL" +
                        ")"
        );
        database.execSQL(
                "CREATE INDEX IF NOT EXISTS seen_lookup " +
                        "ON seen(sender, body_hash, received_at)"
        );
    }
}

package com.ufi.voicegateway;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

final class SmsDb extends SQLiteOpenHelper {
    static final int TYPE_INBOX = 1;
    static final int TYPE_SENT = 2;

    static final class Row {
        final long id;
        final String address;
        final String body;
        final long date;
        final int type;
        final boolean read;

        Row(long id, String address, String body, long date, int type, boolean read) {
            this.id = id;
            this.address = address;
            this.body = body;
            this.date = date;
            this.type = type;
            this.read = read;
        }
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    SmsDb(Context context) {
        super(context, "ufi_messages.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE messages ("
                        + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "fingerprint TEXT NOT NULL UNIQUE,"
                        + "address TEXT NOT NULL,"
                        + "body TEXT NOT NULL,"
                        + "date INTEGER NOT NULL,"
                        + "type INTEGER NOT NULL,"
                        + "read INTEGER NOT NULL DEFAULT 0"
                        + ")");
        database.execSQL("CREATE INDEX messages_date ON messages(date DESC)");
        database.execSQL("CREATE INDEX messages_address ON messages(address)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            Cursor cursor = database.query(
                    "messages",
                    new String[] {"_id", "address", "body", "date", "type"},
                    null, null, null, null, "_id ASC");
            try {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    ContentValues values = new ContentValues();
                    values.put("fingerprint", fingerprint(
                            cursor.getInt(4),
                            cursor.getString(1),
                            cursor.getLong(3),
                            cursor.getString(2)));
                    int updated = database.updateWithOnConflict(
                            "messages",
                            values,
                            "_id=?",
                            new String[] {Long.toString(id)},
                            SQLiteDatabase.CONFLICT_IGNORE);
                    if (updated == 0) {
                        database.delete("messages", "_id=?", new String[] {Long.toString(id)});
                    }
                }
            } finally {
                cursor.close();
            }
        }
    }

    long insertIncoming(String address, long date, String body) {
        return insert(address, date, body, TYPE_INBOX, false, null);
    }

    long insertSent(String address, long date, String body) {
        return insert(address, date, body, TYPE_SENT, true, null);
    }

    long insertFromIcc(String address, long date, String body, int type, boolean read) {
        return insert(address, date, body, type, read, null);
    }

    int markRead(String address) {
        ContentValues values = new ContentValues();
        values.put("read", Integer.valueOf(1));
        return getWritableDatabase().update(
                "messages",
                values,
                "address=? AND type=?",
                new String[] {address, Integer.toString(TYPE_INBOX)});
    }

    List<Row> latest(int requestedLimit) {
        int limit = Math.max(1, Math.min(250, requestedLimit));
        Cursor cursor = getReadableDatabase().query(
                "messages",
                new String[] {"_id", "address", "body", "date", "type", "read"},
                null,
                null,
                null,
                null,
                "date DESC, _id DESC",
                Integer.toString(limit));
        ArrayList<Row> rows = new ArrayList<Row>();
        try {
            while (cursor.moveToNext()) {
                rows.add(new Row(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3),
                        cursor.getInt(4),
                        cursor.getInt(5) != 0));
            }
        } finally {
            cursor.close();
        }
        return rows;
    }

    private long insert(String address, long date, String body, int type,
            boolean read, String sourceKey) {
        String safeAddress = safe(address).trim();
        if (safeAddress.length() == 0) {
            safeAddress = "Unknown sender";
        }
        String safeBody = safe(body);
        long safeDate = date > 0L ? date : System.currentTimeMillis();
        String fingerprint = sourceKey == null
                ? fingerprint(type, safeAddress, safeDate, safeBody) : sourceKey;
        ContentValues values = new ContentValues();
        values.put("fingerprint", fingerprint);
        values.put("address", safeAddress);
        values.put("body", safeBody);
        values.put("date", Long.valueOf(safeDate));
        values.put("type", Integer.valueOf(type));
        values.put("read", Integer.valueOf(read ? 1 : 0));
        return getWritableDatabase().insertWithOnConflict(
                "messages", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(UTF8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte part : hashed) {
                String item = Integer.toHexString(part & 0xff);
                if (item.length() == 1) {
                    hex.append('0');
                }
                hex.append(item);
            }
            return hex.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String fingerprint(int type, String address, long date, String body) {
        return digest(type + "\u0000" + safe(address) + "\u0000" + date
                + "\u0000" + safe(body));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

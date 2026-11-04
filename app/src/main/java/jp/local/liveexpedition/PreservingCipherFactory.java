package jp.local.liveexpedition;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

/** Room adapter using the maintained SQLCipher engine; corruption never deletes user files. */
public final class PreservingCipherFactory implements SupportSQLiteOpenHelper.Factory {
    private final byte[] password;

    public PreservingCipherFactory(byte[] password) {
        this.password = password;
    }

    @Override
    public SupportSQLiteOpenHelper create(SupportSQLiteOpenHelper.Configuration config) {
        SQLiteOpenHelper helper = new SQLiteOpenHelper(
                config.context, config.name, password, null, config.callback.version, 0,
                (database, exception) -> { throw new IllegalStateException("Encrypted database unavailable; files preserved"); },
                null, true) {
            @Override public void onConfigure(net.zetetic.database.sqlcipher.SQLiteDatabase db) { config.callback.onConfigure(db); }
            @Override public void onCreate(net.zetetic.database.sqlcipher.SQLiteDatabase db) { config.callback.onCreate(db); }
            @Override public void onUpgrade(net.zetetic.database.sqlcipher.SQLiteDatabase db, int oldVersion, int newVersion) { config.callback.onUpgrade(db, oldVersion, newVersion); }
            @Override public void onDowngrade(net.zetetic.database.sqlcipher.SQLiteDatabase db, int oldVersion, int newVersion) { config.callback.onDowngrade(db, oldVersion, newVersion); }
            @Override public void onOpen(net.zetetic.database.sqlcipher.SQLiteDatabase db) { config.callback.onOpen(db); }
        };
        return new SupportSQLiteOpenHelper() {
            @Override public String getDatabaseName() { return config.name; }
            @Override public void setWriteAheadLoggingEnabled(boolean enabled) { helper.setWriteAheadLoggingEnabled(enabled); }
            @Override public SupportSQLiteDatabase getWritableDatabase() { return helper.getWritableDatabase(); }
            @Override public SupportSQLiteDatabase getReadableDatabase() { return helper.getReadableDatabase(); }
            @Override public void close() { helper.close(); }
        };
    }
}

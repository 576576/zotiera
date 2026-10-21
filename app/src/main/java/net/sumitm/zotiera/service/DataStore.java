package net.sumitm.zotiera.service;

import android.util.Log;

import com.zerotier.sdk.DataStoreGetListener;
import com.zerotier.sdk.DataStorePutListener;

import net.sumitm.zotiera.util.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * ZeroTier 的文件数据源。
 *
 * 所有文件都落在实例目录里（见 {@code InstanceStore}）；自定义 planet 不走文件，直接替换
 * 「planet」这个名字的读写，避免引擎把用户给的 planet 覆盖掉。
 */
public class DataStore implements DataStoreGetListener, DataStorePutListener {

    private static final String TAG = "DataStore";

    private final File baseDir;

    /** 非空表示这个实例用自定义 planet，字节从该文件读。 */
    private final File planetOverride;

    public DataStore(File baseDir, File planetOverride) {
        this.baseDir = baseDir;
        this.planetOverride = planetOverride;
    }

    private boolean isPlanet(String name) {
        return planetOverride != null && Constants.FILE_PLANET.equals(name);
    }

    @Override
    public int onDataStorePut(String name, byte[] buffer, boolean secure) {
        Log.d(TAG, "Writing File: " + name + ", to: " + baseDir);
        if (isPlanet(name)) {
            return 0;
        }
        try {
            File file = new File(baseDir, name);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            FileOutputStream out = new FileOutputStream(file);
            out.write(buffer);
            out.flush();
            out.close();
            return 0;
        } catch (IOException e) {
            StringWriter stringWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stringWriter));
            Log.e(TAG, stringWriter.toString());
            return -2;
        }
    }

    @Override
    public int onDelete(String name) {
        Log.d(TAG, "Deleting File: " + name);
        if (isPlanet(name)) {
            return 0;
        }
        File file = new File(baseDir, name);
        if (!file.exists()) {
            return 0;
        }
        return file.delete() ? 0 : 1;
    }

    @Override
    public long onDataStoreGet(String name, byte[] out_buffer) {
        Log.d(TAG, "Reading File: " + name);
        File file = isPlanet(name) ? planetOverride : new File(baseDir, name);
        if (!file.exists()) {
            return 0;
        }
        try {
            FileInputStream in = new FileInputStream(file);
            int read = in.read(out_buffer);
            in.close();
            return read;
        } catch (IOException e) {
            Log.e(TAG, "", e);
            return -2;
        }
    }
}

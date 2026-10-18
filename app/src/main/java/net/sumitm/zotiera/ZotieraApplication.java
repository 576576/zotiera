package net.sumitm.zotiera;

import android.app.Application;
import android.util.Log;

import net.sumitm.zotiera.data.ZotieraDatabase;
import net.sumitm.zotiera.data.ZotieraRepository;

/**
 * 主程序入口
 *
 * @author kaaass
 */
public class ZotieraApplication extends Application {

    private ZotieraRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("Application", "Starting Application");
        this.repository = new ZotieraRepository(ZotieraDatabase.create(this));
    }

    public ZotieraRepository getRepository() {
        return this.repository;
    }
}

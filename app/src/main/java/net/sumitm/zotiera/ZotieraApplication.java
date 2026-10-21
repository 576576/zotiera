package net.sumitm.zotiera;

import android.app.Application;
import android.util.Log;

import net.sumitm.zotiera.data.ZotieraDatabase;
import net.sumitm.zotiera.data.ZotieraRepository;
import net.sumitm.zotiera.identity.InstanceStore;

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
        // 老布局（files 根目录）搬进默认实例目录，升级后节点 ID 与已加入网络都不变
        new InstanceStore(this).bootstrapLegacy(this.repository);
    }

    public ZotieraRepository getRepository() {
        return this.repository;
    }
}

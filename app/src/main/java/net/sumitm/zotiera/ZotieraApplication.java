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
        // 行为稳定前不做数据接续：旧布局整个丢掉，身份由引擎重新生成
        new InstanceStore(this).resetLegacyLayout();
        this.repository.ensureOfficialPlanet();
        this.repository.ensureDefaultIdentity();
    }

    public ZotieraRepository getRepository() {
        return this.repository;
    }
}

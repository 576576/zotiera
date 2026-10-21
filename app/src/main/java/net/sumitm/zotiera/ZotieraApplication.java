package net.sumitm.zotiera;

import android.app.Application;
import android.util.Log;

import androidx.preference.PreferenceManager;

import net.sumitm.zotiera.data.ZotieraDatabase;
import net.sumitm.zotiera.data.ZotieraRepository;
import net.sumitm.zotiera.identity.InstanceStore;

/**
 * 主程序入口
 *
 * @author kaaass
 */
public class ZotieraApplication extends Application {

    /** 这一版已经不再读的偏好键，启动时删掉，免得留在 shared_prefs 里。 */
    private static final String[] OBSOLETE_PREF_KEYS = {
            "planet_use_custom",
            "set_planet_file",
            "disable_no_notification_alert",
            "amoled_black",
    };

    private ZotieraRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("Application", "Starting Application");
        pruneObsoletePrefs();
        this.repository = new ZotieraRepository(ZotieraDatabase.create(this));
        // 行为稳定前不做数据接续：旧布局整个丢掉，身份由引擎重新生成
        new InstanceStore(this).resetLegacyLayout();
        this.repository.ensureOfficialPlanet();
        this.repository.ensureDefaultIdentity();
    }

    private void pruneObsoletePrefs() {
        var prefs = PreferenceManager.getDefaultSharedPreferences(this);
        var editor = prefs.edit();
        boolean dirty = false;
        for (var key : OBSOLETE_PREF_KEYS) {
            if (prefs.contains(key)) {
                editor.remove(key);
                dirty = true;
            }
        }
        if (dirty) editor.apply();
    }

    public ZotieraRepository getRepository() {
        return this.repository;
    }
}

package wintahh.rageutils.updater;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Fabric's preLaunch entrypoint is the earliest hook available to a mod jar.
 * Keep this class tiny so it does only the pending update swap and then gets
 * out of Fabric's normal startup path.
 */
public final class EarlyUpdateBootstrap implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        UpdateManager.applyPendingUpdateAtBoot();
    }
}

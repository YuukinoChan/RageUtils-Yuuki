package wintahh.rageutils.module;

import wintahh.rageutils.HudConfig;

public class HideArmor extends Module {
    public HideArmor() {
        super("HideArmor", "/yuuki ha");
    }

    @Override
    public void toggle() {
        super.toggle();
        HudConfig.INSTANCE.hideArmor = isEnabled();
        HudConfig.save();
    }
}

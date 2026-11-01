package dev.egor201.physizen.bukkit.display;

import org.bukkit.entity.Entity;

public interface BindingHooks {

    BindingHooks NONE = new BindingHooks() {
        @Override
        public void dropPending(String worldId, String bodyName) {
        }

        @Override
        public void forget(Entity entity) {
        }
    };

    void dropPending(String worldId, String bodyName);

    void forget(Entity entity);
}

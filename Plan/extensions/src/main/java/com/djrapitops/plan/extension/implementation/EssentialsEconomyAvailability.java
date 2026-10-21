/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan.extension.implementation;

import java.math.BigDecimal;
import java.util.UUID;

/** Checks the optional economy contract without initializing Essentials or its listener. */
final class EssentialsEconomyAvailability {

    private EssentialsEconomyAvailability() {
        // Utility class
    }

    static boolean isAvailable(ClassLoader loader) {
        try {
            Class<?> essentials = Class.forName("com.earth2me.essentials.Essentials", false, loader);
            Class<?> settings = Class.forName("net.ess3.api.ISettings", false, loader);
            Class<?> user = Class.forName("com.earth2me.essentials.User", false, loader);
            Class<?> event = Class.forName("net.ess3.api.events.UserBalanceUpdateEvent", false, loader);
            Class<?> player = Class.forName("org.bukkit.entity.Player", false, loader);
            return essentials.getMethod("getSettings").getReturnType() == settings
                    && essentials.getMethod("getUser", UUID.class).getReturnType() == user
                    && settings.getMethod("isEcoDisabled").getReturnType() == boolean.class
                    && user.getMethod("getMoney").getReturnType() == BigDecimal.class
                    && event.getMethod("getPlayer").getReturnType() == player;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }
}

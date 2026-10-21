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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EssentialsEconomyAvailabilityTest {
    @TempDir
    Path directory;

    @Test
    void completeContractIsAvailableWithoutInitializingPluginClasses() throws Exception {
        try (URLClassLoader loader = fixture("complete")) {
            assertTrue(EssentialsEconomyAvailability.isAvailable(loader));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"coreOnly", "noSettingsMethod", "noMoneyMethod", "noEvent", "wrongMoneyReturn", "wrongSettingsReturn", "wrongPlayerReturn"})
    void incompleteEconomyContractIsUnavailableEvenWithCorePresent(String variant) throws Exception {
        try (URLClassLoader loader = fixture(variant)) {
            assertNotNull(Class.forName("com.earth2me.essentials.Essentials", false, loader));
            assertFalse(EssentialsEconomyAvailability.isAvailable(loader), variant);
        }
    }

    @Test
    void missingEssentialsIsUnavailable() throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader())) {
            assertFalse(EssentialsEconomyAvailability.isAvailable(loader));
        }
    }

    private URLClassLoader fixture(String variant) throws Exception {
        List<String> arguments = new ArrayList<>(List.of("-d", directory.toString()));
        write(arguments, "org.bukkit.entity.Player", "public interface Player {} ");
        write(arguments, "net.ess3.api.ISettings", "public interface ISettings { "
                + ("coreOnly".equals(variant) || "noSettingsMethod".equals(variant) ? "" : "boolean isEcoDisabled();") + " }");
        write(arguments, "com.earth2me.essentials.User", "public class User { "
                + ("coreOnly".equals(variant) || "noMoneyMethod".equals(variant) ? ""
                : "wrongMoneyReturn".equals(variant) ? "public double getMoney() { return 71.25; }"
                : "public java.math.BigDecimal getMoney() { return new java.math.BigDecimal(\"71.25\"); }") + " }");
        write(arguments, "com.earth2me.essentials.Essentials", "public class Essentials { "
                + "static { if (System.nanoTime() != 0) throw new AssertionError(\"plugin initialized by probe\"); } "
                + "public " + ("wrongSettingsReturn".equals(variant) ? "Object" : "net.ess3.api.ISettings") + " getSettings() { throw new AssertionError(); } "
                + "public User getUser(java.util.UUID id) { throw new AssertionError(); } }");
        // getPlayer is inherited in the real event API.
        write(arguments, "org.bukkit.event.player.PlayerEvent", "public class PlayerEvent { public "
                + ("wrongPlayerReturn".equals(variant) ? "Object" : "org.bukkit.entity.Player")
                + " getPlayer() { throw new AssertionError(); } }");
        if (!"coreOnly".equals(variant) && !"noEvent".equals(variant)) {
            write(arguments, "net.ess3.api.events.UserBalanceUpdateEvent", "public class UserBalanceUpdateEvent extends org.bukkit.event.player.PlayerEvent {} ");
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(new String[0])));
        return new URLClassLoader(new URL[]{directory.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    private void write(List<String> arguments, String name, String body) throws Exception {
        Path file = directory.resolve(name.replace('.', '/') + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "package " + name.substring(0, name.lastIndexOf('.')) + "; " + body);
        arguments.add(file.toString());
    }
}

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

import com.djrapitops.plan.extension.Caller;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.ExtensionService;
import net.playeranalytics.extension.essentials.EssentialsExtensionFactory;
import net.playeranalytics.extension.cmi.CMIExtensionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExtensionRegisterTest {
    @Test
    void disabledConfigDoesNotProbeOrConstructEconomyFactory() throws Exception {
        ExtensionRegister register = prepared(false, Set.of(), mock(ExtensionService.class));
        try (MockedStatic<EssentialsEconomyAvailability> api = mockStatic(EssentialsEconomyAvailability.class);
             MockedConstruction<EssentialsExtensionFactory> factories = mockConstruction(EssentialsExtensionFactory.class)) {
            register.registerEssentialsEconomyExtension();
            api.verifyNoInteractions();
            assertTrue(factories.constructed().isEmpty());
        }
    }

    @Test
    void disabledSetDoesNotProbeOrConstructEconomyFactory() throws Exception {
        ExtensionRegister register = prepared(true, Set.of("EssentialsEco"), mock(ExtensionService.class));
        try (MockedStatic<EssentialsEconomyAvailability> api = mockStatic(EssentialsEconomyAvailability.class);
             MockedConstruction<EssentialsExtensionFactory> factories = mockConstruction(EssentialsExtensionFactory.class)) {
            register.registerEssentialsEconomyExtension();
            api.verifyNoInteractions();
            assertTrue(factories.constructed().isEmpty());
        }
    }

    @Test
    void missingContractDoesNotConstructOrRegisterEconomy() throws Exception {
        ExtensionService service = mock(ExtensionService.class);
        ExtensionRegister register = prepared(true, Set.of(), service);
        try (MockedStatic<EssentialsEconomyAvailability> api = mockStatic(EssentialsEconomyAvailability.class);
             MockedConstruction<EssentialsExtensionFactory> factories = mockConstruction(EssentialsExtensionFactory.class)) {
            api.when(() -> EssentialsEconomyAvailability.isAvailable(any())).thenReturn(false);
            register.registerEssentialsEconomyExtension();
            assertTrue(factories.constructed().isEmpty());
            verifyNoInteractions(service);
            assertNull(field(register, "registerException"));
        }
    }

    @Test
    void compatibleEnabledEconomyRegistersAndSubscribes() throws Exception {
        ExtensionService service = mock(ExtensionService.class);
        DataExtension extension = mock(DataExtension.class);
        Caller caller = mock(Caller.class);
        when(extension.getPluginName()).thenReturn("EssentialsEco");
        when(service.register(extension)).thenReturn(Optional.of(caller));
        ExtensionRegister register = prepared(true, Set.of(), service);
        try (MockedStatic<EssentialsEconomyAvailability> api = mockStatic(EssentialsEconomyAvailability.class);
             MockedConstruction<EssentialsExtensionFactory> factories = mockConstruction(EssentialsExtensionFactory.class,
                     (factory, context) -> when(factory.createEcoExtension()).thenReturn(Optional.of(extension)))) {
            api.when(() -> EssentialsEconomyAvailability.isAvailable(any())).thenReturn(true);
            register.registerEssentialsEconomyExtension();
            assertEquals(1, factories.constructed().size());
            verify(service).register(extension);
            verify(factories.constructed().getFirst()).registerEconomyUpdateListeners(caller);
            assertNull(field(register, "registerException"));
        }
    }

    @Test
    void economySkipRetainsNonEconomyEssentialsRegistration() {
        ExtensionService service = mock(ExtensionService.class);
        DataExtension retained = mock(DataExtension.class);
        Caller caller = mock(Caller.class);
        DataExtension unrelated = mock(DataExtension.class);
        when(unrelated.getPluginName()).thenReturn("CMI");
        when(service.register(unrelated)).thenReturn(Optional.of(caller));
        when(retained.getPluginName()).thenReturn("Essentials");
        when(service.register(retained)).thenReturn(Optional.of(caller));
        try (MockedStatic<ExtensionService> services = mockStatic(ExtensionService.class);
             MockedStatic<EssentialsEconomyAvailability> api = mockStatic(EssentialsEconomyAvailability.class);
             MockedConstruction<EssentialsExtensionFactory> factories = mockConstruction(EssentialsExtensionFactory.class,
                     (factory, context) -> when(factory.createExtension()).thenReturn(Optional.of(retained)));
             MockedConstruction<CMIExtensionFactory> unrelatedFactories = mockConstruction(CMIExtensionFactory.class,
                     (factory, context) -> when(factory.createCMIExtension()).thenReturn(Optional.of(unrelated)))) {
            services.when(ExtensionService::getInstance).thenReturn(service);
            api.when(() -> EssentialsEconomyAvailability.isAvailable(any())).thenReturn(false);
            ExtensionRegister register = new ExtensionRegister(name -> Set.of("Essentials", "CMI").contains(name));
            assertDoesNotThrow(() -> register.registerBuiltInExtensions(Set.of("EssentialsEco")));
            assertEquals(1, factories.constructed().size());
            verify(service).register(retained);
            verify(service).register(unrelated);
            verify(unrelatedFactories.constructed().getFirst()).registerCMIUpdateListeners(caller);
            verify(factories.constructed().getFirst()).registerUpdateListeners(caller);
            verify(factories.constructed().getFirst(), never()).createEcoExtension();
        }
    }

    private ExtensionRegister prepared(boolean enabled, Set<String> disabled, ExtensionService service) throws Exception {
        ExtensionRegister register = new ExtensionRegister(name -> enabled);
        set(register, "disabledExtensions", disabled);
        set(register, "extensionService", service);
        return register;
    }

    private void set(ExtensionRegister register, String name, Object value) throws Exception {
        Field field = ExtensionRegister.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(register, value);
    }

    private Object field(ExtensionRegister register, String name) throws Exception {
        Field field = ExtensionRegister.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(register);
    }
}

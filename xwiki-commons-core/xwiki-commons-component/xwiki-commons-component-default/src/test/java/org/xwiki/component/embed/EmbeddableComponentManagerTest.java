/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.component.embed;

import java.util.List;
import java.util.Map;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import javax.inject.Provider;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.xwiki.component.descriptor.ComponentDescriptor;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.component.descriptor.DefaultComponentDependency;
import org.xwiki.component.descriptor.DefaultComponentDescriptor;
import org.xwiki.component.manager.ComponentEventManager;
import org.xwiki.component.manager.ComponentLifecycleException;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.phase.Disposable;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;

/**
 * Unit tests for {@link EmbeddableComponentManager}.
 * 
 * @version $Id$
 * @since 2.0M1
 */
@RunWith(JMock.class)
public class EmbeddableComponentManagerTest
{
    private Mockery mockery = new JUnit4Mockery();

    public Mockery getMockery()
    {
        return this.mockery;
    }

    public static interface Role
    {
    }

    public static class RoleImpl implements Role
    {
    }

    public static class OtherRoleImpl implements Role
    {
    }

    public static class InitializableRoleImpl implements Role, Initializable
    {
        private boolean initialized = false;

        @Override
        public void initialize() throws InitializationException
        {
            initialized = true;
        }

        public boolean isInitialized()
        {
            return initialized;
        }
    }

    public static class DisposableRoleImpl implements Role, Disposable
    {
        private boolean finalized = false;

        @Override
        public void dispose() throws ComponentLifecycleException
        {
            finalized = true;
        }

        public boolean isFinalized()
        {
            return finalized;
        }
    }

    public static class LoggingRoleImpl implements Role
    {
        private Logger logger;

        public Logger getLogger()
        {
            return this.logger;
        }
    }

    public static class ProviderImpl implements Provider<Role>
    {
        private Role role;

        @Override 
        public Role get()
        {
            return this.role;
        }
    }

    public static interface Role2
    {
        Provider<Role> getRoleProvider();
    }

    public static class Role2Impl implements Role2
    {
        private Provider<Role> roleProvider;

        @Override
        public Provider<Role> getRoleProvider()
        {
            return this.roleProvider;
        }
    }
    
    @Test
    public void testGetComponentDescriptorList() throws Exception
    {
        EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        DefaultComponentDescriptor<Role> d1 = new DefaultComponentDescriptor<Role>();
        d1.setRole(Role.class);
        d1.setRoleHint("hint1");
        ecm.registerComponent(d1);

        DefaultComponentDescriptor<Role> d2 = new DefaultComponentDescriptor<Role>();
        d2.setRole(Role.class);
        d2.setRoleHint("hint2");
        ecm.registerComponent(d2);

        List<ComponentDescriptor<Role>> cds = ecm.getComponentDescriptorList(Role.class);
        Assert.assertEquals(2, cds.size());
        Assert.assertTrue(cds.contains(d1));
        Assert.assertTrue(cds.contains(d2));
    }

    @Test
    public void testRegisterComponentOverExistingOne() throws Exception
    {
        EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        DefaultComponentDescriptor<Role> d1 = new DefaultComponentDescriptor<Role>();
        d1.setRole(Role.class);
        d1.setImplementation(RoleImpl.class);
        ecm.registerComponent(d1);

        Object instance = ecm.lookup(Role.class);
        Assert.assertSame(RoleImpl.class, instance.getClass());

        DefaultComponentDescriptor<Role> d2 = new DefaultComponentDescriptor<Role>();
        d2.setRole(Role.class);
        d2.setImplementation(OtherRoleImpl.class);
        ecm.registerComponent(d2);

        instance = ecm.lookup(Role.class);
        Assert.assertSame(OtherRoleImpl.class, instance.getClass());
    }

    @Test
    public void testRegisterComponentInstance() throws Exception
    {
        EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        DefaultComponentDescriptor<Role> d1 = new DefaultComponentDescriptor<Role>();
        d1.setRole(Role.class);
        d1.setImplementation(RoleImpl.class);
        Role instance = new RoleImpl();
        ecm.registerComponent(d1, instance);

        Assert.assertSame(instance, ecm.lookup(Role.class));
    }

    @Test
    public void testUnregisterComponent() throws Exception
    {
        EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        DefaultComponentDescriptor<Role> d1 = new DefaultComponentDescriptor<Role>();
        d1.setRole(Role.class);
        d1.setImplementation(RoleImpl.class);
        ecm.registerComponent(d1);

        // Verify that the component is properly registered
        Assert.assertSame(RoleImpl.class, ecm.lookup(Role.class).getClass());

        ecm.unregisterComponent(d1.getRole(), d1.getRoleHint());

        // Verify that the component is not registered anymore
        try {
            ecm.lookup(d1.getRole());
            Assert.fail("Should have thrown a ComponentLookupException");
        } catch (ComponentLookupException expected) {
            // The exception message doesn't matter. All we need to know is that the component descriptor 
            // doesn't exist anymore.
        }
    }
    
    @Test
    public void testLookupWhenComponentInParent() throws Exception
    {
        EmbeddableComponentManager ecm = new EmbeddableComponentManager();
        ecm.setParent(createParentComponentManager());

        Role instance = ecm.lookup(Role.class);
        Assert.assertNotNull(instance);
    }
    
    @Test
    public void testLookupListAndMapWhenSomeComponentsInParent() throws Exception
    {
        EmbeddableComponentManager ecm = new EmbeddableComponentManager();
        ecm.setParent(createParentComponentManager());
        
        // Register a component with the same Role and Hint as in the parent
        DefaultComponentDescriptor<Role> cd1 = new DefaultComponentDescriptor<Role>();
        cd1.setRole(Role.class);
        cd1.setImplementation(RoleImpl.class);
        Role roleImpl = new RoleImpl();
        ecm.registerComponent(cd1, roleImpl);

        // Register a component with the same Role as in the parent but with a different hint 
        DefaultComponentDescriptor<Role> cd2 = new DefaultComponentDescriptor<Role>();
        cd2.setRole(Role.class);
        cd2.setRoleHint("hint");
        cd2.setImplementation(RoleImpl.class);
        ecm.registerComponent(cd2);

        // Verify that the components are found
        Assert.assertEquals(3, ecm.lookupList(Role.class).size());
        // Note: We find only 2 components since 2 components are registered with the same Role and Hint.
        // In this case we ensure that the component returned is the one from the client CM
        Map<String, Role> instances = ecm.lookupMap(Role.class);
        Assert.assertEquals(2, instances.size());
        Assert.assertSame(roleImpl, instances.get("default"));
    }

    @Test
    public void testHasComponent() throws Exception
    {
        EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        DefaultComponentDescriptor<Role> d1 = new DefaultComponentDescriptor<Role>();
        d1.setRole(Role.class);
        d1.setRoleHint("default");
        ecm.registerComponent(d1);

        Assert.assertTrue(ecm.hasComponent(Role.class));
        Assert.assertTrue(ecm.hasComponent(Role.class, "default"));
    }

    @Test
    public void testLoggingInjection() throws Exception
    {
        EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        DefaultComponentDescriptor<Role> d = new DefaultComponentDescriptor<Role>();
        d.setRole(Role.class);
        d.setImplementation(LoggingRoleImpl.class);

        DefaultComponentDependency dependencyDescriptor = new DefaultComponentDependency();
        dependencyDescriptor.setMappingType(Logger.class);
        dependencyDescriptor.setName("logger");

        d.addComponentDependency(dependencyDescriptor);
        ecm.registerComponent(d);

        LoggingRoleImpl impl = (LoggingRoleImpl) ecm.lookup(Role.class);
        Assert.assertNotNull(impl.getLogger());
    }

    @Test
    public void testRegisterProvider() throws Exception
    {
        EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        // Register RoleImpl component first
        DefaultComponentDescriptor<Role> cd1 = new DefaultComponentDescriptor<Role>();
        cd1.setRole(Role.class);
        cd1.setImplementation(RoleImpl.class);
        ecm.registerComponent(cd1);        
        
        // Register our Provider as a component
        DefaultComponentDescriptor<Provider> cd2 = new DefaultComponentDescriptor<Provider>();
        cd2.setRole(Provider.class);
        cd2.setRoleHint("myprovider");
        cd2.setImplementation(ProviderImpl.class);
        DefaultComponentDependency<Role> dd2 = new DefaultComponentDependency<Role>();
        dd2.setRole(Role.class);
        dd2.setMappingType(Role.class);
        dd2.setName("role");
        cd2.addComponentDependency(dd2);
        ecm.registerComponent(cd2);

        // Verify registration worked by looking up our Provider as a Component
        Provider provider = ecm.lookup(Provider.class, "myprovider");
        Assert.assertEquals(RoleImpl.class.getName(), provider.get().getClass().getName());
        
        // Now verify that a component can get injected our provider.
        // First register it and the look it up
        DefaultComponentDescriptor<Role2> cd3 = new DefaultComponentDescriptor<Role2>();
        cd3.setRole(Role2.class);
        cd3.setImplementation(Role2Impl.class);
        DefaultComponentDependency<Provider> dd3 = new DefaultComponentDependency<Provider>();
        dd3.setRole(Provider.class);
        dd3.setRoleHint("myprovider");
        dd3.setMappingType(Provider.class);
        dd3.setName("roleProvider");
        cd3.addComponentDependency(dd3);
        ecm.registerComponent(cd3);        

        Role2 role2 = ecm.lookup(Role2.class);
        Assert.assertSame(provider, role2.getRoleProvider());
        
        // Verify that removing a Provider component works
        ecm.unregisterComponent(Provider.class, "myprovider");
        Assert.assertFalse(ecm.hasComponent(Provider.class, "myprovider"));
        
        // Verify we cannot get our Provider injected anymore (but a default one is injected now).
        ecm.unregisterComponent(Role2.class, "default");
        ecm.registerComponent(cd3);
        role2 = ecm.lookup(Role2.class);
        Assert.assertSame(GenericProvider.class.getName(), role2.getRoleProvider().getClass().getName());
        Assert.assertEquals(RoleImpl.class.getName(), role2.getRoleProvider().get().getClass().getName());
    }

    private ComponentManager createParentComponentManager() throws Exception
    {
        EmbeddableComponentManager parent = new EmbeddableComponentManager();
        DefaultComponentDescriptor<Role> cd = new DefaultComponentDescriptor<Role>();
        cd.setRole(Role.class);
        cd.setImplementation(RoleImpl.class);
        parent.registerComponent(cd);
        return parent;
    }

    @Test
    public void testRegisterInitializableComponent() throws Exception
    {
        EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        DefaultComponentDescriptor<Role> cd = new DefaultComponentDescriptor<Role>();
        cd.setRole(Role.class);
        cd.setImplementation(InitializableRoleImpl.class);
        ecm.registerComponent(cd);
        InitializableRoleImpl instance = (InitializableRoleImpl) ecm.lookup(Role.class);

        Assert.assertTrue(instance.isInitialized());
    }

    @Test
    public void testUnregisterDisposableSingletonComponent() throws Exception
    {
        EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        DefaultComponentDescriptor<Role> cd = new DefaultComponentDescriptor<Role>();
        cd.setRole(Role.class);
        cd.setImplementation(DisposableRoleImpl.class);
        cd.setInstantiationStrategy(ComponentInstantiationStrategy.SINGLETON);

        ecm.registerComponent(cd);
        DisposableRoleImpl instance = (DisposableRoleImpl) ecm.lookup(Role.class);
        ecm.unregisterComponent(cd.getRole(), cd.getRoleHint());

        Assert.assertTrue(instance.isFinalized());
    }

    @Test
    public void testUnregisterDisposableSingletonComponentWithInstance() throws Exception
    {
        EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        DefaultComponentDescriptor<Role> cd = new DefaultComponentDescriptor<Role>();
        cd.setRole(Role.class);
        cd.setInstantiationStrategy(ComponentInstantiationStrategy.SINGLETON);

        DisposableRoleImpl instance = new DisposableRoleImpl();
        ecm.registerComponent(cd, instance);
        ecm.unregisterComponent(cd.getRole(), cd.getRoleHint());

        Assert.assertTrue(instance.isFinalized());
    }

    @Test
    public void testRelease() throws Exception
    {
        final EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        final DefaultComponentDescriptor<Role> cd = new DefaultComponentDescriptor<Role>();
        cd.setRole(Role.class);
        cd.setImplementation(RoleImpl.class);
        Role roleImpl = new RoleImpl();
        ecm.registerComponent(cd, roleImpl);

        final ComponentEventManager cem = getMockery().mock(ComponentEventManager.class);
        ecm.setComponentEventManager(cem);

        getMockery().checking(new Expectations() {{
            // Verify that when we release a component an unregistration event is sent followed by a registration one
            // see comments in {@link EmbeddableComponentManager#release} code.
            oneOf(cem).notifyComponentUnregistered(cd, ecm);
            oneOf(cem).notifyComponentRegistered(cd, ecm);
        }});
        
        ecm.release(roleImpl);

        Assert.assertNotNull(ecm.lookup(Role.class));
        Assert.assertNotSame(roleImpl, ecm.lookup(Role.class));
    }

    @Test
    public void testReleaseDisposableComponent() throws Exception
    {
        EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        DefaultComponentDescriptor<Role> cd = new DefaultComponentDescriptor<Role>();
        cd.setRole(Role.class);
        cd.setImplementation(DisposableRoleImpl.class);
        cd.setInstantiationStrategy(ComponentInstantiationStrategy.SINGLETON);

        ecm.registerComponent(cd);
        DisposableRoleImpl instance = (DisposableRoleImpl) ecm.lookup(Role.class);
        ecm.release(instance);

        Assert.assertTrue(instance.isFinalized());
    }

    @Test
    public void testRegisterComponentNotification() throws Exception
    {
        final EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        final DefaultComponentDescriptor<Role> cd = new DefaultComponentDescriptor<Role>();
        cd.setRole(Role.class);
        cd.setImplementation(RoleImpl.class);

        final ComponentEventManager cem = getMockery().mock(ComponentEventManager.class);
        ecm.setComponentEventManager(cem);

        getMockery().checking(new Expectations() {{
            oneOf(cem).notifyComponentRegistered(cd, ecm);
        }});

        ecm.registerComponent(cd);
    }

    @Test
    public void testUnregisterComponentNotification() throws Exception
    {
        final EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        final DefaultComponentDescriptor<Role> cd = new DefaultComponentDescriptor<Role>();
        cd.setRole(Role.class);
        cd.setImplementation(RoleImpl.class);
        ecm.registerComponent(cd);

        final ComponentEventManager cem = getMockery().mock(ComponentEventManager.class);
        ecm.setComponentEventManager(cem);

        getMockery().checking(new Expectations() {{
            oneOf(cem).notifyComponentUnregistered(cd, ecm);
        }});

        ecm.unregisterComponent(cd.getRole(), cd.getRoleHint());
    }

    @Test
    public void testRegisterComponentNotificationOnSecondRegistration() throws Exception
    {
        final EmbeddableComponentManager ecm = new EmbeddableComponentManager();

        final DefaultComponentDescriptor<Role> cd1 = new DefaultComponentDescriptor<Role>();
        cd1.setRole(Role.class);
        cd1.setImplementation(RoleImpl.class);
        ecm.registerComponent(cd1);

        final DefaultComponentDescriptor<Role> cd2 = new DefaultComponentDescriptor<Role>();
        cd2.setRole(Role.class);
        cd2.setImplementation(OtherRoleImpl.class);

        final ComponentEventManager cem = getMockery().mock(ComponentEventManager.class);
        ecm.setComponentEventManager(cem);

        getMockery().checking(new Expectations() {{
            oneOf(cem).notifyComponentUnregistered(cd1, ecm);
            oneOf(cem).notifyComponentRegistered(cd2, ecm);
        }});

        ecm.registerComponent(cd2);
    }
}

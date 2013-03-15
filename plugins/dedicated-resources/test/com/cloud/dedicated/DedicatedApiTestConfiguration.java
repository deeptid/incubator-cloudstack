// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.dedicated;

import java.io.IOException;

import org.apache.cloudstack.acl.SecurityChecker;
import org.apache.cloudstack.affinity.AffinityGroupProcessor;
import org.apache.cloudstack.affinity.dao.AffinityGroupDao;
import org.apache.cloudstack.affinity.dao.AffinityGroupVMMapDao;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

import com.cloud.agent.AgentManager;
import com.cloud.alert.AlertManager;
import com.cloud.capacity.dao.CapacityDao;
import com.cloud.configuration.ConfigurationManagerImpl;
import com.cloud.configuration.dao.ConfigurationDao;
import com.cloud.dc.dao.AccountVlanMapDao;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.DataCenterIpAddressDao;
import com.cloud.dc.dao.DataCenterLinkLocalIpAddressDao;
import com.cloud.dc.dao.DedicatedResourceDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.dc.dao.PodVlanMapDao;
import com.cloud.dc.dao.VlanDao;
import com.cloud.domain.dao.DomainDao;
import com.cloud.event.EventUtils;
import com.cloud.event.dao.EventDao;
import com.cloud.host.dao.HostDao;
import com.cloud.manager.DedicatedResourceManagerImpl;
import com.cloud.network.Ipv6AddressManager;
import com.cloud.network.NetworkManager;
import com.cloud.network.NetworkModel;
import com.cloud.network.NetworkService;
import com.cloud.network.StorageNetworkManager;
import com.cloud.network.dao.FirewallRulesDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkTrafficTypeDao;
import com.cloud.network.element.DhcpServiceProvider;
import com.cloud.network.element.IpDeployer;
import com.cloud.network.element.NetworkElement;
import com.cloud.network.guru.NetworkGuru;
import com.cloud.network.lb.LoadBalancingRulesManager;
import com.cloud.network.rules.FirewallManager;
import com.cloud.network.rules.RulesManager;
import com.cloud.network.vpc.NetworkACLManager;
import com.cloud.network.vpc.VpcManager;
import com.cloud.network.vpn.RemoteAccessVpnService;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.offerings.dao.NetworkOfferingServiceMapDao;
import com.cloud.projects.ProjectManager;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.dao.DiskOfferingDao;
import com.cloud.storage.dao.S3Dao;
import com.cloud.storage.dao.SwiftDao;
import com.cloud.storage.s3.S3Manager;
import com.cloud.storage.secondary.SecondaryStorageVmManager;
import com.cloud.storage.swift.SwiftManager;
import com.cloud.user.AccountManager;
import com.cloud.user.ResourceLimitService;
import com.cloud.user.UserContextInitializer;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.component.SpringComponentScanUtils;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.UserVmDao;

@Configuration
@ComponentScan(basePackageClasses = {DedicatedResourceManagerImpl.class}, includeFilters = { @Filter(value = DedicatedApiTestConfiguration.Library.class, type = FilterType.CUSTOM) }, useDefaultFilters = false)
public class DedicatedApiTestConfiguration {
//    @Bean
//    public DedicatedResourceManagerImpl dedicatedResourceManagerImpl() {
//        return Mockito.mock(DedicatedResourceManagerImpl.class);
//    }

    @Bean
    public DedicatedResourceDao dedicatedResourceDao() {
        return Mockito.mock(DedicatedResourceDao.class);
    }

    @Bean
    public AffinityGroupProcessor affinityGroupProcessor() {
        return Mockito.mock(AffinityGroupProcessor.class);
    }

    @Bean
    public AccountDao accountDao() {
        return Mockito.mock(AccountDao.class);
    }

    @Bean
    public DomainDao DomainDao() {
        return Mockito.mock(DomainDao.class);
    }

    @Bean
    public DataCenterDao DataCenterDao() {
        return Mockito.mock(DataCenterDao.class);
    }

    @Bean
    public HostPodDao HostPodDao() {
        return Mockito.mock(HostPodDao.class);
    }

    @Bean
    public ClusterDao ClusterDao() {
        return Mockito.mock(ClusterDao.class);
    }

    @Bean
    public HostDao HostDao() {
        return Mockito.mock(HostDao.class);
    }


    @Bean
    public EventUtils eventUtils() {
        return Mockito.mock(EventUtils.class);
    }

    @Bean
    public EventDao eventDao() {
        return Mockito.mock(EventDao.class);
    }

    @Bean
    public AccountVlanMapDao AccountVlanMapDao() {
        return Mockito.mock(AccountVlanMapDao.class);
    }

    @Bean
    public PodVlanMapDao PodVlanMapDao() {
        return Mockito.mock(PodVlanMapDao.class);
    }

    @Bean
    public SwiftDao SwiftDao() {
        return Mockito.mock(SwiftDao.class);
    }

    @Bean
    public S3Dao S3Dao() {
        return Mockito.mock(S3Dao.class);
    }

    @Bean
    public ServiceOfferingDao ServiceOfferingDao() {
        return Mockito.mock(ServiceOfferingDao.class);
    }

    @Bean
    public DiskOfferingDao DiskOfferingDao() {
        return Mockito.mock(DiskOfferingDao.class);
    }

    @Bean
    public ConfigurationManagerImpl ConfigurationManagerImpl() {
        return Mockito.mock(ConfigurationManagerImpl.class);
    }

    @Bean
    public VlanDao VlanDao() {
        return Mockito.mock(VlanDao.class);
    }

    @Bean
    public IPAddressDao IpAddressDao() {
        return Mockito.mock(IPAddressDao.class);
    }
    
    @Bean
    public DataCenterIpAddressDao DataCenterIpAddressDao() {
        return Mockito.mock(DataCenterIpAddressDao.class);
    }

    @Bean
    public CapacityDao CapacityDao() {
        return Mockito.mock(CapacityDao.class);
    }

    @Bean
    public PhysicalNetworkDao PhysicalNetworkDao() {
        return Mockito.mock(PhysicalNetworkDao.class);
    }

    @Bean
    public PhysicalNetworkTrafficTypeDao PhysicalNetworkTrafficTypeDao() {
        return Mockito.mock(PhysicalNetworkTrafficTypeDao.class);
    }

    @Bean
    public NicDao NicDao() {
        return Mockito.mock(NicDao.class);
    }

    @Bean
    public FirewallRulesDao FirewallRulesDao() {
        return Mockito.mock(FirewallRulesDao.class);
    }

    @Bean
    public DataCenterLinkLocalIpAddressDao LinkLocalIpAddressDao() {
        return Mockito.mock(DataCenterLinkLocalIpAddressDao.class);
    }

    @Bean
    public ComponentContext componentContext() {
        return Mockito.mock(ComponentContext.class);
    }

    @Bean
    public UserContextInitializer userContextInitializer() {
        return Mockito.mock(UserContextInitializer.class);
    }

    @Bean
    public UserVmVO userVmVO() {
        return Mockito.mock(UserVmVO.class);
    }

    @Bean
    public AffinityGroupDao affinityGroupDao() {
        return Mockito.mock(AffinityGroupDao.class);
    }

    @Bean
    public AffinityGroupVMMapDao affinityGroupVMMapDao() {
        return Mockito.mock(AffinityGroupVMMapDao.class);
    }

    @Bean
    public AccountManager acctMgr() {
        return Mockito.mock(AccountManager.class);
    }

    @Bean
    public NetworkService ntwkSvc() {
        return Mockito.mock(NetworkService.class);
    }

    @Bean
    public NetworkModel ntwkMdl() {
        return Mockito.mock(NetworkModel.class);
    }

    @Bean
    public AlertManager alertMgr() {
        return Mockito.mock(AlertManager.class);
    }

    @Bean
    public SecurityChecker securityChkr() {
        return Mockito.mock(SecurityChecker.class);
    }

    @Bean
    public ResourceLimitService resourceSvc() {
        return Mockito.mock(ResourceLimitService.class);
    }

    @Bean
    public ProjectManager projectMgr() {
        return Mockito.mock(ProjectManager.class);
    }

    @Bean
    public SecondaryStorageVmManager ssvmMgr() {
        return Mockito.mock(SecondaryStorageVmManager.class);
    }

    @Bean
    public SwiftManager swiftMgr() {
        return Mockito.mock(SwiftManager.class);
    }

    @Bean
    public S3Manager s3Mgr() {
        return Mockito.mock(S3Manager.class);
    }

    @Bean
    public VpcManager vpcMgr() {
        return Mockito.mock(VpcManager.class);
    }

    @Bean
    public UserVmDao userVMDao() {
        return Mockito.mock(UserVmDao.class);
    }

    @Bean
    public RulesManager rulesMgr() {
        return Mockito.mock(RulesManager.class);
    }

    @Bean
    public LoadBalancingRulesManager lbRulesMgr() {
        return Mockito.mock(LoadBalancingRulesManager.class);
    }

    @Bean
    public RemoteAccessVpnService vpnMgr() {
        return Mockito.mock(RemoteAccessVpnService.class);
    }

    @Bean
    public NetworkGuru ntwkGuru() {
        return Mockito.mock(NetworkGuru.class);
    }

    @Bean
    public NetworkElement ntwkElement() {
        return Mockito.mock(NetworkElement.class);
    }

    @Bean
    public IpDeployer ipDeployer() {
        return Mockito.mock(IpDeployer.class);
    }

    @Bean
    public DhcpServiceProvider dhcpProvider() {
        return Mockito.mock(DhcpServiceProvider.class);
    }

    @Bean
    public FirewallManager firewallMgr() {
        return Mockito.mock(FirewallManager.class);
    }

    @Bean
    public AgentManager agentMgr() {
        return Mockito.mock(AgentManager.class);
    }

    @Bean
    public StorageNetworkManager storageNtwkMgr() {
        return Mockito.mock(StorageNetworkManager.class);
    }

    @Bean
    public NetworkACLManager ntwkAclMgr() {
        return Mockito.mock(NetworkACLManager.class);
    }

    @Bean
    public Ipv6AddressManager ipv6Mgr() {
        return Mockito.mock(Ipv6AddressManager.class);
    }

    @Bean
    public ConfigurationDao configDao() {
        return Mockito.mock(ConfigurationDao.class);
    }

    @Bean
    public NetworkManager networkManager() {
        return Mockito.mock(NetworkManager.class);
    }

    @Bean
    public NetworkOfferingDao networkOfferingDao() {
        return Mockito.mock(NetworkOfferingDao.class);
    }

    @Bean
    public NetworkDao networkDao() {
        return Mockito.mock(NetworkDao.class);
    }

    @Bean
    public NetworkOfferingServiceMapDao networkOfferingServiceMapDao() {
        return Mockito.mock(NetworkOfferingServiceMapDao.class);
    }

    public static class Library implements TypeFilter {

        @Override
        public boolean match(MetadataReader mdr, MetadataReaderFactory arg1) throws IOException {
            mdr.getClassMetadata().getClassName();
            ComponentScan cs = DedicatedApiTestConfiguration.class.getAnnotation(ComponentScan.class);
            return SpringComponentScanUtils.includedInBasePackageClasses(mdr.getClassMetadata().getClassName(), cs);
        }

    }
}

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
package com.cloud.offerings;

import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.junit.Before;

import com.cloud.configuration.ConfigurationManager;
import com.cloud.configuration.ConfigurationService;
import com.cloud.network.vpc.VpcManager;
import com.cloud.offering.ServiceOffering;
import com.cloud.offerings.dao.MockServiceOfferingDaoImpl;
import com.cloud.server.ManagementService;
import com.cloud.utils.component.ComponentLocator;
import com.cloud.utils.component.MockComponentLocator;
import com.cloud.vm.VirtualMachine;
import com.cloud.vpc.MockConfigurationManagerImpl;

public class ServiceOfferingUnitTest extends TestCase{
    private static final Logger s_logger = Logger.getLogger(ServiceOfferingUnitTest.class);
    MockComponentLocator _locator;
    VpcManager _vpcService;
    ConfigurationService _configService;
    ConfigurationManager _configManager;

    @Override
    @Before
    public void setUp() throws Exception {
        _locator = new MockComponentLocator(ManagementService.Name);
        _locator.addDao("ServiceOfferingDao", MockServiceOfferingDaoImpl.class);
        _locator.makeActive(null);
        _configManager = ComponentLocator.inject(MockConfigurationManagerImpl.class);
        s_logger.info("Finished setUp");
    }
    
    public void test() {
        s_logger.debug("Starting test for Service Offering");
        createServiceOffering();
    }
    
    private void createServiceOffering() {
        ServiceOffering result = null;
        String msg = null;
        // test for dedicated service offering
        try {
            result = _configManager.createServiceOffering(1l, false, VirtualMachine.Type.User, "offering", 1, 512, 500, "check dedication", false, false, false, null, null, "H1", 0, true);
        } catch (Exception ex) {
           msg = ex.getMessage();
        } finally {
            assert(result != null): "Test Failed: Service Offering not created";
            assert(result.getIsDedicated() == true): "Test Failed: Service Offering is not dedicated";
        }
        
        // test for non-dedicated service offering
        try {
            result = _configManager.createServiceOffering(2l, false, VirtualMachine.Type.User, "offering", 1, 512, 500, "check non dedication", false, false, false, null, null, "H1", 0, false);
        } catch (Exception ex) {
           msg = ex.getMessage();
        } finally {
            assert(result != null): "Test Failed: Service Offering not created";
            assert(result.getIsDedicated() == false): "Test Failed: Service Offering is dedicated";
        }
    }
    

}

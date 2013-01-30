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
package com.cloud.offerings.dao;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.ConfigurationException;

import junit.framework.TestCase;

import org.apache.cloudstack.api.command.user.vm.DeployVMCmd;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import com.cloud.configuration.ConfigurationService;
import com.cloud.offering.ServiceOffering;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDaoImpl;
import com.cloud.utils.component.ComponentLocator;
import com.cloud.vm.VirtualMachine;

@Ignore
public class ServiceOfferingDaoImplTest extends TestCase {
    
    private ServiceOffering offering;
    private ConfigurationService configuration;
    private ServiceOfferingDaoImpl soDao;
    private ServiceOfferingVO vo;
    private DeployVMCmd deployvm;
	Map<String,Object> _parameters;
	
	@Before
	public void setUp() throws ConfigurationException {
	    offering = mock(ServiceOffering.class);
	    configuration = mock(ConfigurationService.class);
	    vo = mock(ServiceOfferingVO.class);
	    soDao = mock(ServiceOfferingDaoImpl.class);
	    deployvm = new DeployVMCmd(){  
	    };
	}
	
	@Test
	public void testDedicatedServiceOffering() {
        //test for dedicated service offering 
        soDao = ComponentLocator.inject(ServiceOfferingDaoImpl.class);
        vo = new ServiceOfferingVO("Service Offering For dedicated vm", 1, 512, 500, 0, 0, false, true, null, false, true, null, true, VirtualMachine.Type.User, true);
        soDao.persist(vo);
        assert (vo.getIsDedicated() == true) : "Not a Dedicated Service Offering, isDedicated:" + vo.getIsDedicated();        
        soDao.expunge(vo.getId());
        
	}
	
	@Test
    public void testNonDedicatedServiceOffering() {
        //test for dedicated service offering 
        soDao = ComponentLocator.inject(ServiceOfferingDaoImpl.class);
        vo = new ServiceOfferingVO("Service Offering For non-dedicated vm", 1, 512, 500, 0, 0, false, false, null, false, true, null, true, VirtualMachine.Type.User, true);
        soDao.persist(vo);
        assert (vo.getIsDedicated() == false) : "Not a non-Dedicated Service Offering, isDedicated:" + vo.getIsDedicated();
        soDao.expunge(vo.getId());
	}
	
	@Test
	public void testDeployVmWithDedicatedSO() {
	    //test vm is deployed with dedicated Service Offering
	}

}


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
package com.cloud.api;

import java.io.File;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import com.cloud.server.ManagementService;
import com.cloud.utils.PropertiesUtil;
import com.cloud.utils.component.ComponentLocator;

public class RegionTest {
	private static final Logger s_logger = Logger.getLogger(RegionTest.class.getName());
	
    public static void main(String args[]){
    	System.out.println("Starting");
    	File file = PropertiesUtil.findConfigFile("log4j-cloud.xml");
    	if (file != null) {
    		s_logger.info("log4j configuration found at " + file.getAbsolutePath());
    		DOMConfigurator.configureAndWatch(file.getAbsolutePath());
    	}
    	final ComponentLocator _locator = ComponentLocator.getLocator(ManagementService.Name, "components-regions.xml", "log4j-cloud");
    	MockApiServer.initApiServer(new String[] { "commands.properties" });
    	System.out.println("Started");
    }
}
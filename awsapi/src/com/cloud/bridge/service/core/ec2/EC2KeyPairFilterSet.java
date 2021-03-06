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

package com.cloud.bridge.service.core.ec2;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

public class EC2KeyPairFilterSet {
	protected final static Logger logger = Logger.getLogger(EC2KeyPairFilterSet.class);
	
	protected List<EC2Filter> filterSet = new ArrayList<EC2Filter>();    
	
	private Map<String,String> filterTypes = new HashMap<String,String>();
	
	public EC2KeyPairFilterSet() {
		// -> use these values to check that the proper filter is passed to this type of filter set
		filterTypes.put( "fingerprint", "String" );
		filterTypes.put( "key-name", "String" );
	}

	public void addFilter( EC2Filter param ) {	
		String filterName = param.getName();
		String value = (String) filterTypes.get( filterName );
		
		if (null == value) {
			// Changing this to silently ignore
			logger.error("Unsupported filter [" + filterName + "] - 1");
			return;
		}
		
		if (null != value && value.equalsIgnoreCase( "null" )) {
			logger.error("Unsupported filter [" + filterName + "] - 2");
			return;
		}	

		// ToDo we could add checks to make sure the type of a filters value is correct (e.g., an integer)
		filterSet.add( param );
	}
	
	public EC2Filter[] getFilterSet() {
		return filterSet.toArray(new EC2Filter[0]);
	}


	public EC2DescribeKeyPairsResponse evaluate( List<EC2SSHKeyPair> sampleList) throws ParseException	{
		EC2DescribeKeyPairsResponse resultList = new EC2DescribeKeyPairsResponse();
		
		boolean matched;
		
		EC2SSHKeyPair[] keypairSet = sampleList.toArray(new EC2SSHKeyPair[0]);
		EC2Filter[] filterSet = getFilterSet();
		for (EC2SSHKeyPair keyPair : keypairSet) {
			matched = true;
			for (EC2Filter filter : filterSet) {
				if (!filterMatched(keyPair, filter)) {
					matched = false;
					break;
				}
			}
			if (matched == true)
				resultList.addKeyPair(keyPair);

		}
		return resultList;
	}

	private boolean filterMatched( EC2SSHKeyPair keypair, EC2Filter filter ) throws ParseException {
		String filterName = filter.getName();
		String[] valueSet = filter.getValueSet();
		
		if ( filterName.equalsIgnoreCase("fingerprint")) {
			return containsString(keypair.getFingerprint(), valueSet);
		} else if ( filterName.equalsIgnoreCase("key-name")) {
			return containsString(keypair.getKeyName(), valueSet);
		}
		return false;
	}
	
	private boolean containsString( String lookingFor, String[] set ){
		if (lookingFor == null) 
			return false;
		
		for (String filter: set) {
			if (lookingFor.matches( filter )) return true;
		}
		return false;
	}

}

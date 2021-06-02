/**
 * ShinyProxy-Visualizer
 * 
 * Copyright (C) 2016-2021 Open Analytics
 * 
 * ===========================================================================
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 * 
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package com.metamorphsoftware.shinyproxy;

import java.io.File;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.metamorphsoftware.shinyproxy.services.SQLService;
import com.metamorphsoftware.shinyproxy.services.SQLService.UserFileAccess;

import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;

/**
 * @author Fraser LeFevre
 *
 */
@Component
@Primary
public class SQLProxySpecProvider implements IProxySpecProvider {
	
	@Inject
	protected SQLService sqlService;
	
	@Inject
	protected UserService userService;

	@Override
	public List<ProxySpec> getSpecs() {
		return sqlService.getUserFileAccess(sqlService.getUser(userService.getCurrentUserId()).getId(), false)
				.stream().map(SQLProxySpecProvider::createProxySpec)
				.collect(Collectors.toList());
	}
	
	private static ProxySpec createProxySpec(UserFileAccess ufa) {
		ProxySpec pSpec = new ProxySpec();
		pSpec.setId(ufa.getSharedUserId() + "_" + ufa.getFileId());
		pSpec.setDisplayName(ufa.getFilename());
		
		{
			ContainerSpec cSpec = new ContainerSpec();
			cSpec.setImage("visualizer");
			String datapath = System.getProperty("user.dir") + File.separator + "userdata" + File.separator 
					+ ufa.getSharedUserId() + File.separator + ufa.getFileId();
			cSpec.setVolumes(new String[] { String.format("%s:/home/visualizer/data", datapath) });
			cSpec.setEnv(Stream.of(
					new AbstractMap.SimpleEntry<String, String>("DIG_INPUT_CSV", "/home/visualizer/data/output.csv"))
						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
			cSpec.setCmd(new String[] {"R", "-e", "shiny::runApp('Dig',display.mode='normal', quiet=TRUE, launch.browser=FALSE, host='0.0.0.0', port=80)"});
			cSpec.setPortMapping(Stream.of(
					new AbstractMap.SimpleEntry<String, Integer>("default", 80))
						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
			pSpec.setContainerSpecs(Arrays.asList(new ContainerSpec[] { cSpec }));
		}
		
		return pSpec;
	}

	@Override
	public ProxySpec getSpec(String id) {
		if (id == null || id.isEmpty()) return null;
		return getSpecs().stream().filter(s -> id.equals(s.getId())).findAny().orElse(null);
	}
}

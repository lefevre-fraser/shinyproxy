/**
 * ShinyProxy-Visualizer
 * 
 * Copyright 2021 MetaMorph
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *       
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.metamorphsoftware.shinyproxy.providers;

import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.metamorphsoftware.shinyproxy.services.FileHandlingService;
import com.metamorphsoftware.shinyproxy.services.SQLService.File;
import com.metamorphsoftware.shinyproxy.services.SQLService.Record.DBWhereClause.DBWhereClauseBuilder;
import com.metamorphsoftware.shinyproxy.services.SQLService.UserFilePermission;
import com.metamorphsoftware.shinyproxy.services.SQLUserService;

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
	protected SQLUserService sqlUserService;
	
	@Inject
	protected UserService userService;
	
	@Inject
	protected FileHandlingService fileHandlingService;

	@Override
	public List<ProxySpec> getSpecs() {
		List<UserFilePermission> userfpList = sqlUserService.getUserFileAccess(false, true);
		return (userfpList == null ? List.<UserFilePermission>of() : userfpList)
				.stream().map(new Function<UserFilePermission, ProxySpec>() {
					@Override
					public ProxySpec apply(UserFilePermission ufp) {
						return createProxySpec(ufp);
					}
				})
				.collect(Collectors.toList());
	}
	
	/**
	 * @param UserFilePermission
	 * @return
	 */
	private ProxySpec createProxySpec(UserFilePermission UserFilePermission) {
		ProxySpec pSpec = new ProxySpec();
		pSpec.setId(UserFilePermission.getFileId().toString());
		
		File file = (File) new File().findOne(DBWhereClauseBuilder.Builder().withRecord(new File()).withWhereList()
					.addWhere().withColumn("id").withValue(UserFilePermission.getFileId()).addToWhereList()
					.addListToClause().build());
		
		pSpec.setDisplayName(file.getTitle());
		pSpec.setDescription(file.getDescription());
		String userId = (UserFilePermission.getUserId() == null ? "anonymous" : UserFilePermission.getUserId().toString());
		
		{
			ContainerSpec cSpec = new ContainerSpec();
			cSpec.setImage("visualizer");
			Path dataPath = fileHandlingService.findJSONLaunch(UserFilePermission.getFileId().toString(), userId);
			if (dataPath == null) {
				dataPath = fileHandlingService.findCSVLaunch(UserFilePermission.getFileId().toString(), userId);
			}
			
			cSpec.setVolumes(new String[] { String.format("%s:/home/visualizer/data", 
					(fileHandlingService.getConfig().getUserDockerVolume() == null ?
							dataPath.getParent().toAbsolutePath().toString() :
								fileHandlingService.getConfig().getUserDockerVolume())) });
			
			String filename = dataPath.getFileName().toString();
			String launchFile = "/home/visualizer/data/" + 
					(fileHandlingService.getConfig().getUserDockerVolume() == null ?
							"" : (userId + "/" + UserFilePermission.getFileId() + "/")) + filename;
			String envVar = (filename.endsWith(".csv") ? "DIG_INPUT_CSV" : "DIG_DATASET_CONFIG"); 
			cSpec.setEnv(Stream.of(
					new AbstractMap.SimpleEntry<String, String>(envVar, launchFile))
						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
			
//			cSpec.setCmd(new String[] {"R", "-e", "shiny::runApp('Dig',display.mode='normal', quiet=TRUE, launch.browser=FALSE, host='0.0.0.0', port=80)"});
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

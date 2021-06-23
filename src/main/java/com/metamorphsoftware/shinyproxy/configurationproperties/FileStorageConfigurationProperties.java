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
package com.metamorphsoftware.shinyproxy.configurationproperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

/**
 * @author Fraser LeFevre
 *
 */
@Validated
@ConstructorBinding
@ConfigurationProperties("proxy.storage.files")
public class FileStorageConfigurationProperties {
	private final String zipLocation;
	private final String userLocation;
	private final String zipDockerVolume;
	private final String userDockerVolume;

	/**
	 * @param zipLocation
	 * @param userLocation
	 * @param zipDockerVolume
	 * @param userDockerVolume
	 * @throws IOException
	 */
	public FileStorageConfigurationProperties(String zipLocation, String userLocation, String zipDockerVolume, String userDockerVolume) throws IOException {
		this.zipLocation = (zipLocation == null || zipLocation.isEmpty() ? "zips" : zipLocation);
		this.userLocation = (userLocation == null || userLocation.isEmpty() ? "userdata" : userLocation);
		this.zipDockerVolume = (zipDockerVolume == null || zipDockerVolume.isEmpty() ? null : zipDockerVolume);
		this.userDockerVolume = (userDockerVolume == null || userDockerVolume.isEmpty() ? null : userDockerVolume);
		Assert.isTrue(!Files.isSameFile(Path.of(this.zipLocation), Path.of(this.userLocation)), "user-location and location must differ");
	}

	/**
	 * @return the ziplocation
	 */
	public String getZipLocation() {
		return zipLocation;
	}

	/**
	 * @return the userLocation
	 */
	public String getUserLocation() {
		return userLocation;
	}

	/**
	 * @return the zipDockerVolume
	 */
	public String getZipDockerVolume() {
		return zipDockerVolume;
	}

	/**
	 * @return the userDockerVolume
	 */
	public String getUserDockerVolume() {
		return userDockerVolume;
	}
}

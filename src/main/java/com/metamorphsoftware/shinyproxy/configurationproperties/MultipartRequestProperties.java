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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * @author Fraser LeFevre
 *
 */
@Validated
@ConstructorBinding
@ConfigurationProperties("spring.servlet.multipart")
public class MultipartRequestProperties {
	private DataSize maxFileSize = DataSize.ofGigabytes(2);
	private DataSize maxRequestSize = DataSize.ofGigabytes(2);
	
	/**
	 * @param maxFileSize
	 * @param maxRequestSize
	 */
	public MultipartRequestProperties(DataSize maxFileSize, DataSize maxRequestSize) {
		this.maxFileSize = maxFileSize;
		this.maxRequestSize = maxRequestSize;
	}

	/**
	 * @return the maxFileSize
	 */
	public DataSize getMaxFileSize() {
		return maxFileSize;
	}

	/**
	 * @return the maxRequestSize
	 */
	public DataSize getMaxRequestSize() {
		return maxRequestSize;
	}
}

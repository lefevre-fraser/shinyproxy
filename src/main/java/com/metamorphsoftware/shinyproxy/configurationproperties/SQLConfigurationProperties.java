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

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/**
 * @author Fraser LeFevre
 *
 */
@Validated
@ConstructorBinding
@ConfigurationProperties("proxy.sql")
public class SQLConfigurationProperties {
	private String url = "jdbc:postgresql://localhost:8080/postgres";
	private String username = "postgres";
	
	@NotBlank
	private String password;
	
	@Min(1)
	private int maxConnections = 10;
	private int maxConnectionLifetimeMilliseconds = 0;
	private int maxIdleConnections = 5;
	private int minIdleConnections = 2;
	
	/**
	 * @param url
	 * @param username
	 * @param password
	 * @param maxConnections
	 * @param maxConnectionLifetimeMilliseconds
	 * @param maxIdleConnections
	 * @param minIdleConnections
	 */
	public SQLConfigurationProperties(String url, String username, @NotBlank String password,
			@Min(1) Integer maxConnections, Integer maxConnectionLifetimeMilliseconds, Integer maxIdleConnections,
			Integer minIdleConnections) {
		this.url = (url == null ? this.url : url);
		this.username = (username == null ? this.username : username);
		this.password = password;
		this.maxConnections = (maxConnections == null ? this.maxConnections : maxConnections);
		this.maxConnectionLifetimeMilliseconds = (maxConnectionLifetimeMilliseconds == null ? 
				this.maxConnectionLifetimeMilliseconds : this.maxConnectionLifetimeMilliseconds);
		this.maxIdleConnections = (maxIdleConnections == null ? this.maxIdleConnections : maxIdleConnections);
		this.minIdleConnections = (minIdleConnections == null ? this.minIdleConnections : minIdleConnections);
	}

	/**
	 * @return the url
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * @return the password
	 */
	public String getPassword() {
		String password = this.password;
		
		// once the password is used, forget it for security
		this.password = null;
		return password;
	}

	/**
	 * @return the maxConnections
	 */
	public int getMaxConnections() {
		return maxConnections;
	}

	/**
	 * @return the maxConnectionLifetimeMilliseconds
	 */
	public int getMaxConnectionLifetimeMilliseconds() {
		return maxConnectionLifetimeMilliseconds;
	}

	/**
	 * @return the maxIdleConnections
	 */
	public int getMaxIdleConnections() {
		return maxIdleConnections;
	}

	/**
	 * @return the minIdleConnections
	 */
	public int getMinIdleConnections() {
		return minIdleConnections;
	}
}

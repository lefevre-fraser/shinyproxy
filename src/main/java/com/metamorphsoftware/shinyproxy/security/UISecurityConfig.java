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
package com.metamorphsoftware.shinyproxy.security;

import javax.inject.Inject;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.security.ICustomSecurityConfig;
import eu.openanalytics.containerproxy.service.UserService;

@Component("MetaMorphUISecurityConfig")
@Order(Ordered.LOWEST_PRECEDENCE)
public class UISecurityConfig implements ICustomSecurityConfig {

	@Inject
	private IAuthenticationBackend auth;
	
	@Inject
	private UserService userService;
	
	@Override
	public void apply(HttpSecurity http) throws Exception {
		if (auth.hasAuthorization()) {
			
			// Permit access by anonymous users to linkshare and app/app_direct
			http.authorizeRequests().antMatchers("/linkshare/**", "/app/**", "/app_direct/**").permitAll();

			// Limit access to the admin pages
			http.authorizeRequests().antMatchers("/admin/**").hasAnyRole(userService.getAdminGroups());
		}
	}
}
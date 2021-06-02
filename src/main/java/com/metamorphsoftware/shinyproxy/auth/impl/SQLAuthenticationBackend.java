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
package com.metamorphsoftware.shinyproxy.auth.impl;

import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer.AuthorizedUrl;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;

public class SQLAuthenticationBackend implements IAuthenticationBackend {
	
	public static final String NAME = "sqlAuth";

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public boolean hasAuthorization() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void configureHttpSecurity(HttpSecurity http, @SuppressWarnings("rawtypes") AuthorizedUrl anyRequestConfigurer) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		// TODO Auto-generated method stub
		
	}

}

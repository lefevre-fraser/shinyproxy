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
package com.metamorphsoftware.shinyproxy.controllers;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.springframework.core.env.Environment;

import com.metamorphsoftware.shinyproxy.services.SQLService;

import eu.openanalytics.containerproxy.service.ProxyService;

public class BaseController extends eu.openanalytics.shinyproxy.controllers.BaseController {

	@Inject
	ProxyService proxyService;
	
	@Inject
	SQLService sqlService;
	
	@Inject
	Environment environment;
	
	private static Pattern deletePattern = Pattern.compile(".*?/delete[^/]*/(?<uuid>[^/]*)/?$");
	
	protected String getDeleteUUID(HttpServletRequest request) {
		Matcher matcher = deletePattern.matcher(request.getRequestURI());
		String uuid = matcher.matches() ? matcher.group("uuid") : null;
		return uuid;
	}
	
	protected Map<String, String> simpleMessageResponse(boolean error, String sucessMessage, String errorMessage) {
		Map<String, String> response = new HashMap<>(); 
		if (error) response.put("error", errorMessage);
		else response.put("message", sucessMessage);
		return response;
	}
}

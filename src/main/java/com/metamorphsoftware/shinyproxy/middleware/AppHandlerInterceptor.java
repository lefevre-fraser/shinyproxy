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
package com.metamorphsoftware.shinyproxy.middleware;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

@Component
@WebFilter(value={"/app/{appname}/**", "/app_direct/{appname}/**"})
public class AppHandlerInterceptor extends HandlerInterceptorAdapter {
	
	private static Logger logger = LogManager.getLogger(AppHandlerInterceptor.class);

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		@SuppressWarnings("unchecked")
		Map<String, String> map = new HashMap<String, String>((Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE));
		String appname = (String) map.get("appname");
        
		logger.info(map);
		logger.info(String.format("App preHandle: {appname, method}: {%s, %s}", appname, request.getMethod()));
		return super.preHandle(request, response, handler);
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {
		@SuppressWarnings("unchecked")
		Map<String, String> map = new HashMap<String, String>((Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE));
		String appname = (String) map.get("appname");
        
		logger.info(map);
		logger.info(String.format("App postHandle: {appname, method}: {%s, %s}", appname, request.getMethod()));
		super.postHandle(request, response, handler, modelAndView);
	}
	
}

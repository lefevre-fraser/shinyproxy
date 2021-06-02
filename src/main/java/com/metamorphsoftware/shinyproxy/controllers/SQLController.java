/**
 * ShinyProxy-Visualizer
 * 
 * Copyright (C) 2021 MetaMorph
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
import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.metamorphsoftware.shinyproxy.services.SQLService.File;

@Controller
public class SQLController extends BaseController {
	
	@RequestMapping("/status")
	protected Object status(ModelMap map, HttpServletRequest request) {
		prepareMap(map, request);
		
		map.put("status", 200);
		map.put("message", "SQL Controller");

		return "status";
    }
	
	@RequestMapping(value="/addfile", method=RequestMethod.GET)
	protected Object addFileGET(ModelMap map, HttpServletRequest request) {
		
		return "addfile";
	}
	
	@RequestMapping(value="/addfile", method=RequestMethod.PUT)
	protected Map<String,String> addFilePUT(ModelMap map, HttpServletRequest request) {
		request.getParameterMap();
		
		Map<String,String> response = new HashMap<>();
		response.put("message", "success!");
		return response;
	}
	
	@RequestMapping(value="/deletefile/*", method=RequestMethod.DELETE)
	protected Map<String,String> deleteFile(ModelMap map, HttpServletRequest request) {
		String fileId = getDeleteUUID(request);
		File deletedFile = sqlService.deleteFile(new File(fileId));
		
		return simpleMessageResponse(deletedFile == null, 
				String.format("Filed to delete file: %s", fileId), "success!");
	}
}

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

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.metamorphsoftware.shinyproxy.services.SQLService.User;

public class SQLAdminController extends BaseController {

	@RequestMapping(value="/admin/deleteuser/{userId}", method=RequestMethod.DELETE)
	@ResponseBody
	protected Map<String,String> deleteUser(@PathVariable("userId") String userId, HttpServletRequest request) {
		User deletedUser = sqlService.deleteUser(new User(userId));
		
		return simpleMessageResponse(deletedUser == null, "success!", 
				String.format("Fialed to delete user: %s", userId));
	}
}

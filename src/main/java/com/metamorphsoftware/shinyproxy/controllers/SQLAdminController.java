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
package com.metamorphsoftware.shinyproxy.controllers;

import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.metamorphsoftware.shinyproxy.services.SQLService.User;

/**
 * @author Fraser LeFevre
 *
 */
@Controller
public class SQLAdminController extends BaseController {
	@RequestMapping(value="/admin/deleteuser/{userId}", method=RequestMethod.DELETE)
	@ResponseBody
	protected MessageResponse deleteUser(@PathVariable("userId") String userId, HttpServletRequest request) {
		User user = User.fromId(UUID.fromString(userId));// sqlService.new User(userId);

		return new MessageResponse(!user.delete(), 
				String.format("Failed to delete user: %s", user.getUsername()));
	}
}

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

import java.util.TreeMap;

import javax.inject.Inject;

import org.springframework.core.env.Environment;

import com.metamorphsoftware.shinyproxy.services.FileHandlingService;
import com.metamorphsoftware.shinyproxy.services.SQLService;
import com.metamorphsoftware.shinyproxy.services.SQLUserService;

import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;

/**
 * @author Fraser LeFevre
 *
 */
public class BaseController extends eu.openanalytics.shinyproxy.controllers.BaseController {

	@Inject
	ProxyService proxyService;
	
	@Inject
	Environment environment;
	
	@Inject
	SQLService sqlService;
	
	@Inject
	SQLUserService sqlUserService;
	
	@Inject
	FileHandlingService fileHandlingService;
	
	@Inject
	UserService userService;
	
	/**
	 * @author Fraser LeFevre
	 *
	 */
	public static class MessageResponse extends TreeMap<String, String> {
		private static final long serialVersionUID = 6375452769798045370L;

		private final static String ERROR_KEY = "error";
		
		private final static String SUCCESS_KEY = "message";
		private final static String SUCCESS_MESSAGE = "success!";
		
		/**
		 * 
		 */
		public MessageResponse() {
			this(false, MessageResponse.SUCCESS_MESSAGE, null);
		}
		
		/**
		 * @param error
		 * @param successMessage
		 * @param errorMessage
		 */
		public MessageResponse(boolean error, String successMessage, String errorMessage) {
			super();
			if (error) this.put(MessageResponse.ERROR_KEY, errorMessage);
			else this.put(MessageResponse.SUCCESS_KEY, successMessage);
		}
		
		/**
		 * @param error
		 * @param errorMessage
		 */
		public MessageResponse(boolean error, String errorMessage) {
			this(error, MessageResponse.SUCCESS_MESSAGE, errorMessage);
		}
		
		/**
		 * @param errorMessage
		 */
		public MessageResponse(String errorMessage) {
			this(true, errorMessage);
		}
		
		/**
		 * @return
		 */
		public static MessageResponse success() {
			return new MessageResponse();
		}
		
		/**
		 * @param errorMessage
		 * @return
		 */
		public static MessageResponse error(String errorMessage) {
			return new MessageResponse(errorMessage);
		}
	}
}

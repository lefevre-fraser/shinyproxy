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
package com.metamorphsoftware.shinyproxy.services;

import javax.servlet.annotation.WebFilter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.metamorphsoftware.shinyproxy.configurationproperties.FileStorageConfigurationProperties;
import com.metamorphsoftware.shinyproxy.configurationproperties.MultipartRequestProperties;
import com.metamorphsoftware.shinyproxy.configurationproperties.SQLConfigurationProperties;
import com.metamorphsoftware.shinyproxy.middleware.LocationHandlerInterceptor;

/**
 * @author Fraser LeFevre
 *
 */
@Configuration
@EnableConfigurationProperties({ MultipartRequestProperties.class, SQLConfigurationProperties.class, FileStorageConfigurationProperties.class })
public class MiddlewareWebMvcConfigurer implements WebMvcConfigurer {

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		WebFilter filter = LocationHandlerInterceptor.class.getDeclaredAnnotation(WebFilter.class);
		registry.addInterceptor(new LocationHandlerInterceptor()).addPathPatterns(filter.value());
	}
	
	@Autowired
	MultipartRequestProperties multipartRequestProperties;
	
	@Bean
    public MultipartResolver multipartResolver() {
        CommonsMultipartResolver multipartResolver = new CommonsMultipartResolver();
//        multipartResolver.setMaxUploadSize(500000000);
        return multipartResolver;
    }
}

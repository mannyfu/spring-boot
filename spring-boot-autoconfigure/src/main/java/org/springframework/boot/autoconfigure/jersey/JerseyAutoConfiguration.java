/*
 * Copyright 2012-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.jersey;

import java.util.Arrays;
import java.util.EnumSet;

import javax.annotation.PostConstruct;
import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.ws.rs.ApplicationPath;

import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.ServletProperties;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.DispatcherServletAutoConfiguration;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.filter.RequestContextFilter;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Jersey.
 *
 * @author Dave Syer
 * @author Andy Wilkinson
 */
@Configuration
@ConditionalOnClass(name = {
		"org.glassfish.jersey.server.spring.SpringComponentProvider",
		"javax.servlet.ServletRegistration" })
@ConditionalOnBean(type = "org.glassfish.jersey.server.ResourceConfig")
@ConditionalOnWebApplication
@Order(Ordered.HIGHEST_PRECEDENCE)
@AutoConfigureBefore(DispatcherServletAutoConfiguration.class)
@EnableConfigurationProperties(JerseyProperties.class)
public class JerseyAutoConfiguration implements WebApplicationInitializer {

	@Autowired
	private JerseyProperties jersey;
	
	@Autowired
	private ListableBeanFactory context;

	@Autowired
	private ResourceConfig config;

	private String path;

	@PostConstruct
	public void path() {
		this.path = findPath(AnnotationUtils.findAnnotation(this.config.getClass(),
				ApplicationPath.class));
	}

	@Bean
	@ConditionalOnMissingBean
	public FilterRegistrationBean requestContextFilter() {
		FilterRegistrationBean registration = new FilterRegistrationBean();
		registration.setFilter(new RequestContextFilter());
		registration.setOrder(jersey.getFilter().getOrder()-1);
		registration.setName("requestContextFilter");
		return registration;
	}

	@Bean
	@ConditionalOnMissingBean(name = "jerseyFilterRegistration")
	@ConditionalOnExpression("'${spring.jersey.type:servlet}' == 'filter'")
	public FilterRegistrationBean jerseyFilterRegistration() {
		Class<? extends ResourceConfig> configType = this.config.getClass();
		FilterRegistrationBean registration = new FilterRegistrationBean();
		registration.setFilter(new ServletContainer());
		registration.setUrlPatterns(Arrays.asList(this.path));
		registration.setOrder(jersey.getFilter().getOrder());
		registration.addInitParameter(ServletProperties.JAXRS_APPLICATION_CLASS,
				configType.getName());
		registration.addInitParameter(CommonProperties.METAINF_SERVICES_LOOKUP_DISABLE,  "true");
		registration.setName("jerseyFilter");
        registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
		return registration;
	}
	
	@Bean
	@ConditionalOnMissingBean(name = "jerseyServletRegistration")
	@ConditionalOnExpression("'${spring.jersey.type:servlet}' == 'servlet'")
	public ServletRegistrationBean jerseyServletRegistration() {
		Class<? extends ResourceConfig> configType = this.config.getClass();
		ServletRegistrationBean registration = new ServletRegistrationBean(
				new ServletContainer(), this.path);
		registration.addInitParameter(ServletProperties.JAXRS_APPLICATION_CLASS,
				configType.getName());
		registration.addInitParameter(CommonProperties.METAINF_SERVICES_LOOKUP_DISABLE,  "true");
		registration.setName("jerseyServlet");
		return registration;
	}

	@Override
	public void onStartup(ServletContext servletContext) throws ServletException {
		// We need to switch *off* the Jersey WebApplicationInitializer because it
		// will try and register a ContextLoaderListener which we don't need
		servletContext.setInitParameter("contextConfigLocation", "<NONE>");
	}

	private static String findPath(ApplicationPath annotation) {
		// Jersey doesn't like to be the default servlet, so map to /* as a fallback
		if (annotation == null) {
			return "/*";
		}
		String path = annotation.value();
		return ((path.isEmpty() || path.equals("/")) ? "/*" : path + "/*");
	}

}
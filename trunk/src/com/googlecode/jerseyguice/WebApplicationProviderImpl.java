package com.googlecode.jerseyguice;

import com.googlecode.jerseyguice.copied.WebApplicationImpl;
import com.sun.jersey.api.container.ContainerException;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.container.WebApplicationProvider;

public class WebApplicationProviderImpl implements WebApplicationProvider {

	public WebApplication createWebApplication() throws ContainerException {
		return new WebApplicationImpl();
	}

}

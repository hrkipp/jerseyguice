package com.googlecode.jerseyguice.example;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import com.google.inject.Inject;
import com.googlecode.jerseyguice.JerseyGuiceManaged;

@Path("/test")
@JerseyGuiceManaged(module=GuiceInjector.class)
public class ExampleService {

	@Inject
	IService service;
	
	@GET
	@Path("hello")
	@Produces("text/html")
	public UserVO ideInit(@PathParam(value="name") String name,@QueryParam(value="place") String place){
		return new UserVO();//"hello "+service.getName();
	}
}

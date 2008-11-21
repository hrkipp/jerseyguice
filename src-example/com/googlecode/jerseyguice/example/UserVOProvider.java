package com.googlecode.jerseyguice.example;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import com.google.inject.Inject;
import com.googlecode.jerseyguice.JerseyGuiceManaged;

@Provider
@Produces("text/html")
@JerseyGuiceManaged(module=GuiceInjector.class)
public class UserVOProvider implements MessageBodyWriter<UserVO>{

	@Inject
	IService service;
	
	public long getSize(UserVO arg0, Class<?> arg1, Type arg2,
			Annotation[] arg3, MediaType arg4) {
		// TODO Auto-generated method stub
		return service.getName().getBytes().length;
	}

	public boolean isWriteable(Class<?> arg0, Type arg1, Annotation[] arg2,
			MediaType arg3) {
		// TODO Auto-generated method stub
		return true;
	}

	public void writeTo(UserVO arg0, Class<?> arg1, Type arg2,
			Annotation[] arg3, MediaType arg4,
			MultivaluedMap<String, Object> arg5, OutputStream out)
			throws IOException, WebApplicationException {
		out.write(service.getName().getBytes());
	}

}

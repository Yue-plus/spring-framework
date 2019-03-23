/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.mvc.method.annotation;

import static org.junit.Assert.*;
import static org.mockito.BDDMockito.*;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.junit.Before;
import org.junit.Test;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.test.MockHttpServletRequest;
import org.springframework.mock.web.test.MockHttpServletResponse;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Test fixture for {@link RequestResponseBodyMethodProcessor} delegating to a
 * mock HttpMessageConverter.
 *
 * <p>Also see {@link RequestResponseBodyMethodProcessorTests}.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 */
public class RequestResponseBodyMethodProcessorMockTests {

	private RequestResponseBodyMethodProcessor processor;

	private HttpMessageConverter<String> messageConverter;

	private MethodParameter paramRequestBodyString;
	private MethodParameter paramInt;
	private MethodParameter paramValidBean;
	private MethodParameter paramStringNotRequired;
	private MethodParameter returnTypeString;
	private MethodParameter returnTypeInt;
	private MethodParameter returnTypeStringProduces;

	private ModelAndViewContainer mavContainer;

	private NativeWebRequest webRequest;

	private MockHttpServletRequest servletRequest;


	@SuppressWarnings("unchecked")
	@Before
	public void setUp() throws Exception {
		messageConverter = mock(HttpMessageConverter.class);
		given(messageConverter.getSupportedMediaTypes()).willReturn(Collections.singletonList(MediaType.TEXT_PLAIN));

		processor = new RequestResponseBodyMethodProcessor(Collections.<HttpMessageConverter<?>>singletonList(messageConverter));

		Method methodHandle1 = getClass().getMethod("handle1", String.class, Integer.TYPE);
		paramRequestBodyString = new MethodParameter(methodHandle1, 0);
		paramInt = new MethodParameter(methodHandle1, 1);
		returnTypeString = new MethodParameter(methodHandle1, -1);
		returnTypeInt = new MethodParameter(getClass().getMethod("handle2"), -1);
		returnTypeStringProduces = new MethodParameter(getClass().getMethod("handle3"), -1);
		paramValidBean = new MethodParameter(getClass().getMethod("handle4", SimpleBean.class), 0);
		paramStringNotRequired = new MethodParameter(getClass().getMethod("handle5", String.class), 0);

		mavContainer = new ModelAndViewContainer();

		servletRequest = new MockHttpServletRequest();
		servletRequest.setMethod("POST");
		webRequest = new ServletWebRequest(servletRequest, new MockHttpServletResponse());
	}

	@Test
	public void supportsParameter() {
		assertTrue("RequestBody parameter not supported", processor.supportsParameter(paramRequestBodyString));
		assertFalse("non-RequestBody parameter supported", processor.supportsParameter(paramInt));
	}

	@Test
	public void supportsReturnType() {
		assertTrue("ResponseBody return type not supported", processor.supportsReturnType(returnTypeString));
		assertFalse("non-ResponseBody return type supported", processor.supportsReturnType(returnTypeInt));
	}

	@Test
	public void resolveArgument() throws Exception {
		MediaType contentType = MediaType.TEXT_PLAIN;
		servletRequest.addHeader("Content-Type", contentType.toString());

		String body = "Foo";
		servletRequest.setContent(body.getBytes(Charset.forName("UTF-8")));

		given(messageConverter.canRead(String.class, contentType)).willReturn(true);
		given(messageConverter.read(eq(String.class), isA(HttpInputMessage.class))).willReturn(body);

		Object result = processor.resolveArgument(paramRequestBodyString, mavContainer, webRequest, new ValidatingBinderFactory());

		assertEquals("Invalid argument", body, result);
		assertFalse("The requestHandled flag shouldn't change", mavContainer.isRequestHandled());
	}

	@Test
	public void resolveArgumentNotValid() throws Exception {
		try {
			testResolveArgumentWithValidation(new SimpleBean(null));
			fail("Expected exception");
		}
		catch (MethodArgumentNotValidException e) {
			assertEquals("simpleBean", e.getBindingResult().getObjectName());
			assertEquals(1, e.getBindingResult().getErrorCount());
			assertNotNull(e.getBindingResult().getFieldError("name"));
		}
	}

	@Test
	public void resolveArgumentValid() throws Exception {
		testResolveArgumentWithValidation(new SimpleBean("name"));
	}

	private void testResolveArgumentWithValidation(SimpleBean simpleBean) throws Exception {
		MediaType contentType = MediaType.TEXT_PLAIN;
		servletRequest.addHeader("Content-Type", contentType.toString());
		servletRequest.setContent("payload".getBytes(Charset.forName("UTF-8")));

		@SuppressWarnings("unchecked")
		HttpMessageConverter<SimpleBean> beanConverter = mock(HttpMessageConverter.class);
		given(beanConverter.getSupportedMediaTypes()).willReturn(Collections.singletonList(MediaType.TEXT_PLAIN));
		given(beanConverter.canRead(SimpleBean.class, contentType)).willReturn(true);
		given(beanConverter.read(eq(SimpleBean.class), isA(HttpInputMessage.class))).willReturn(simpleBean);

		processor = new RequestResponseBodyMethodProcessor(Collections.<HttpMessageConverter<?>>singletonList(beanConverter));
		processor.resolveArgument(paramValidBean, mavContainer, webRequest, new ValidatingBinderFactory());
	}

	@Test(expected = HttpMediaTypeNotSupportedException.class)
	public void resolveArgumentCannotRead() throws Exception {
		MediaType contentType = MediaType.TEXT_PLAIN;
		servletRequest.addHeader("Content-Type", contentType.toString());
		servletRequest.setContent("payload".getBytes(Charset.forName("UTF-8")));

		given(messageConverter.canRead(String.class, contentType)).willReturn(false);

		processor.resolveArgument(paramRequestBodyString, mavContainer, webRequest, null);
	}

	@Test(expected = HttpMediaTypeNotSupportedException.class)
	public void resolveArgumentNoContentType() throws Exception {
		servletRequest.setContent("payload".getBytes(Charset.forName("UTF-8")));
		given(messageConverter.canRead(String.class, MediaType.APPLICATION_OCTET_STREAM)).willReturn(false);
		processor.resolveArgument(paramRequestBodyString, mavContainer, webRequest, null);
	}

	@Test(expected = HttpMediaTypeNotSupportedException.class)
	public void resolveArgumentInvalidContentType() throws Exception {
		this.servletRequest.setContentType("bad");
		servletRequest.setContent("payload".getBytes(Charset.forName("UTF-8")));
		processor.resolveArgument(paramRequestBodyString, mavContainer, webRequest, null);
	}

	// SPR-9942

	@Test(expected = HttpMessageNotReadableException.class)
	public void resolveArgumentRequiredNoContent() throws Exception {
		servletRequest.setContentType(MediaType.TEXT_PLAIN_VALUE);
		servletRequest.setContent(new byte[0]);
		given(messageConverter.canRead(String.class, MediaType.TEXT_PLAIN)).willReturn(true);
		given(messageConverter.read(eq(String.class), isA(HttpInputMessage.class))).willReturn(null);
		assertNull(processor.resolveArgument(paramRequestBodyString, mavContainer, webRequest, new ValidatingBinderFactory()));
	}

	@Test
	public void resolveArgumentNotRequiredNoContent() throws Exception {
		servletRequest.setContentType("text/plain");
		servletRequest.setContent(new byte[0]);
		given(messageConverter.canRead(String.class, MediaType.TEXT_PLAIN)).willReturn(true);
		assertNull(processor.resolveArgument(paramStringNotRequired, mavContainer, webRequest, new ValidatingBinderFactory()));
	}

	// SPR-13417
	@Test
	public void resolveArgumentNotRequiredNoContentNoContentType() throws Exception {
		servletRequest.setContent(new byte[0]);
		given(messageConverter.canRead(String.class, MediaType.TEXT_PLAIN)).willReturn(true);
		given(messageConverter.canRead(String.class, MediaType.APPLICATION_OCTET_STREAM)).willReturn(false);
		assertNull(processor.resolveArgument(paramStringNotRequired, mavContainer, webRequest, new ValidatingBinderFactory()));
	}

	@Test
	public void resolveArgumentNotGetRequests() throws Exception {
		servletRequest.setMethod("GET");
		servletRequest.setContent(new byte[0]);
		given(messageConverter.canRead(String.class, MediaType.APPLICATION_OCTET_STREAM)).willReturn(false);
		assertNull(processor.resolveArgument(paramStringNotRequired, mavContainer, webRequest, new ValidatingBinderFactory()));
	}

	@Test
	public void handleReturnValue() throws Exception {
		MediaType accepted = MediaType.TEXT_PLAIN;
		servletRequest.addHeader("Accept", accepted.toString());

		String body = "Foo";
		given(messageConverter.canWrite(String.class, null)).willReturn(true);
		given(messageConverter.getSupportedMediaTypes()).willReturn(Collections.singletonList(MediaType.TEXT_PLAIN));
		given(messageConverter.canWrite(String.class, accepted)).willReturn(true);

		processor.handleReturnValue(body, returnTypeString, mavContainer, webRequest);

		assertTrue("The requestHandled flag wasn't set", mavContainer.isRequestHandled());
		verify(messageConverter).write(eq(body), eq(accepted), isA(HttpOutputMessage.class));
	}

	@Test
	public void handleReturnValueProduces() throws Exception {
		String body = "Foo";

		servletRequest.addHeader("Accept", "text/*");
		servletRequest.setAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE, Collections.singleton(MediaType.TEXT_HTML));

		given(messageConverter.canWrite(String.class, MediaType.TEXT_HTML)).willReturn(true);

		processor.handleReturnValue(body, returnTypeStringProduces, mavContainer, webRequest);

		assertTrue(mavContainer.isRequestHandled());
		verify(messageConverter).write(eq(body), eq(MediaType.TEXT_HTML), isA(HttpOutputMessage.class));
	}


	@Test(expected = HttpMediaTypeNotAcceptableException.class)
	public void handleReturnValueNotAcceptable() throws Exception {
		MediaType accepted = MediaType.APPLICATION_ATOM_XML;
		servletRequest.addHeader("Accept", accepted.toString());

		given(messageConverter.canWrite(String.class, null)).willReturn(true);
		given(messageConverter.getSupportedMediaTypes()).willReturn(Arrays.asList(MediaType.TEXT_PLAIN));
		given(messageConverter.canWrite(String.class, accepted)).willReturn(false);

		processor.handleReturnValue("Foo", returnTypeString, mavContainer, webRequest);
	}

	@Test(expected = HttpMediaTypeNotAcceptableException.class)
	public void handleReturnValueNotAcceptableProduces() throws Exception {
		MediaType accepted = MediaType.TEXT_PLAIN;
		servletRequest.addHeader("Accept", accepted.toString());

		given(messageConverter.canWrite(String.class, null)).willReturn(true);
		given(messageConverter.getSupportedMediaTypes()).willReturn(Collections.singletonList(MediaType.TEXT_PLAIN));
		given(messageConverter.canWrite(String.class, accepted)).willReturn(false);

		processor.handleReturnValue("Foo", returnTypeStringProduces, mavContainer, webRequest);
	}

	// SPR-9841

	@Test
	public void handleReturnValueMediaTypeSuffix() throws Exception {
		String body = "Foo";
		MediaType accepted = MediaType.APPLICATION_XHTML_XML;
		List<MediaType> supported = Collections.singletonList(MediaType.valueOf("application/*+xml"));

		servletRequest.addHeader("Accept", accepted);

		given(messageConverter.canWrite(String.class, null)).willReturn(true);
		given(messageConverter.getSupportedMediaTypes()).willReturn(supported);
		given(messageConverter.canWrite(String.class, accepted)).willReturn(true);

		processor.handleReturnValue(body, returnTypeStringProduces, mavContainer, webRequest);

		assertTrue(mavContainer.isRequestHandled());
		verify(messageConverter).write(eq(body), eq(accepted), isA(HttpOutputMessage.class));
	}


	@SuppressWarnings("unused")
	@ResponseBody
	public String handle1(@RequestBody String s, int i) {
		return s;
	}

	@SuppressWarnings("unused")
	public int handle2() {
		return 42;
	}

	@SuppressWarnings("unused")
	@ResponseBody
	public String handle3() {
		return null;
	}

	@SuppressWarnings("unused")
	public void handle4(@Valid @RequestBody SimpleBean b) {
	}

	@SuppressWarnings("unused")
	public void handle5(@RequestBody(required=false) String s) {
	}

	private final class ValidatingBinderFactory implements WebDataBinderFactory {
		@Override
		public WebDataBinder createBinder(NativeWebRequest webRequest, Object target, String objectName) throws Exception {
			LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
			validator.afterPropertiesSet();
			WebDataBinder dataBinder = new WebDataBinder(target, objectName);
			dataBinder.setValidator(validator);
			return dataBinder;
		}
	}

	@SuppressWarnings("unused")
	private static class SimpleBean {

		@NotNull
		private final String name;

		public SimpleBean(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

}

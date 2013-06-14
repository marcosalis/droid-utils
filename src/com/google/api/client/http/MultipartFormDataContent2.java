/*
 * Copyright 2013 Marco Salis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.client.http;

import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import com.google.common.annotations.Beta;

/**
 * Serializes MIME "multipart/form-data" content as specified by <a
 * href="http://tools.ietf.org/html/rfc2388">RFC 2388: Returning Values from
 * Forms: multipart/form-data</a>
 * 
 * The implementation is a subclass of {@link MultipartContent} that sets the
 * media type to <code>"multipart/form-data"</code> and defaults to
 * {@link #DEFAULT_BOUNDARY} as a boundary string.
 * 
 * For a reference on how to build a multipart/form-data request see:
 * <ul>
 * <li>{@link http://chxo.com/be2/20050724_93bf.html}</li>
 * <li>{@link http://www.faqs.org/rfcs/rfc1867.html}</li>
 * </ul>
 * 
 * Specifications on the "content-disposition" (RFC 2183)<br>
 * {@link http://tools.ietf.org/html/rfc2183}
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@NotThreadSafe
public class MultipartFormDataContent2 extends MultipartContent {

	protected static final String DEFAULT_BOUNDARY = "0xKhTmLbOuNdArY";

	/**
	 * Factory method to create {@link HttpMediaType} with media type
	 * <code>"multipart/form-data"</code>
	 */
	protected static final HttpMediaType getMultipartFormDataMediaType() {
		return new HttpMediaType("multipart/form-data");
	}

	/**
	 * Creates a new empty {@link MultipartFormDataContent2}.
	 */
	public MultipartFormDataContent2() {
		final HttpMediaType mediaType = getMultipartFormDataMediaType();
		setMediaType(mediaType.setParameter("boundary", DEFAULT_BOUNDARY));
	}

	@Override
	public final MultipartFormDataContent2 setMediaType(HttpMediaType mediaType) {
		super.setMediaType(mediaType);
		return this;
	}

	@Override
	public MultipartFormDataContent2 addPart(Part part) {
		return (MultipartFormDataContent2) super.addPart(part);
	}

	/**
	 * Adds an HTTP multipart part with no headers.
	 */
	public MultipartFormDataContent2 addPart(HttpContent content) {
		return (MultipartFormDataContent2) super.addPart(new Part(content));
	}

	@Override
	public MultipartFormDataContent2 setParts(Collection<Part> parts) {
		return (MultipartFormDataContent2) super.setParts(parts);
	}

	@Override
	public MultipartFormDataContent2 setContentParts(Collection<? extends HttpContent> contentParts) {
		return (MultipartFormDataContent2) super.setContentParts(contentParts);
	}

	public void setContentDisposition(@Nullable String contentDisposition) {
		final Collection<Part> parts = getParts();

		for (@Nonnull
		Part part : parts) {
			HttpHeaders headers = new HttpHeaders().setAcceptEncoding(null);
			if (part.headers != null) {
				headers.fromHttpHeaders(part.headers);
			}
			headers.set("Content-Disposition", contentDisposition);
			part.setHeaders(headers);
		}
	}

	/**
	 * Sets the boundary string to use (must be not null)
	 * 
	 * If this is not called, the boundary defaults to {@link #DEFAULT_BOUNDARY}
	 * 
	 * @param boundary
	 *            The new boundary for the content
	 * @throws NullPointerException
	 *             if boundary is null
	 */
	@Override
	public MultipartFormDataContent2 setBoundary(@Nonnull String boundary) {
		return (MultipartFormDataContent2) super.setBoundary(boundary);
	}

}
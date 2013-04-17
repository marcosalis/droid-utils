/*
 * Copyright 2013 Luluvise Ltd
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
package com.github.luluvise.droid_utils.network;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

import com.google.api.client.http.AbstractHttpContent;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpMediaType;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.MultipartRelatedContent;
import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/**
 * Serializes MIME multipart/form-data content as specified by <a
 * href="http://tools.ietf.org/html/rfc2388">RFC 2388: Returning Values from
 * Forms: multipart/form-data</a>
 * 
 * Implementation customised from the {@link MultipartRelatedContent} class.<br>
 * <br>
 * For a reference on how to build a multipart/form-data request see:<br>
 * {@link http://chxo.com/be2/20050724_93bf.html}
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@NotThreadSafe
public class MultipartFormDataContent extends AbstractHttpContent {

	/**
	 * Collection of HTTP content parts.
	 * 
	 * <p>
	 * By default, it is an empty list.
	 * </p>
	 */
	private final Collection<HttpContent> parts;

	private static final byte[] CR_LF = "\r\n".getBytes();
	private static final byte[] CONTENT_DISP = "Content-Disposition: ".getBytes();
	private static final byte[] CONTENT_TYPE = "Content-Type: ".getBytes();
	private static final byte[] CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding: binary"
			.getBytes();
	private static final byte[] TWO_DASHES = "--".getBytes();
	private static final String BOUNDARY_STRING = "0xKhTmLbOuNdArY";
	private static final byte[] BOUNDARY = BOUNDARY_STRING.getBytes();

	private byte[] mContentDisposition;

	/**
	 * @param content
	 *            first HTTP content part
	 * @param otherParts
	 *            other HTTP content parts
	 */
	public MultipartFormDataContent(HttpContent firstPart, HttpContent... otherParts) {
		super(new HttpMediaType("multipart/form-data").setParameter("boundary", BOUNDARY_STRING));
		List<HttpContent> parts = new ArrayList<HttpContent>(otherParts.length + 1);
		parts.add(firstPart);
		parts.addAll(Arrays.asList(otherParts));
		this.parts = parts;
	}

	/**
	 * Sets this multi-part content as the content for the given HTTP request,
	 * set the {@link HttpHeaders#setMimeVersion(String) MIME version header} to
	 * {@code "1.0"} and the {@link HttpHeaders#setContentType(String)} to
	 * {@code "multipart/form-data"}.
	 * 
	 * @param request
	 *            HTTP request
	 */
	public void forRequest(HttpRequest request) {
		request.setContent(this);
		// request.getHeaders().setMimeVersion("1.0");
	}

	public void setContentDisposition(String contentDisposition) {
		mContentDisposition = contentDisposition.getBytes();
	}

	public void writeTo(OutputStream out) throws IOException {

		// do *NOT* put more than one CR_LF between lines
		out.write(TWO_DASHES);
		out.write(BOUNDARY);
		for (HttpContent part : parts) {
			// add custom headers
			if (mContentDisposition != null) {
				out.write(CR_LF);
				out.write(CONTENT_DISP);
				out.write(mContentDisposition);
			}
			String contentType = part.getType();
			if (contentType != null) {
				byte[] typeBytes = contentType.getBytes();
				out.write(CR_LF);
				out.write(CONTENT_TYPE);
				out.write(typeBytes);
			}
			out.write(CR_LF);
			if (!isTextBasedContentType(contentType)) {
				out.write(CONTENT_TRANSFER_ENCODING);
				out.write(CR_LF);
			}
			out.write(CR_LF);
			part.writeTo(out);
			out.write(CR_LF);
			out.write(TWO_DASHES);
			out.write(BOUNDARY);
		}
		out.write(TWO_DASHES);
		out.flush();
	}

	@Override
	public long computeLength() throws IOException {
		long result = TWO_DASHES.length * 2 + BOUNDARY.length;
		for (HttpContent part : parts) {
			long length = part.getLength();
			if (length < 0) {
				return -1;
			}
			if (mContentDisposition != null) {
				result += CR_LF.length + CONTENT_DISP.length + mContentDisposition.length;
			}
			String contentType = part.getType();
			if (contentType != null) {
				byte[] typeBytes = contentType.getBytes();
				result += CR_LF.length + CONTENT_TYPE.length + typeBytes.length;
			}
			if (!isTextBasedContentType(contentType)) {
				result += CONTENT_TRANSFER_ENCODING.length + CR_LF.length;
			}
			result += CR_LF.length * 3 + length + TWO_DASHES.length + BOUNDARY.length;
		}
		return result;
	}

	@Override
	public boolean retrySupported() {
		for (HttpContent onePart : parts) {
			if (!onePart.retrySupported()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public MultipartFormDataContent setMediaType(HttpMediaType mediaType) {
		super.setMediaType(mediaType);
		return this;
	}

	/**
	 * Returns the boundary string to use.
	 */
	public String getBoundary() {
		return BOUNDARY_STRING;
	}

	/**
	 * Sets the boundary string to use.
	 * 
	 * <p>
	 * Defaults to {@code "END_OF_PART"}.
	 * </p>
	 */
	public MultipartFormDataContent setBoundary(String boundary) {
		getMediaType().setParameter("boundary", Preconditions.checkNotNull(boundary));
		return this;
	}

	/**
	 * Returns the HTTP content parts.
	 */
	public Collection<HttpContent> getParts() {
		return Collections.unmodifiableCollection(parts);
	}

	/**
	 * Returns whether the given content type is text rather than binary data.
	 * 
	 * @param contentType
	 *            content type or {@code null}
	 * @return whether it is not {@code null} and text-based
	 */
	private static boolean isTextBasedContentType(String contentType) {
		if (contentType == null) {
			return false;
		}
		HttpMediaType hmt = new HttpMediaType(contentType);
		return hmt.getType().equals("text") || hmt.getType().equals("application");
	}
}

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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/**
 * Serializes MIME "multipart/form-data" content as specified by <a
 * href="http://tools.ietf.org/html/rfc2388">RFC 2388: Returning Values from
 * Forms: multipart/form-data</a>
 * 
 * Implementation customised from the {@link MultipartContent} class.<br>
 * <br>
 * For a reference on how to build a multipart/form-data request see:
 * <ul>
 * <li>{@link http://chxo.com/be2/20050724_93bf.html}</li>
 * <li>{@link http://www.faqs.org/rfcs/rfc1867.html}</li>
 * </ul>
 * 
 * Specifications on the "content-disposition" (RFC 2183)<br>
 * {@link http://tools.ietf.org/html/rfc2183}
 * 
 * The content media type is <code>"multipart/form-data"</code> and the boundary
 * string can be retrieved by calling {@link #getBoundary()} and set by calling
 * {@link #setBoundary(String)}.
 * 
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@NotThreadSafe
public class MultipartFormDataContent extends AbstractHttpContent {

	private static final byte[] CR_LF = "\r\n".getBytes();
	private static final byte[] CONTENT_DISP = "Content-Disposition: ".getBytes();
	private static final byte[] CONTENT_TYPE = "Content-Type: ".getBytes();
	private static final byte[] CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding: binary"
			.getBytes();
	private static final byte[] TWO_DASHES = "--".getBytes();

	protected static final String DEFAULT_BOUNDARY = "0xKhTmLbOuNdArY";

	/**
	 * Factory method to create {@link HttpMediaType} with media type
	 * <code>"multipart/form-data"</code>
	 */
	protected static final HttpMediaType getMultipartFormDataMediaType() {
		return new HttpMediaType("multipart/form-data");
	}

	/**
	 * Collection of HTTP content parts.
	 * 
	 * <p>
	 * By default, it is an empty list.
	 * </p>
	 */
	private final Collection<HttpContent> parts;

	private byte[] mContentDisposition;

	/**
	 * Creates a new {@link MultipartFormDataContent}.
	 * 
	 * @param content
	 *            first HTTP content part
	 * @param otherParts
	 *            other HTTP content parts
	 */
	public MultipartFormDataContent(HttpContent firstPart, HttpContent... otherParts) {
		super(getMultipartFormDataMediaType().setParameter("boundary", DEFAULT_BOUNDARY));
		final List<HttpContent> parts = new ArrayList<HttpContent>(otherParts.length + 1);
		parts.add(firstPart);
		parts.addAll(Arrays.asList(otherParts));
		this.parts = parts;
	}

	/**
	 * From RFC 2388:
	 * 
	 * <pre>
	 * "multipart/form-data" contains a series of parts. Each part is
	 *    expected to contain a content-disposition header [RFC 2183] where the
	 *    disposition type is "form-data", and where the disposition contains
	 *    an (additional) parameter of "name", where the value of that
	 *    parameter is the original field name in the form. For example, a part
	 *    might contain a header:
	 * 
	 *         Content-Disposition: form-data; name="user"
	 * 
	 *    with the value corresponding to the entry of the "user" field.
	 * 
	 *    Field names originally in non-ASCII character sets may be encoded
	 *    within the value of the "name" parameter using the standard method
	 *    described in RFC 2047.
	 * 
	 *    As with all multipart MIME types, each part has an optional
	 *    "Content-Type", which defaults to text/plain.  If the contents of a
	 *    file are returned via filling out a form, then the file input is
	 *    identified as the appropriate media type, if known, or
	 *    "application/octet-stream".  If multiple files are to be returned as
	 *    the result of a single form entry, they should be represented as a
	 *    "multipart/mixed" part embedded within the "multipart/form-data".
	 * 
	 *    Each part may be encoded and the "content-transfer-encoding" header
	 *    supplied if the value of that part does not conform to the default
	 *    encoding.
	 * </pre>
	 * 
	 * @param contentDisposition
	 */
	public void setContentDisposition(@Nonnull String contentDisposition) {
		mContentDisposition = contentDisposition.getBytes();
	}

	@Override
	public void writeTo(OutputStream out) throws IOException {
		final byte[] boundaryBytes = getBoundary().getBytes();
		// do *NOT* put more than one CR_LF between lines
		out.write(TWO_DASHES);
		out.write(boundaryBytes);
		for (HttpContent part : parts) {
			// add custom headers
			if (mContentDisposition != null) {
				// FIXME: content disposition is specific to each part
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
			out.write(boundaryBytes);
		}
		out.write(TWO_DASHES);
		out.flush();
	}

	@Override
	public long computeLength() throws IOException {
		final byte[] boundaryBytes = getBoundary().getBytes();
		long result = TWO_DASHES.length * 2 + boundaryBytes.length;
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
			result += CR_LF.length * 3 + length + TWO_DASHES.length + boundaryBytes.length;
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
	 * Returns the boundary string that the content uses.
	 */
	public final String getBoundary() {
		// media type is always not null here
		return getMediaType().getParameter("boundary");
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
	public final MultipartFormDataContent setBoundary(@Nonnull String boundary) {
		// media type is always not null here
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
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
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import com.google.api.client.http.MultipartContent.Part;
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
 * @deprecated Use {@link MultipartFormDataContent} instead
 * @since 1.0
 * @author Marco Salis
 */
@Beta
@Deprecated
@NotThreadSafe
public class MultipartFormDataContent2 extends AbstractHttpContent {

	/*
	 * Saving a static reference to byte arrays of constant strings to avoid
	 * calling the expensive getBytes() every time it's needed
	 */

	private static final String CR_LF_STR = "\r\n";
	private static final byte[] CR_LF = CR_LF_STR.getBytes();

	private static final String CONTENT_DISP_STR = "content-disposition: ";
	private static final byte[] CONTENT_DISP = CONTENT_DISP_STR.getBytes();

	private static final String CONTENT_TYPE_STR = "Content-Type: ";
	private static final byte[] CONTENT_TYPE = CONTENT_TYPE_STR.getBytes();

	private static final String TRANSFER_ENCODING_STR = "Content-Transfer-Encoding: binary";
	private static final byte[] TRANSFER_ENCODING = TRANSFER_ENCODING_STR.getBytes();

	private static final String TWO_DASHES_STR = "--";
	private static final byte[] TWO_DASHES = TWO_DASHES_STR.getBytes();

	protected static final String DEFAULT_BOUNDARY = "0xKhTmLbOuNdArY";

	/**
	 * Factory method to create {@link HttpMediaType} with media type
	 * <code>"multipart/form-data"</code>
	 */
	protected static final HttpMediaType getMultipartFormDataMediaType() {
		return new HttpMediaType("multipart/form-data");
	}

	/** Parts of the HTTP multipart request. */
	private ArrayList<Part> parts = new ArrayList<Part>();

	private String mContentDisposition;

	/**
	 * Creates a new {@link MultipartFormDataContent}.
	 * 
	 * @param content
	 *            first HTTP content part
	 * @param otherParts
	 *            other HTTP content parts
	 */
	public MultipartFormDataContent2() {
		super(getMultipartFormDataMediaType().setParameter("boundary", DEFAULT_BOUNDARY));
	}

	@Override
	public void writeTo(OutputStream out) throws IOException {
		final OutputStreamWriter writer = new OutputStreamWriter(out, getCharset());

		final String boundary = getBoundary();
		// do *NOT* put more than one CR_LF between lines
		writer.write(TWO_DASHES_STR);
		writer.write(boundary);
		// iterating over each http content part
		for (Part part : parts) {
			final HttpContent content = part.getContent();
			// add custom headers
			if (mContentDisposition != null) {
				// FIXME: content disposition is specific to each part
				writer.write(CR_LF_STR);
				writer.write(CONTENT_DISP_STR);
				writer.write(mContentDisposition);
			}
			String contentType = content.getType();
			if (contentType != null) {
				writer.write(CR_LF_STR);
				writer.write(CONTENT_TYPE_STR);
				writer.write(contentType);
			}
			writer.write(CR_LF_STR);
			if (!isTextBasedContentType(contentType)) {
				writer.write(TRANSFER_ENCODING_STR);
				writer.write(CR_LF_STR);
			}
			writer.write(CR_LF_STR);
			writer.flush(); // flush before writing content
			content.writeTo(out);
			writer.write(CR_LF_STR);
			writer.write(TWO_DASHES_STR);
			writer.write(boundary);
		}
		writer.write(TWO_DASHES_STR);
		writer.flush(); // flush before returning
	}

	@Override
	public long computeLength() throws IOException {
		final byte[] boundaryBytes = getBoundary().getBytes();
		long result = TWO_DASHES.length * 2 + boundaryBytes.length;
		for (Part part : parts) {
			final HttpContent content = part.getContent();
			long length = content.getLength();
			if (length < 0) {
				return -1;
			}
			if (mContentDisposition != null) {
				result += CR_LF.length + CONTENT_DISP.length
						+ mContentDisposition.getBytes().length;
			}
			String contentType = content.getType();
			if (contentType != null) {
				byte[] typeBytes = contentType.getBytes();
				result += CR_LF.length + CONTENT_TYPE.length + typeBytes.length;
			}
			if (!isTextBasedContentType(contentType)) {
				result += TRANSFER_ENCODING.length + CR_LF.length;
			}
			result += CR_LF.length * 3 + length + TWO_DASHES.length + boundaryBytes.length;
		}
		return result;
	}

	@Override
	public boolean retrySupported() {
		for (Part part : parts) {
			if (!part.content.retrySupported()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public MultipartFormDataContent2 setMediaType(HttpMediaType mediaType) {
		super.setMediaType(mediaType);
		return this;
	}

	/** Returns an unmodifiable view of the parts of the HTTP multipart request. */
	public final Collection<Part> getParts() {
		return Collections.unmodifiableCollection(parts);
	}

	/**
	 * Adds an HTTP multipart part.
	 */
	public MultipartFormDataContent2 addPart(Part part) {
		parts.add(Preconditions.checkNotNull(part));
		return this;
	}

	/**
	 * Adds an {@link HttpContent} with no headers as a new {@link Part}.
	 */
	public MultipartFormDataContent2 addPart(HttpContent content) {
		parts.add(new Part(Preconditions.checkNotNull(content)));
		return this;
	}

	/**
	 * Sets the parts of the HTTP multipart request.
	 * 
	 * <p>
	 * Overriding is only supported for the purpose of calling the super
	 * implementation and changing the return type, but nothing else.
	 * </p>
	 */
	public MultipartFormDataContent2 setParts(Collection<Part> parts) {
		this.parts = new ArrayList<Part>(parts);
		return this;
	}

	/**
	 * Sets the HTTP content parts of the HTTP multipart request, where each
	 * part is assumed to have no HTTP headers and no encoding.
	 * 
	 * <p>
	 * Overriding is only supported for the purpose of calling the super
	 * implementation and changing the return type, but nothing else.
	 * </p>
	 */
	public MultipartFormDataContent2 setContentParts(Collection<? extends HttpContent> contentParts) {
		this.parts = new ArrayList<Part>(contentParts.size());
		for (HttpContent contentPart : contentParts) {
			addPart(new Part(contentPart));
		}
		return this;
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
	public void setContentDisposition(@Nullable String contentDisposition) {
		mContentDisposition = contentDisposition;
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
	public final MultipartFormDataContent2 setBoundary(@Nonnull String boundary) {
		// media type is always not null here
		getMediaType().setParameter("boundary", Preconditions.checkNotNull(boundary));
		return this;
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
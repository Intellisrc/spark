/*
 * Copyright 2011- Per Wendel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package spark;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Provides functionality for modifying the response
 *
 * @author Per Wendel
 */
public class Response {

    /**
     * The logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Response.class);

    public enum Compression {
        // Compression should not be handled in any way by Spark
        NONE,
        // It will request Spark to compress depending on browser support (BROTLI if not, GZIP)
        AUTO,
        // It will request Spark to compress the output (gzip) and set the GZIP header
        GZIP_COMPRESS,
        // It will notify Spark that the output is already GZIP compressed.
        // In such case, Spark won't compress the output and will ensure the GZIP header is set.
        GZIP_COMPRESSED,
        // It will request Spark to compress the output (brotli) and set the BROTLI header
        // NOTE: Brotli is a successor to gzip, it is supported by all major web browsers. It provides better compression than gzip.
        // Brotli Library is included with `com.aayushatharva.brotli4j` library
        BROTLI_COMPRESS,
        // It will notify Spark that the output is already BROTLI compressed.
        // In such case, Spark won't compress the output and will ensure the BROTLI header is set.
        BROTLI_COMPRESSED;

        /**
         * @return true if output is reported to be already compressed
         */
        public boolean isCompressed() {
            return this == GZIP_COMPRESSED || this == BROTLI_COMPRESSED;
        }

        /**
         * @return true if Spark is notified to compress output
         */
        public boolean toCompress() {
            return this == GZIP_COMPRESS || this == BROTLI_COMPRESS;
        }
    }

    private HttpServletResponse response;
    private String body;

    /**
     * Notify Spark how compression should be handled
     */
    public Compression compression = Compression.NONE;

    protected Response() {
        // Used by wrapper
    }

    Response(HttpServletResponse response) {
        this.response = response;
    }


    /**
     * Sets the status code for the
     *
     * @param statusCode the status code
     */
    public void status(int statusCode) {
        response.setStatus(statusCode);
    }

    /**
     * Returns the status code
     *
     * @return the status code
     */
    public int status() {
        return response.getStatus();
    }

    /**
     * Sets the content type for the response
     *
     * @param contentType the content type
     */
    public void type(String contentType) {
        response.setContentType(contentType);
    }

    /**
     * Returns the content type
     *
     * @return the content type
     */
    public String type() {
        return response.getContentType();
    }

    /**
     * Sets the body
     *
     * @param body the body
     */
    public void body(String body) {
        this.body = body;
    }

    /**
     * returns the body
     *
     * @return the body
     */
    public String body() {
        return this.body;
    }

    /**
     * @return the raw response object handed in by Jetty
     */
    public HttpServletResponse raw() {
        return response;
    }

    /**
     * Trigger a browser redirect
     *
     * @param location Where to redirect
     */
    public void redirect(String location) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Redirecting ({} {} to {}", "Found", HttpServletResponse.SC_FOUND, location);
        }
        try {
            response.sendRedirect(location);
        } catch (IOException ioException) {
            LOG.warn("Redirect failure", ioException);
        }
    }

    /**
     * Trigger a browser redirect with specific http 3XX status code.
     *
     * @param location       Where to redirect permanently
     * @param httpStatusCode the http status code
     */
    public void redirect(String location, int httpStatusCode) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Redirecting ({} to {}", httpStatusCode, location);
        }
        response.setStatus(httpStatusCode);
        response.setHeader("Location", location);
        response.setHeader("Connection", "close");
        try {
            response.sendError(httpStatusCode);
        } catch (IOException e) {
            LOG.warn("Exception when trying to redirect permanently", e);
        }
    }

    /**
     * Adds/Sets a response header
     *
     * @param header the header
     * @param value  the value
     */
    public void header(String header, String value) {
        response.addHeader(header, value);
    }

    /**
     * Adds/Sets a response header
     *
     * @param header the header
     * @param value  the value
     */
    public void header(String header, int value) {
        response.addIntHeader(header, value);
    }

    /**
     * Adds/Sets a response header
     *
     * @param header the header
     * @param value  the value
     */
    public void header(String header, Date value) {
        response.addDateHeader(header, value.getTime());
    }

    /**
     * Adds/Sets a response header
     *
     * @param header the header
     * @param value  the value
     */
    public void header(String header, java.sql.Date value) {
        response.addDateHeader(header, value.getTime());
    }

    /**
     * Adds/Sets a response header
     *
     * @param header the header
     * @param value  the value
     */
    public void header(String header, Instant value) {
        response.addDateHeader(header, value.toEpochMilli());
    }

    /**
     * Adds not persistent cookie to the response.
     * Can be invoked multiple times to insert more than one cookie.
     *
     * @param name  name of the cookie
     * @param value value of the cookie
     */
    public void cookie(String name, String value) {
        cookie(name, value, -1, false);
    }

    /**
     * Adds cookie to the response. Can be invoked multiple times to insert more than one cookie.
     *
     * @param name   name of the cookie
     * @param value  value of the cookie
     * @param maxAge max age of the cookie in seconds (negative for the not persistent cookie,
     *               zero - deletes the cookie)
     */
    public void cookie(String name, String value, int maxAge) {
        cookie(name, value, maxAge, false);
    }

    /**
     * Adds cookie to the response. Can be invoked multiple times to insert more than one cookie.
     *
     * @param name    name of the cookie
     * @param value   value of the cookie
     * @param maxAge  max age of the cookie in seconds (negative for the not persistent cookie, zero - deletes the cookie)
     * @param secured if true : cookie will be secured
     */
    public void cookie(String name, String value, int maxAge, boolean secured) {
        cookie(name, value, maxAge, secured, false);
    }

    /**
     * Adds cookie to the response. Can be invoked multiple times to insert more than one cookie.
     *
     * @param name     name of the cookie
     * @param value    value of the cookie
     * @param maxAge   max age of the cookie in seconds (negative for the not persistent cookie, zero - deletes the cookie)
     * @param secured  if true : cookie will be secured
     * @param httpOnly if true: cookie will be marked as http only
     */
    public void cookie(String name, String value, int maxAge, boolean secured, boolean httpOnly) {
        cookie("", "", name, value, maxAge, secured, httpOnly);
    }

    /**
     * Adds cookie to the response. Can be invoked multiple times to insert more than one cookie.
     *
     * @param path    path of the cookie
     * @param name    name of the cookie
     * @param value   value of the cookie
     * @param maxAge  max age of the cookie in seconds (negative for the not persistent cookie, zero - deletes the cookie)
     * @param secured if true : cookie will be secured
     */
    public void cookie(String path, String name, String value, int maxAge, boolean secured) {
        cookie("", path, name, value, maxAge, secured, false);
    }

    /**
     * Adds cookie to the response. Can be invoked multiple times to insert more than one cookie.
     *
     * @param path     path of the cookie
     * @param name     name of the cookie
     * @param value    value of the cookie
     * @param maxAge   max age of the cookie in seconds (negative for the not persistent cookie, zero - deletes the cookie)
     * @param secured  if true : cookie will be secured
     * @param httpOnly if true: cookie will be marked as http only
     */
    public void cookie(String path, String name, String value, int maxAge, boolean secured, boolean httpOnly) {
        cookie("", path, name, value, maxAge, secured, httpOnly);
    }

    /**
     * Adds cookie to the response. Can be invoked multiple times to insert more than one cookie.
     *
     * @param domain   domain of the cookie
     * @param path     path of the cookie
     * @param name     name of the cookie
     * @param value    value of the cookie
     * @param maxAge   max age of the cookie in seconds (negative for the not persistent cookie, zero - deletes the cookie)
     * @param secured  if true : cookie will be secured
     * @param httpOnly if true: cookie will be marked as http only
     */
    public void cookie(String domain, String path, String name, String value, int maxAge, boolean secured, boolean httpOnly) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath(path);
        cookie.setDomain(domain);
        cookie.setMaxAge(maxAge);
        cookie.setSecure(secured);
        cookie.setHttpOnly(httpOnly);
        response.addCookie(cookie);
    }

    /**
     * Removes the cookie.
     *
     * @param name name of the cookie
     */
    public void removeCookie(String name) {
        removeCookie(null, name);
    }

    /**
     * Removes the cookie with given path and name.
     *
     * @param path path of the cookie
     * @param name name of the cookie
     */
    public void removeCookie(String path, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath(path);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}

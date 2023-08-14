/*
 * Copyright 2015 - Per Wendel
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
package spark.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import spark.Response;

import static spark.Response.Compression.AUTO;
import static spark.Response.Compression.BROTLI_COMPRESS;
import static spark.Response.Compression.GZIP_COMPRESS;
import static spark.Response.Compression.NONE;

/**
 * GZIP utility class.
 *
 * @author Edward Raff
 * @author Per Wendel
 */
public class CompressUtil {
    private static final Logger LOG = LoggerFactory.getLogger(CompressUtil.class);
    private static final String ACCEPT_ENCODING = "Accept-Encoding";
    private static final String CONTENT_ENCODING = "Content-Encoding";

    private static final String GZIP = "gzip";
    private static final String BROTLI = "br";

    private static final boolean IS_BROTLI_AVAILABLE;
    static {
        boolean available = false;
        try {
            Class<?> brotli = Class.forName("com.aayushatharva.brotli4j.Brotli4jLoader");
            available = (Boolean) brotli.getMethod("isAvailable").invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                 InvocationTargetException e) {
            LOG.debug("Brotli was not found: {} Cause: {}", e.getMessage(), e.getCause());
        }
        IS_BROTLI_AVAILABLE = available;
    }
    // Hide constructor
    private CompressUtil() {}

    /**
     * Class used to be able to specify compression level
     */
    protected static class SparkGZIPOutputStream extends GZIPOutputStream {
        public SparkGZIPOutputStream(OutputStream out, Float quality) throws IOException {
            super(out, true);
            int q = Math.round(quality * 10);
            if(q <= 0) {
                q = 9;
            }
            if(q > 9) {
                q = 9;
            }
            def.setLevel(q);
        }
    }

    /**
     * 'checkAndWrap' with default compression to AUTO
     * @param httpRequest        the HTTP servlet request.
     * @param httpResponse       the HTTP servlet response.
     * @return output stream (either compressed or not).
     * @throws IOException in case of IO error.
     */
    public static OutputStream checkAndWrap(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
        return checkAndWrap(httpRequest, httpResponse, AUTO);
    }
    /**
     * Checks if the HTTP request/response accepts and wants to compress outputStream and i that case wraps the response output stream in a
     * {@link java.util.zip.GZIPOutputStream} or 'com.aayushatharva.brotli4j.encoder.BrotliOutputStream'.
     *
     * @param httpRequest        the HTTP servlet request.
     * @param httpResponse       the HTTP servlet response.
     * @param compression        how Spark should handle compression
     * @return output stream (either compressed or not).
     * @throws IOException in case of IO error.
     */
    public static OutputStream checkAndWrap(HttpServletRequest httpRequest,
                                            HttpServletResponse httpResponse,
                                            Response.Compression compression) throws
                                                                        IOException {
        OutputStream responseStream = httpResponse.getOutputStream();

        ArrayList<String> acceptsList    = Collections.list(httpRequest.getHeaders(ACCEPT_ENCODING));
        ArrayList<String> encodingHeader = new ArrayList<>(httpResponse.getHeaders(CONTENT_ENCODING));
        if(acceptsList.isEmpty()) {
            if(encodingHeader.contains(GZIP) || encodingHeader.contains(BROTLI)) {
                httpResponse.setHeader(CONTENT_ENCODING, ""); //TODO: check if this works
            }
        } else {
            HashMap<String, Float> accepts = new HashMap<>();
            for (final String element : acceptsList) {
                for (final String part : element.split(",")) {
                    Matcher matcher = Pattern.compile("^([^;]+)(;q=(.*))?$").matcher(part);
                    if (matcher.find()) {
                        String algo = matcher.group(1);
                        if (!algo.equals("*")) {
                            accepts.put(algo.trim(), matcher.group(3) != null ? Float.parseFloat(matcher.group(3)) : 1f);
                        }
                    }
                }
            }

            if(compression == AUTO) {
                if(accepts.containsKey(BROTLI) && isBrotliAvailable()) {
                    compression = BROTLI_COMPRESS;
                } else if(accepts.containsKey(GZIP)) {
                    compression = GZIP_COMPRESS;
                } else { //Unknown
                    compression = NONE;
                    if(encodingHeader.contains(GZIP) || encodingHeader.contains(BROTLI)) {
                        httpResponse.setHeader(CONTENT_ENCODING, ""); //TODO: check if this works
                    }
                }
            }
            switch (compression) {
                case GZIP_COMPRESS:
                    if(accepts.containsKey(GZIP)) {
                        responseStream = new SparkGZIPOutputStream(responseStream, accepts.get(GZIP));
                    }
                case GZIP_COMPRESSED:
                    if(!encodingHeader.contains(GZIP) && accepts.containsKey(GZIP)) {
                        addContentEncodingHeader(httpResponse, GZIP);
                    }
                    break;
                case BROTLI_COMPRESS:
                    if(isBrotliAvailable()) {
                        if (accepts.containsKey(BROTLI)) {
                            int q = Math.round(accepts.get(BROTLI) * 10);
                            try {
                                responseStream = getBrotliOutputStream(responseStream, q);
                                addContentEncodingHeader(httpResponse, BROTLI); //Only add if succeeds
                            } catch(RuntimeException ignore) {}
                        }
                    }
                case BROTLI_COMPRESSED:
                    if(isBrotliAvailable()) {
                        if (!encodingHeader.contains(BROTLI) && accepts.containsKey(BROTLI)) {
                            addContentEncodingHeader(httpResponse, BROTLI);
                        }
                    }
                    break;
            }
        }
        return responseStream;
    }

    /**
     * Add GZIP header if it doesn't exist
     * @param response HTTP Response
     * @param algo Compression Algorithm
     */
    private static void addContentEncodingHeader(HttpServletResponse response, String algo) {
        response.setHeader(CONTENT_ENCODING, algo);
    }

    /**
     * As Brotli library is optional, it might not exist. Be sure it exist before calling it.
     * @return true if brotli is available
     */
    public static boolean isBrotliAvailable() {
        return IS_BROTLI_AVAILABLE;
    }

    /**
     * As Brotli library is optional, it might not exist.
     * We are using reflection to access Brotli classes if they exist.
     * @param response content to be encoded/compressed
     * @param q compression quality (0-11) 11 = Max compression
     * @return compressed content
     * @throws RuntimeException In case it was unable to compress
     */
    private static OutputStream getBrotliOutputStream(OutputStream response, int q) throws RuntimeException {
        OutputStream responseStream;
        if (q <= 0) q = 10;
        if (q > 11) q = 11;
        try {
            Class<?> brotliOutputStream = Class.forName("com.aayushatharva.brotli4j.encoder.BrotliOutputStream");
            Class<?> encoderParams = Class.forName("com.aayushatharva.brotli4j.encoder.Encoder$Parameters");
            Object encoderParamsInstance = encoderParams.getDeclaredConstructor().newInstance();
            Object params = encoderParams.getMethod("setQuality", int.class).invoke(encoderParamsInstance, q);
            responseStream = (OutputStream) brotliOutputStream.getConstructor(OutputStream.class, encoderParams)
                .newInstance(response, encoderParams.cast(params));
        } catch (ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException |
                 NoSuchMethodException e) {
            LOG.debug("Unable to compress with Brotli: {} Cause: {}", e.getMessage(), e.getCause());
            throw new RuntimeException(e);
        }
        return responseStream;
    }
}

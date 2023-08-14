package spark.examples.brotli;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;

import spark.utils.IOUtils;

import com.aayushatharva.brotli4j.decoder.BrotliInputStream;

/**
 * Created by A.Lepe (2022-07-27) based on GzipClient
 */
public class BrotliClient {

    public static String getAndDecompress(String url) throws Exception {
        InputStream compressed = get(url);
        BrotliInputStream brotliInputStream = new BrotliInputStream(compressed);
        return IOUtils.toString(brotliInputStream); //decompressed
    }

    public static InputStream get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.addRequestProperty("Accept-Encoding", "gzip;q=1,br;q=0.3"); //As "br" is included, we take that as priority
        connection.connect();
        return connection.getInputStream();
    }

}

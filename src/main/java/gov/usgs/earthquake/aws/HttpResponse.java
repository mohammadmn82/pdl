package gov.usgs.earthquake.aws;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import javax.json.Json;
import javax.json.JsonObject;

import gov.usgs.util.StreamUtils;

/**
 * Utility class to hold HttpURLConnection and parse JSON response data.
 */
public class HttpResponse {
  /** Variable to hold HttpURLConnection */
  public final HttpURLConnection connection;
  /** Variable for holding IOExceptions */
  public final IOException readException;
  /** Varialbe to hold URL response */
  public final byte[] response;

  /**
   * Reads response from HttpUrlConnection.
   * @param connection HttpURLConnection to read
   * @throws Exception exception if errors
   */
  public HttpResponse(final HttpURLConnection connection) throws Exception {
    this.connection = connection;
    IOException exception = null;
    byte[] data = null;
    try (final InputStream in = connection.getInputStream()) {
      data = StreamUtils.readStream(in);
    } catch (IOException e) {
      exception = e;
      try (final InputStream err = connection.getErrorStream()) {
        data = StreamUtils.readStream(err);
      } catch (IOException e2) {
        // ignore
      }
    } finally {
      this.response = data;
      this.readException = exception;
    }
  }

  /**
   * Parse response into JsonObject.
   *
   * @return parsed JsonObject
   * @throws Exception if unable to parse.
   */
  public JsonObject getJsonObject() throws Exception {
    return Json.createReader(new ByteArrayInputStream(response)).readObject();
  }
}
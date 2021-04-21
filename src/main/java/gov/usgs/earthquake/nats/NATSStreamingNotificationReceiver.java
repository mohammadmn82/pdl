package gov.usgs.earthquake.nats;

import gov.usgs.earthquake.distribution.ConfigurationException;
import gov.usgs.earthquake.distribution.DefaultNotificationReceiver;
import gov.usgs.earthquake.distribution.URLNotification;
import gov.usgs.earthquake.distribution.URLNotificationJSONConverter;
import gov.usgs.util.Config;
import gov.usgs.util.FileUtils;
import io.nats.streaming.Message;
import io.nats.streaming.MessageHandler;
import io.nats.streaming.Subscription;
import io.nats.streaming.SubscriptionOptions;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connects directly to a NATS streaming server to receive notifications using a NATSClient
 */
public class NATSStreamingNotificationReceiver extends DefaultNotificationReceiver implements MessageHandler {

  private static final Logger LOGGER = Logger
          .getLogger(DefaultNotificationReceiver.class.getName());

  /** Property for tracking file name */
  public static String TRACKING_FILE_NAME_PROPERTY = "trackingFile";
  /** Property on if update sequence should occur after exception */
  public static String UPDATE_SEQUENCE_AFTER_EXCEPTION_PROPERTY = "updateSequenceAfterException";
  /** Property for sequence */
  public static String SEQUENCE_PROPERTY = "sequence";

  /** Name of deafult tracking file */
  public static String DEFAULT_TRACKING_FILE_NAME_PROPERTY = "data/STANReceiverInfo.json";
  /** Default state of update after exception */
  public static String DEFAULT_UPDATE_SEQUENCE_AFTER_EXCEPTION_PROPERTY = "true";

  private NATSClient client = new NATSClient();
  private Subscription subscription;

  private String subject;
  private long sequence = 0;
  private String trackingFileName;
  private boolean updateSequenceAfterException;
  private boolean exceptionThrown = false;

  /**
   * Configures receiver based on included properties
   *
   * @param config
   *            The user-defined configuration
   *
   * @throws Exception If required properties are ignored
   */
  @Override
  public void configure(Config config) throws Exception {
    super.configure(config);
    client.configure(config);

    subject = config.getProperty(NATSClient.SUBJECT_PROPERTY);
    if (subject == null) {
      throw new ConfigurationException(NATSClient.SUBJECT_PROPERTY + " is a required parameter");
    }

    trackingFileName = config.getProperty(TRACKING_FILE_NAME_PROPERTY, DEFAULT_TRACKING_FILE_NAME_PROPERTY);
    updateSequenceAfterException = Boolean.parseBoolean(config.getProperty(
      UPDATE_SEQUENCE_AFTER_EXCEPTION_PROPERTY,
      DEFAULT_UPDATE_SEQUENCE_AFTER_EXCEPTION_PROPERTY));
  }

  /**
   * Does initial tracking file management and subscribes to server
   * With a tracking file, gets the last sequence
   *
   * @throws InterruptedException if interrupted
   * @throws IOException if IO error occurs
   */
  @Override
  public void startup() throws Exception {
    super.startup();

    //Start client
    client.startup();

    //Check properties if tracking file exists
    JsonObject properties = readTrackingFile();
    if (properties != null &&
        properties.getString(NATSClient.SERVER_HOST_PROPERTY).equals(client.getServerHost()) &&
        properties.getString(NATSClient.SERVER_PORT_PROPERTY).equals(client.getServerPort()) &&
        properties.getString(NATSClient.CLUSTER_ID_PROPERTY).equals(client.getClusterId()) &&
        properties.getString(NATSClient.CLIENT_ID_PROPERTY).equals(client.getClientId()) &&
        properties.getString(NATSClient.SUBJECT_PROPERTY).equals(subject)) {
      sequence = Long.parseLong(properties.get(SEQUENCE_PROPERTY).toString());
    }

    subscription = client.getConnection().subscribe(
      subject,
      this,
      new SubscriptionOptions.Builder().startAtSequence(sequence).build());
    // Always starts at stored sequence; initialized to 0 and overwritten by storage
  }

  /**
   * Closes subscription/connection and writes state in tracking file
   * Wraps each statement in a try/catch to ensure each step still happens
   *
   * @throws IOException if IO error occurs
   * @throws InterruptedException if interrupted
   * @throws TimeoutException if timeout
   */
  @Override
  public void shutdown() throws Exception {
    try {
      writeTrackingFile();
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "[" + getName() + "] failed to write to tracking file");
    }
    try {
      subscription.unsubscribe();
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "[" + getName() + "] failed to unsubscribe from NATS channel");
    }
    subscription = null;
    client.shutdown();
    super.shutdown();
  }

  /**
   * Writes pertinent configuration information to tracking file
   * @throws Exception if error occurs
   */
  public void writeTrackingFile() throws Exception {
    JsonObject json = Json.createObjectBuilder()
      .add(NATSClient.SERVER_HOST_PROPERTY,client.getServerHost())
      .add(NATSClient.SERVER_PORT_PROPERTY,client.getServerPort())
      .add(NATSClient.CLUSTER_ID_PROPERTY,client.getClusterId())
      .add(NATSClient.CLIENT_ID_PROPERTY,client.getClientId())
      .add(NATSClient.SUBJECT_PROPERTY,subject)
      .add(SEQUENCE_PROPERTY,sequence)
    .build();

    FileUtils.writeFileThenMove(
      new File(trackingFileName + "_tmp"),
      new File(trackingFileName),
      json.toString().getBytes());
  }

  /**
   * Reads contents of tracking file
   *
   * @return JsonObject containing tracking file contents, or null if file doesn't exist
   * @throws Exception if error occurs
   */
  public JsonObject readTrackingFile() throws Exception {
    JsonObject json = null;

    File trackingFile = new File(trackingFileName);
    if (trackingFile.exists()) {
      InputStream contents = new ByteArrayInputStream(FileUtils.readFile(trackingFile));
      JsonReader jsonReader = Json.createReader(contents);
      json = jsonReader.readObject();
      jsonReader.close();
    }
    return json;
  }

  /**
   * Defines behavior for message receipt. Attempts to process notifications, with configurable behavior
   * for exception handling
   *
   * @param message
   *            The message received from the STAN server
   */
  @Override
  public void onMessage(Message message) {
    try {
      // parse message, send to listeners
      URLNotification notification = URLNotificationJSONConverter.parseJSON(new ByteArrayInputStream(message.getData()));
      receiveNotification(notification);
      // update sequence and tracking file if exception not thrown or we still want to update sequence anyway
      if (!exceptionThrown || updateSequenceAfterException) {
        sequence = message.getSequence();
        writeTrackingFile();
      }
    } catch (Exception e) {
      exceptionThrown = true;
      LOGGER.log(Level.WARNING,
        "[" + getName() + "] exception handling NATSStreaming message." +
        (!updateSequenceAfterException ? " Will no longer update sequence; restart PDL to reprocess.":"") +
        " Stack Trace: " + e);
      LOGGER.log(Level.FINE, "[" + getName() + "] Message: " + message.getData());
    }
  }

  /** @return trackingFileName */
  public String getTrackingFileName() {
    return trackingFileName;
  }

  /** @param trackingFileName to set */
  public void setTrackingFileName(String trackingFileName) {
    this.trackingFileName = trackingFileName;
  }

  /** @return NATSClient */
  public NATSClient getClient() {
    return client;
  }

  /** @param client NATSClient to set */
  public void setClient(NATSClient client) {
    this.client = client;
  }

  /** @return subject */
  public String getSubject() {
    return subject;
  }

  /** @param subject to set */
  public void setSubject(String subject) {
    this.subject = subject;
  }

}

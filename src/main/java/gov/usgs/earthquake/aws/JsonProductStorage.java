package gov.usgs.earthquake.aws;

import java.io.ByteArrayInputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;

import gov.usgs.earthquake.distribution.ProductAlreadyInStorageException;
import gov.usgs.earthquake.distribution.ProductStorage;
import gov.usgs.earthquake.distribution.StorageEvent;
import gov.usgs.earthquake.distribution.StorageListener;
import gov.usgs.earthquake.product.Product;
import gov.usgs.earthquake.product.ProductId;
import gov.usgs.earthquake.product.io.JsonProduct;
import gov.usgs.earthquake.product.io.ObjectProductHandler;
import gov.usgs.earthquake.product.io.ObjectProductSource;
import gov.usgs.earthquake.product.io.ProductSource;
import gov.usgs.earthquake.util.JDBCConnection;
import gov.usgs.util.Config;

/**
 * Store Products in a database.
 *
 * Note that this storage does not store Product Content, and is intended for
 * Products that use URLContent and can be serialized using JsonProduct.
 *
 * Only SQLITE or local development should rely on createSchema.
 * Products (data column) have exceeded 64kb, plan accordingly.
 *
 * Mysql Schema Example:<br>
 * <pre>
 * CREATE TABLE IF NOT EXISTS indexer_storage
 * (id INTEGER PRIMARY KEY AUTO_INCREMENT
 * , source VARCHAR(255)
 * , type VARCHAR(255)
 * , code VARCHAR(255)
 * , updatetime BIGINT
 * , data LONGTEXT
 * , UNIQUE KEY product_index (source, type, code, updatetime)
 * ) ENGINE=innodb CHARSET=utf8;
 * </pre>
 */
public class JsonProductStorage extends JDBCConnection implements ProductStorage {

  private static final Logger LOGGER = Logger.getLogger(
      JsonProductStorage.class.getName());

  /** Variable for the default driver */
  public static final String DEFAULT_DRIVER = "org.sqlite.JDBC";
  /** Variable for the default table */
  public static final String DEFAULT_TABLE = "product";
  /** Variable for the default URL */
  public static final String DEFAULT_URL = "jdbc:sqlite:json_product_index.db";

  /** Database table name. */
  private String table;

  /**
   * Create a JsonProductStorage using defaults.
   */
  public JsonProductStorage() {
    this(DEFAULT_DRIVER, DEFAULT_URL);
  }

  /**
   * Create a JsonProductStorage with a default table.
   * @param driver Driver to use
   * @param url URL to use
   */
  public JsonProductStorage(final String driver, final String url) {
    this(driver, url, DEFAULT_TABLE);
  }

  /**
   * Create a JsonProductStorage with a custom driver, url, and table.
   * @param driver Driver to use
   * @param url URL to use
   * @param table Table to use
   */
  public JsonProductStorage(
      final String driver, final String url, final String table) {
    super(driver, url);
    this.table = table;
  }

  /** @return table */
  public String getTable() { return this.table; }
  /** @param table Table to set */
  public void setTable(final String table) { this.table = table; }

  @Override
  public void configure(final Config config) throws Exception {
    super.configure(config);
    if (getDriver() == null) { setDriver(DEFAULT_DRIVER); }
    if (getUrl() == null) { setUrl(DEFAULT_URL); }

    setTable(config.getProperty("table", DEFAULT_TABLE));
    LOGGER.config("[" + getName() + "] driver=" + getDriver());
    LOGGER.config("[" + getName() + "] table=" + getTable());
    // do not log url, it may contain user/pass
  }

  /**
   * After normal startup, check whether schema exists and attempt to create.
   * @throws Exception if error occurs
   */
  @Override
  public void startup() throws Exception {
    super.startup();
    // make sure schema exists
    if (!schemaExists()) {
      LOGGER.warning("[" + getName() + "] schema not found, creating");
      createSchema();
    }
  }

  /**
   * Check whether schema exists.
   *
   * @return boolean
   * @throws Exception if error occurs
   */
  public boolean schemaExists() throws Exception {
    final String sql = "select * from " + this.table + " limit 1";
    beginTransaction();
    try (final PreparedStatement test = getConnection().prepareStatement(sql)) {
      test.setQueryTimeout(60);
      // should throw exception if table does not exist
      try (final ResultSet rs = test.executeQuery()) {
        rs.next();
      }
      commitTransaction();
      // schema exists
      return true;
    } catch (Exception e) {
      rollbackTransaction();
      return false;
    }
  }

  /**
   * Attempt to create schema.
   *
   * Only supports sqlite or mysql.  When not using sqlite, relying on this
   * method is only recommended for local development.
   *
   * @throws Exception if error occurs
   */
  public void createSchema() throws Exception {
    // create schema
    beginTransaction();
    try (final Statement statement = getConnection().createStatement()) {
      String autoIncrement = "";
      String engine = "";
      if (getDriver().contains("mysql")) {
        autoIncrement = " AUTO_INCREMENT";
        engine = " ENGINE=innodb CHARSET=utf8";
      }
      statement.executeUpdate(
          "CREATE TABLE " + this.table
          + " (id INTEGER PRIMARY KEY" + autoIncrement
          + ", source VARCHAR(255)"
          + ", type VARCHAR(255)"
          + ", code VARCHAR(255)"
          + ", updatetime BIGINT"
          + ", data TEXT"
          + ")" + engine);
      statement.executeUpdate(
          "CREATE UNIQUE INDEX product_index ON " + this.table
          + " (source, type, code, updatetime)");
      commitTransaction();
    } catch (Exception e) {
      rollbackTransaction();
      throw e;
    }
  }

  /**
   * Check whether product found in storage.
   */
  @Override
  public boolean hasProduct(ProductId id) throws Exception {
    return getProduct(id) != null;
  }

  /**
   * Get a product from storage.
   *
   * @param id
   *     The product to get.
   * @return product if found, otherwise null.
   */
  @Override
  public synchronized Product getProduct(ProductId id) throws Exception {
    Product product = null;
    final String sql = "SELECT * FROM " + this.table
        + " WHERE source=? AND type=? AND code=? AND updatetime=?";
    // prepare statement
    beginTransaction();
    try (final PreparedStatement statement = getConnection().prepareStatement(sql)) {
      statement.setQueryTimeout(60);
      // set parameters
      statement.setString(1, id.getSource());
      statement.setString(2, id.getType());
      statement.setString(3, id.getCode());
      statement.setLong(4, id.getUpdateTime().getTime());

      // execute
      try (final ResultSet rs = statement.executeQuery()) {
        if (rs.next()) {
          // found product
          final String data = rs.getString("data");
          product = new JsonProduct().getProduct(
              Json.createReader(
                new ByteArrayInputStream(data.getBytes())
              ).readObject());
        }
      }
      commitTransaction();
    } catch (SQLException e) {
      try {
        // otherwise roll back
        rollbackTransaction();
      } catch (SQLException e2) {
        // ignore
      }
      LOGGER.log(
          Level.INFO,
          "[" + getName() + "] exception in getProduct("
              + id.toString() + ")",
          e);
    }
    return product;
  }

  /**
   * Add product to storage.
   *
   * @throws ProductAlreadyInStorageException
   *     if product already in storage.
   */
  @Override
  public synchronized ProductId storeProduct(Product product) throws Exception {
    // prepare statement
    beginTransaction();
    try (
      final PreparedStatement statement = getConnection().prepareStatement(
          "INSERT INTO " + this.table
          + " (source, type, code, updatetime, data)"
          + " VALUES (?, ?, ?, ?, ?)")
    ) {
      statement.setQueryTimeout(60);
      final ProductId id = product.getId();
      // set parameters
      statement.setString(1, id.getSource());
      statement.setString(2, id.getType());
      statement.setString(3, id.getCode());
      statement.setLong(4, id.getUpdateTime().getTime());
      statement.setString(5,
          product != null
          ? new JsonProduct().getJsonObject(product).toString()
          : "");
      // execute
      statement.executeUpdate();
      commitTransaction();
      return id;
    } catch (SQLException e) {
      try {
        // otherwise roll back
        rollbackTransaction();
      } catch (SQLException e2) {
        // ignore
      }
      if (e.toString().contains("Duplicate entry")) {
        throw new ProductAlreadyInStorageException(e.toString());
      }
      throw e;
    }
  }

  /**
   * Get a ProductSource for product in database.
   *
   * @return ObjectProductSource or null if product not found.
   */
  @Override
  public ProductSource getProductSource(ProductId id) throws Exception {
    final Product product = getProduct(id);
    if (product == null) {
      return null;
    }
    return new ObjectProductSource(product);
  }

  /**
   * Store a ProductSource.
   *
   * Uses ObjectProductHandler to read Product, then calls storeProduct.
   *
   * @throws ProductAlreadyInStorageException
   *     if product already in storage.
   */
  @Override
  public ProductId storeProductSource(ProductSource input) throws Exception {
    final ObjectProductHandler handler = new ObjectProductHandler();
    input.streamTo(handler);
    return storeProduct(handler.getProduct());
  }

  /**
   * Remove product from storage.
   */
  @Override
  public synchronized void removeProduct(ProductId id) throws Exception {
    // prepare statement
    final String sql = "DELETE FROM " + this.table
          + " WHERE source=? AND type=? AND code=? AND updatetime=?";
    beginTransaction();
    try (final PreparedStatement statement = getConnection().prepareStatement(sql)) {
      statement.setQueryTimeout(60);
      // set parameters
      statement.setString(1, id.getSource());
      statement.setString(2, id.getType());
      statement.setString(3, id.getCode());
      statement.setLong(4, id.getUpdateTime().getTime());
      // execute
      statement.executeUpdate();
      commitTransaction();
    } catch (SQLException e) {
      try {
        // otherwise roll back
        rollbackTransaction();
      } catch (SQLException e2) {
        // ignore
      }
      throw e;
    }
  }

  @Override
  public void notifyListeners(StorageEvent event) {
    // listeners not supported
  }

  @Override
  public void addStorageListener(StorageListener listener) {
    // listeners not supported
  }

  @Override
  public void removeStorageListener(StorageListener listener) {
    // listeners not supported
  }

}

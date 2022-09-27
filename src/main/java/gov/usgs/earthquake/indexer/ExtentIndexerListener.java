/**
 * Extent Indexer Listener
 */
package gov.usgs.earthquake.indexer;

import gov.usgs.util.Config;
import gov.usgs.earthquake.distribution.ConfigurationException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ExtentIndexerListener is an extension of the ReliableIndexerListener. It
 * populates the ExtentSummary table with viable products.
 *
 * This listener takes an ExtentIndex for querying and table updates:
 * <dl>
 * <dt>index</dt>
 * <dd>(Required) the ExtentIndex used for querying and updates</dd>
 * </dl>
 */
public class ExtentIndexerListener extends ReliableIndexerListener {

  private static final Logger LOGGER = Logger.getLogger(ExtentIndexerListener.class.getName());

  /**
   * Configures listener, checking for correct type
   *
   * @param config configuration
   *
   * @throws ConfigurationException if incorrect type provided
   */
  @Override
  public void configure(Config config) throws Exception {
    super.configure(config);
    if (!(this.getProductIndex() instanceof ExtentIndex)) {
      throw new ConfigurationException(
          "[" + getName() + "] index must be of type ExtentIndex. Given type "
          + productIndex.getClass().getName());
    }
  }

  /**
   * Loads the last index id in the extent table before the listener has to use it
   *
   * @throws Exception if ExtentIndex can't do database transaction
   */
  @Override
  protected void onBeforeProcessThreadStart() throws Exception {
    long lastIndexId = ((ExtentIndex) productIndex).getLastExtentIndexId();
    LOGGER.fine("[" + getName() + "] last index id " + lastIndexId);
    setLastIndexId(lastIndexId);
  }

  /**
   * Hands product to index to be added to table
   *
   * @param product the product to be added
   *
   * @throws Exception if ExtentIndex can't do database transaction
   */
  @Override
  public void processProduct(ProductSummary product) throws Exception {
    super.processProduct(product);
    ExtentSummary extent = new ExtentSummary(product);

    if (extent.isValid()) {
      LOGGER.log(Level.FINEST, "[" + getName() + "] adding product "
          + product.getId().toString()
          + " to extent table");
      ((ExtentIndex) productIndex).addExtentSummary(extent);
    } else {
      LOGGER.log(Level.FINEST, "[" + getName() + "] product "
          + product.getId().toString()
          + " has no extent information; won't add to extent table");
    }

    setLastIndexId(product.getIndexId());
  }

}

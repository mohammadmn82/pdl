package gov.usgs.earthquake.dyfi;

import gov.usgs.earthquake.product.Content;
import gov.usgs.earthquake.product.Product;

import java.io.InputStream;
import java.math.BigDecimal;

/**
 * DYFIProduct object to add additional Product properties based on contents.
 *
 * This subclass of Product provides access to additional DYFI-specific
 * attributes and loads these attributes, as well as additional Product
 * attributes from ShakeMap source XML files.
 */
public class DYFIProduct extends Product {

	/** References the event_data.xml file in the Product */
	public static final String DYFI_EVENT_XML_ATTACHMENT = "event_data.xml";

	/** Attribute for number of responses. Read from XML */
	public static final String DYFI_NUM_RESP_ATTRIBUTE = "nresponses";
	/** Attribute for max intensity. Read from XML */
	public static final String DYFI_MAX_MMI_ATTRIBUTE = "max_intensity";

	/** Internally set number of responses property */
	public static final String DYFI_NUM_RESP_PROPERTY = "numResp";
	/** Internally set max intensity property */
	public static final String DYFI_MAX_MMI_PROPERTY = "maxmmi";

	/**
	 * Creates a new DYFIProduct using the given <code>Product</code> as a
	 * basis. The given product must have a
	 * <code>DYFI_EVENT_XML_ATTACHMENT</code> in order to successfully create a
	 * DYFIProduct.
	 *
	 * @param product
	 *            The product serving as a basis for this instance.
	 */
	public DYFIProduct(final Product product) {
		super(product);

		if (product.getProperties().get(DYFI_NUM_RESP_PROPERTY) == null
				|| product.getProperties().get(DYFI_MAX_MMI_PROPERTY) == null) {
			// only load xml if properties not set
			loadEventXml();
		}
	}

	/** Reads in DYFI Event EXL and parses it into a EventDataXMLHandler */
	protected void loadEventXml() {
		// Parse event_data.xml for more information about product
		Content source = getContents().get(DYFI_EVENT_XML_ATTACHMENT);
		if (source == null) {
			throw new IllegalArgumentException("Given product lacked the "
					+ "required file to be a DYFIProduct. ("
					+ DYFI_EVENT_XML_ATTACHMENT + ")");
		}

		EventDataXMLHandler handler = new EventDataXMLHandler(this);
		try (final InputStream in = source.getInputStream()) {
			handler.parse(in);
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * @return The number of felt reports submitted for this event.
	 */
	public int getNumResponses() {
		return Integer.parseInt(getProperties().get(DYFI_NUM_RESP_PROPERTY));
	}

	/**
	 * Set the number of felt reports submitted for this event.
	 *
	 * @param numResponses
	 *            The new number of submitted felt reports.
	 * @throws NumberFormatException
	 *             If the given <code>numResponses</code> could not be parsed
	 *             into an integer.
	 */
	public void setNumResponses(final String numResponses)
			throws NumberFormatException {
		// This is a throw-away line, but makes sure the value being passed
		// at least makes sense; an exception is thrown for garbage input.
		Integer.parseInt(numResponses);

		getProperties().put(DYFI_NUM_RESP_PROPERTY, numResponses);
	}

	/**
	 * @return The maximum reported intensity for this event.
	 */
	public BigDecimal getMaxMMI() {
		return new BigDecimal(getProperties().get(DYFI_MAX_MMI_PROPERTY));
	}

	/**
	 * Sets the maximum reported intensity for this event.
	 *
	 * @param maxMMI
	 *            The new maximum reported intensity.
	 * @throws NumberFormatException
	 *             If the given <code>maxMMI</code> could not be parsed
	 *             into a double.
	 */
	public void setMaxMMI(final String maxMMI) throws NumberFormatException {
		// This is a throw-away line, but makes sure the value being passed
		// at least makes sense; an exception is thrown for garbage input.
		Double.parseDouble(maxMMI);

		getProperties().put(DYFI_MAX_MMI_PROPERTY, maxMMI);
	}
}

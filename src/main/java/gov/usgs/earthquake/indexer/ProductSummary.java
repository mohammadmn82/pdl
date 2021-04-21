/*
 * ProductSummary
 */
package gov.usgs.earthquake.indexer;

import gov.usgs.earthquake.product.Product;
import gov.usgs.earthquake.product.ProductId;
import gov.usgs.util.XmlUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.net.URI;

/**
 * A ProductSummary is essentially a product without its contents.
 *
 * These are usually created by {@link IndexerModule}s, which may
 * inspect Product Content to add additional summary properties.
 */
public class ProductSummary {

	/** An ID used by the ProductIndex. */
	private Long indexId = null;

	/** The product id. */
	private ProductId id = null;

	/** The product status. */
	private String status = null;

	/** The product tracker url. */
	private URL trackerURL = null;

	/** The product properties. */
	private Map<String, String> properties = new HashMap<String, String>();

	/** The product links. */
	private Map<String, List<URI>> links = new HashMap<String, List<URI>>();

	/** Unique identifier for this event. */
	private String eventSource = null;

	/** Unique identifier from the event network. */
	private String eventSourceCode = null;

	/** When the event occurred. */
	private Date eventTime = null;

	/** Where the event occurred. */
	private BigDecimal eventLatitude = null;

	/** Where the event occurred. */
	private BigDecimal eventLongitude = null;

	/** Where the event occurred. */
	private BigDecimal eventDepth = null;

	/** How big the event was. */
	private BigDecimal eventMagnitude = null;

	/** Product version. */
	private String version = null;

	/** Whether this product is "preferred". */
	private long preferredWeight = 0;

	/**
	 * Empty constructor.
	 */
	public ProductSummary() {
	}

	/**
	 * Copy constructor for ProductSummary.
	 *
	 * Does a deep copy of properties and links maps. All other attributes are
	 * copied by reference.
	 *
	 * @param copy
	 *            product summary to copy.
	 */
	public ProductSummary(final ProductSummary copy) {
		this.indexId = copy.getIndexId();
		this.id = copy.getId();
		this.status = copy.getStatus();
		this.trackerURL = copy.getTrackerURL();
		this.properties.putAll(copy.getProperties());

		Map<String, List<URI>> copyLinks = copy.getLinks();
		Iterator<String> iter = copyLinks.keySet().iterator();
		while (iter.hasNext()) {
			String relation = iter.next();
			links.put(relation, new LinkedList<URI>(copyLinks.get(relation)));
		}

		this.setEventSource(copy.getEventSource());
		this.setEventSourceCode(copy.getEventSourceCode());
		this.setEventTime(copy.getEventTime());
		this.setEventLatitude(copy.getEventLatitude());
		this.setEventLongitude(copy.getEventLongitude());
		this.setEventDepth(copy.getEventDepth());
		this.setEventMagnitude(copy.getEventMagnitude());
		this.setVersion(copy.getVersion());
		this.setPreferredWeight(copy.getPreferredWeight());
	}

	/**
	 * Create a ProductSummary from a product.
	 *
	 * All attributes are copied from the product, and preferredWeight is set to
	 * 1L.
	 *
	 * @param product
	 *            the product to summarize.
	 */
	public ProductSummary(final Product product) {
		this.id = product.getId();
		this.status = product.getStatus();
		this.trackerURL = product.getTrackerURL();
		this.properties.putAll(product.getProperties());

		Map<String, List<URI>> copyLinks = product.getLinks();
		Iterator<String> iter = copyLinks.keySet().iterator();
		while (iter.hasNext()) {
			String relation = iter.next();
			links.put(relation, new LinkedList<URI>(copyLinks.get(relation)));
		}

		this.setEventSource(product.getEventSource());
		this.setEventSourceCode(product.getEventSourceCode());
		this.setEventTime(product.getEventTime());
		this.setEventLatitude(product.getLatitude());
		this.setEventLongitude(product.getLongitude());
		this.setEventDepth(product.getDepth());
		this.setEventMagnitude(product.getMagnitude());
		this.setVersion(product.getVersion());
		this.setPreferredWeight(1L);
	}

	/** @return indexId */
	public Long getIndexId() {
		return indexId;
	}

	/** @param indexId to set */
	public void setIndexId(Long indexId) {
		this.indexId = indexId;
	}

	/** @return product id */
	public ProductId getId() {
		return id;
	}

	/** @param id to set */
	public void setId(ProductId id) {
		this.id = id;
	}

	/** @return status */
	public String getStatus() {
		return status;
	}

	/** @param status to set */
	public void setStatus(String status) {
		this.status = status;
	}

	/** @return if product is deleted */
	public boolean isDeleted() {
		if (Product.STATUS_DELETE.equalsIgnoreCase(this.status)) {
			return true;
		} else {
			return false;
		}
	}

	/** @return trackerURL */
	public URL getTrackerURL() {
		return trackerURL;
	}

	/** @param trackerURL to set */
	public void setTrackerURL(URL trackerURL) {
		this.trackerURL = trackerURL;
	}

	/** @return preferredWeight */
	public long getPreferredWeight() {
		return preferredWeight;
	}

	/** @param weight to set */
	public void setPreferredWeight(long weight) {
		this.preferredWeight = weight;
	}

	/**
	 * @return the properties
	 */
	public Map<String, String> getProperties() {
		return properties;
	}

	/**
	 * @param properties
	 *            the properties to set
	 */
	public void setProperties(final Map<String, String> properties) {
		this.properties.putAll(properties);
	}

	/**
	 * Returns a reference to the links map.
	 *
	 * @return the links
	 */
	public Map<String, List<URI>> getLinks() {
		return links;
	}

	/**
	 * Copies entries from provided map.
	 *
	 * @param links
	 *            the links to set
	 */
	public void setLinks(final Map<String, List<URI>> links) {
		this.links.clear();
		this.links.putAll(links);
	}

	/**
	 * Add a link to a product.
	 *
	 * @param relation
	 *            how link is related to product.
	 * @param href
	 *            actual link.
	 */
	public void addLink(final String relation, final URI href) {
		List<URI> relationLinks = links.get(relation);
		if (relationLinks == null) {
			relationLinks = new LinkedList<URI>();
			links.put(relation, relationLinks);
		}
		relationLinks.add(href);
	}

	/** @return null or eventId */
	public String getEventId() {
		if (eventSource == null || eventSourceCode == null) {
			return null;
		}
		return (eventSource + eventSourceCode).toLowerCase();
	}

	/** @return eventSource */
	public String getEventSource() {
		return eventSource;
	}

	/** @param eventSource to set */
	public void setEventSource(String eventSource) {
		this.eventSource = eventSource;

		// event ids are case insensitive, force lower.
		if (this.eventSource != null) {
			this.eventSource = this.eventSource.toLowerCase();
		}

		if (this.eventSource != null) {
			this.properties.put(Product.EVENTSOURCE_PROPERTY, this.eventSource);
		} else {
			this.properties.remove(Product.EVENTSOURCE_PROPERTY);
		}
	}

	/** @return eventSourceCode */
	public String getEventSourceCode() {
		return eventSourceCode;
	}

	/** @param eventSourceCode to set */
	public void setEventSourceCode(String eventSourceCode) {
		this.eventSourceCode = eventSourceCode;

		// event ids are case insensitive, force lower.
		if (this.eventSourceCode != null) {
			this.eventSourceCode = this.eventSourceCode.toLowerCase();
		}

		if (this.eventSourceCode != null) {
			this.properties.put(Product.EVENTSOURCECODE_PROPERTY,
					this.eventSourceCode);
		} else {
			this.properties.remove(Product.EVENTSOURCECODE_PROPERTY);
		}
	}

	/** @return eventTime */
	public Date getEventTime() {
		return eventTime;
	}

	/** @param eventTime to set */
	public void setEventTime(Date eventTime) {
		this.eventTime = eventTime;
		if (eventTime != null) {
			this.properties.put(Product.EVENTTIME_PROPERTY,
					XmlUtils.formatDate(eventTime));
		} else {
			this.properties.remove(Product.EVENTTIME_PROPERTY);
		}

	}

	/** @return eventLatitude */
	public BigDecimal getEventLatitude() {
		return eventLatitude;
	}

	/** @param eventLatitude to set */
	public void setEventLatitude(BigDecimal eventLatitude) {
		this.eventLatitude = eventLatitude;
		if (eventLatitude != null) {
			this.properties.put(Product.LATITUDE_PROPERTY,
					eventLatitude.toString());
		} else {
			this.properties.remove(Product.LATITUDE_PROPERTY);
		}
	}

	/** @return eventLongitude */
	public BigDecimal getEventLongitude() {
		return eventLongitude;
	}

	/** @param eventLongitude to set */
	public void setEventLongitude(BigDecimal eventLongitude) {
		this.eventLongitude = eventLongitude;
		if (eventLongitude != null) {
			this.properties.put(Product.LONGITUDE_PROPERTY,
					eventLongitude.toString());
		} else {
			this.properties.remove(Product.LONGITUDE_PROPERTY);
		}
	}

	/** @return eventDepth */
	public BigDecimal getEventDepth() {
		return eventDepth;
	}

	/** @param eventDepth to set */
	public void setEventDepth(BigDecimal eventDepth) {
		this.eventDepth = eventDepth;
		if (eventDepth != null) {
			this.properties.put(Product.DEPTH_PROPERTY, eventDepth.toString());
		} else {
			this.properties.remove(Product.DEPTH_PROPERTY);
		}
	}

	/** @return eventMagnitude */
	public BigDecimal getEventMagnitude() {
		return eventMagnitude;
	}

	/** @param eventMagnitude to set */
	public void setEventMagnitude(BigDecimal eventMagnitude) {
		this.eventMagnitude = eventMagnitude;
		if (eventMagnitude != null) {
			this.properties.put(Product.MAGNITUDE_PROPERTY,
					eventMagnitude.toString());
		} else {
			this.properties.remove(Product.MAGNITUDE_PROPERTY);
		}
	}

	/** @return version */
	public String getVersion() {
		return version;
	}

	/** @param version to set */
	public void setVersion(String version) {
		this.version = version;
		if (version != null) {
			this.properties.put(Product.VERSION_PROPERTY, version);
		} else {
			this.properties.remove(Product.VERSION_PROPERTY);
		}
	}

	/** @return type */
	public String getType() {
		return getId().getType();
	}

	/** @return source */
	public String getSource() {
		return getId().getSource();
	}

	/** @return code */
	public String getCode() {
		return getId().getCode();
	}

	/** @return updateTime */
	public Date getUpdateTime() {
		return getId().getUpdateTime();
	}

	/**
	 * Compares two ProductSummaries to determine if they are equal.
	 *
	 * This first implementation just considers the ProductId of each summary.
	 * This is probably not the best way to check for equality.
	 */
	@Override
	public boolean equals(Object o) {
		if (o instanceof ProductSummary) {
			return ((ProductSummary) o).getId().equals(this.getId());
		}
		return false;
	}

	/**
	 * Generate hashcode for ProductId using all components.
	 *
	 * This implementation just uses hashcode from ProductId.
	 */
	@Override
	public int hashCode() {
		return getId().hashCode();
	}

}

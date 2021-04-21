/**
 * This class is not currently in use, but kept for posterity and the ability
 * to add this support back in if it is deemed necessary.
 */
package gov.usgs.earthquake.shakemap;

import gov.usgs.util.StringUtils;
import gov.usgs.util.XmlUtils;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parser for Shakemap "stationlist.xml" metadata.
 */
public class StationlistXMLHandler extends DefaultHandler {

	/** Element for Shakemap data */
	public static final String SHAKEMAPDATA_ELEMENT = "shakemap-data";
	/** Shakemap data version */
	public static final String SHAKEMAPDATA_VERSION = "map_version";

	/** Element for earthquake */
	public static final String EARTHQUAKE_ELEMENT = "earthquake";
	/** String for earthquake id */
	public static final String EARTHQUAKE_ID = "id";
	/** String for earthquake latitiude */
	public static final String EARTHQUAKE_LAT = "lat";
	/** String for earthquake longitude */
	public static final String EARTHQUAKE_LON = "lon";
	/** String for earthquake magnitude */
	public static final String EARTHQUAKE_MAG = "mag";
	/** String for earthquake year */
	public static final String EARTHQUAKE_YEAR = "year";
	/** String for earthquake month */
	public static final String EARTHQUAKE_MONTH = "month";
	/** String for earthquake day */
	public static final String EARTHQUAKE_DAY = "day";
	/** String for earthquake hour */
	public static final String EARTHQUAKE_HOUR = "hour";
	/** String for earthquake minute */
	public static final String EARTHQUAKE_MINUTE = "minute";
	/** String for earthquake second */
	public static final String EARTHQUAKE_SECOND = "second";
	/** String for earthquake timezone */
	public static final String EARTHQUAKE_TIMEZONE = "timezone";
	/** String for earthquake depth */
	public static final String EARTHQUAKE_DEPTH = "depth";
	/** String for earthquake locstring */
	public static final String EARTHQUAKE_LOCSTRING = "locstring";
	/** String for earthquake created */
	public static final String EARTHQUAKE_CREATED = "created";

	/** The ShakeMap object parsed by this handler. */
	private ShakeMap shakemap;

	/**
	 * Constructor
	 * @param shakemap a shakemap object parsed by handler
	 */
	public StationlistXMLHandler(ShakeMap shakemap) {
		this.shakemap = shakemap;
	}

	/** @return shakemap */
	public ShakeMap getShakemap() {
		return shakemap;
	}

	/** @param shakemap to set */
	public void setShakemap(ShakeMap shakemap) {
		this.shakemap = shakemap;
	}

	/**
	 * Takes in an XML object and parses it
	 * @param in an object
	 * @return A shakemap
	 * @throws Exception if error occurs
	 */
	public ShakeMap parse(final Object in) throws Exception {
		XmlUtils.parse(in, this);
		return getShakemap();
	}

	/**
	 * Parse element attributes.
	 *
	 * @param uri element namespace.
	 * @param localName element name.
	 * @param qName qualified element name.
	 * @param attributes element attributes.
	 * @throws SAXException if error occurs
	 */
	public final void startElement(final String uri, final String localName,
			final String qName, final Attributes attributes)
			throws SAXException {

		try {
			if (localName.equals(SHAKEMAPDATA_ELEMENT)) {
				shakemap.setVersion(attributes
						.getValue(SHAKEMAPDATA_VERSION));
			} else if (localName.equals(EARTHQUAKE_ELEMENT)) {
				shakemap.setLatitude(new BigDecimal(attributes
						.getValue(EARTHQUAKE_LAT)));
				shakemap.setLongitude(new BigDecimal(attributes
						.getValue(EARTHQUAKE_LON)));
				shakemap.setMagnitude(new BigDecimal(attributes
						.getValue(EARTHQUAKE_MAG)));
				shakemap.setDepth(new BigDecimal(attributes
						.getValue(EARTHQUAKE_DEPTH)));
				Calendar cal = Calendar.getInstance(TimeZone
						.getTimeZone(attributes.getValue(EARTHQUAKE_TIMEZONE)));
				cal.set(Integer.parseInt(attributes.getValue(EARTHQUAKE_YEAR)),
						Integer.parseInt(attributes.getValue(EARTHQUAKE_MONTH)),
						Integer.parseInt(attributes.getValue(EARTHQUAKE_DAY)),
						Integer.parseInt(attributes.getValue(EARTHQUAKE_HOUR)),
						Integer.parseInt(attributes.getValue(EARTHQUAKE_MINUTE)),
						Integer.parseInt(attributes.getValue(EARTHQUAKE_SECOND)));
				shakemap.setEventTime(cal.getTime());
				shakemap.setEventDescription(attributes
						.getValue(EARTHQUAKE_LOCSTRING));
				shakemap.setProcessTimestamp(new Date(StringUtils
						.getLong(attributes.getValue(EARTHQUAKE_CREATED))));
			}
		} catch (Exception e) {
			throw new SAXException(e);
		}
	}

}

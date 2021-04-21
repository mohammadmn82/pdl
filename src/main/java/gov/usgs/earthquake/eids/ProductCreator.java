package gov.usgs.earthquake.eids;

import gov.usgs.earthquake.product.Product;

import java.io.File;
import java.util.List;

/**
 * Interface used by the EIDSInputWedge to create products from files.
 *
 * Parse a file (or directory) into a list of products.
 */
public interface ProductCreator {

	/**
	 * Parse product(s) from a file or directory.
	 *
	 * @param file
	 *            file or directory.
	 * @return list of parsed products.
	 * @throws Exception if error occurs
	 */
	List<Product> getProducts(final File file) throws Exception;

	/**
	 * @return whether product creator is currently validating.
	 */
	boolean isValidate();

	/**
	 * Enable validation during getProducts method.
	 *
	 * @param validate boolean to enable/disable
	 * @throws IllegalArgumentException
	 *             if creator doesn't support validation.
	 */
	void setValidate(boolean validate);

}

package gov.usgs.earthquake.distribution;

import java.io.File;
import java.io.InputStream;
import java.security.PublicKey;
import java.util.Optional;
import java.util.logging.Logger;

import gov.usgs.earthquake.product.Product;
import gov.usgs.earthquake.product.ProductId;
import gov.usgs.util.Config;
import gov.usgs.util.DefaultConfigurable;
import gov.usgs.util.StreamUtils;

public class SignatureVerifier extends DefaultConfigurable {

	/** logging object. */
	private static final Logger LOGGER = Logger
			.getLogger(SignatureVerifier.class.getName());

	/** Property for whether or not to verify signatures. */
	public static final String VERIFY_SIGNATURES_PROPERTY_NAME = "verifySignatures";
	/** Don't verify signatures (Default). */
	public static final String DEFAULT_VERIFY_SIGNATURE = "off";
	/** Test signatures, but don't reject invalid. */

	public static final String TEST_VERIFY_SIGNATURE = "test";
	/** Allow products that do not have a configured key. */
	public static final String ONLY_VERIFY_KNOWN = "allowUnknownSigner";

	/** Property for a list of keys. */
	public static final String KEYCHAIN_PROPERTY_NAME = "keychain";

	/** Property for a file of keys. */
	public static final String KEYCHAIN_FILE_PROPERTY_NAME = "keychainFile";

	/** Whether or not to reject invalid signatures. */
	private boolean rejectInvalidSignatures = false;

	/** If not rejecting invalid signatures, test them anyways. */
	private boolean testSignatures = false;

	/**
	 * When rejecting invalid signatures, true will prevent an
	 * InvalidSignatureException if there are no candidate keys.
	 */
	private boolean allowUnknownSigner = false;

	/** List of candidate keys. */
	private ProductKeyChain keychain;

	@Override
	public void configure(final Config config) throws Exception {
		String verifySignatures = config
				.getProperty(VERIFY_SIGNATURES_PROPERTY_NAME);
		// configured
		if (verifySignatures != null) {
			// "test"
			if (verifySignatures.equals(TEST_VERIFY_SIGNATURE)) {
				testSignatures = true;
				LOGGER.config("[" + getName() + "] test message signatures");
			}
			// not "off"
			else if (!verifySignatures.equals(DEFAULT_VERIFY_SIGNATURE)) {
				rejectInvalidSignatures = true;
				LOGGER.config("[" + getName() + "] reject invalid signatures");
			}

			String keyNames = config.getProperty(KEYCHAIN_PROPERTY_NAME);
			if (keyNames != null) {
				LOGGER.config("[" + getName() + "] using product keys "
						+ keyNames);
				keychain = new ProductKeyChain(keyNames, Config.getConfig());
			} else {
				String keychainFileName = config.getProperty(KEYCHAIN_FILE_PROPERTY_NAME);
				if (keychainFileName != null) {
					Config keychainConfig = new Config();

					try (InputStream in = StreamUtils.getInputStream(
							new File(keychainFileName))) {
						keychainConfig.load(in);
					}
					keyNames = keychainConfig.getProperty(KEYCHAIN_PROPERTY_NAME);
					keychain = new ProductKeyChain(keyNames,keychainConfig);
				} else {
					LOGGER.warning("[" + getName() + "] no product keys configured");
				}
			}
		}
	}

	/** @return boolean RejectInvalidSignatures */
	public boolean isRejectInvalidSignatures() {
		return rejectInvalidSignatures;
	}

	/** @param rejectInvalidSignatures boolean to set */
	public void setRejectInvalidSignatures(boolean rejectInvalidSignatures) {
		this.rejectInvalidSignatures = rejectInvalidSignatures;
	}

	/** @return boolean TestSignatures */
	public boolean isTestSignatures() {
		return testSignatures;
	}

	/** @param testSignatures boolean to set */
	public void setTestSignatures(boolean testSignatures) {
		this.testSignatures = testSignatures;
	}

	/** @return Product keychain */
	public ProductKeyChain getKeychain() {
		return keychain;
	}

	/** @param keychain ProductKeyChain to set */
	public void setKeychain(ProductKeyChain keychain) {
		this.keychain = keychain;
	}

	/** @return boolean AllowUnknownSigner */
	public boolean isAllowUnknownSigner() {
		return allowUnknownSigner;
	}

	/** @param allowUnknownSigner boolean to set */
	public void setAllowUnknownSigner(boolean allowUnknownSigner) {
		this.allowUnknownSigner = allowUnknownSigner;
	}

	/**
	 * Attempt to verify a products signature.
	 *
	 * @param product
	 *            product to verify.
	 * @return true if the signature is from a key in the keychain.
	 * @throws InvalidSignatureException
	 *             if rejectInvalidSignatures=true, and signature was not
	 *             verified; allowUnknownSigner=true prevents this exception
	 *             when no keys are found in the keychain for the product.
	 * @throws Exception if error occurs
	 */
	public boolean verifySignature(final Product product) throws Exception {
		boolean verified = false;
		String verifiedKeyName = null;

		if (testSignatures || rejectInvalidSignatures) {
			ProductId id = product.getId();
			PublicKey[] candidateKeys = new PublicKey[] {};

			if (keychain != null) {
				candidateKeys = keychain.getProductKeys(id);
				LOGGER.finer("[" + getName() + "] number of candidate keys="
						+ candidateKeys.length);
				if (candidateKeys.length > 0) {
					PublicKey publicKey = product.verifySignatureKey(
							candidateKeys,
							product.getSignatureVersion());
					if (publicKey != null) {
						verified = true;
						// find key that verified
						Optional<ProductKey> verifiedKey = keychain.getKeychain()
								.stream().filter(key -> {
									return publicKey.equals(key.getKey());
								}).findAny();
						if (verifiedKey.isPresent()) {
							verifiedKeyName = verifiedKey.get().getName();
						}
					}
				}
			} else {
				LOGGER.warning("[" + getName() + "] missing Signature Keychain");
			}

			LOGGER.fine("[" + getName() + "] signature verified=" + verified
					+ (verified ? " (key=" + verifiedKeyName + ")" : "")
					+ ", id=" + product.getId());

			if (allowUnknownSigner && candidateKeys.length == 0) {
					LOGGER.finer("[" + getName()
							+ "] unknown signer, allowed by configuration");
					return false;
			}

			if (!verified && rejectInvalidSignatures) {
				throw new InvalidSignatureException("[" + getName()
						+ "] bad signature for id=" + id);
			}
		}

		return verified;
	}

}

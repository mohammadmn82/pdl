/*
 * CryptoUtils
 *
 * $Id$
 * $URL$
 */
package gov.usgs.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.Key;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.SignatureException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;

import ch.ethz.ssh2.crypto.PEMDecoder;

/**
 * Encryption and signing utilities.
 */
public class CryptoUtils {

	/** Algorithm used by AES keys and ciphers. */
	public static final String AES_ALGORITHM = "AES";
	/** Number of bits for AES 128 bit key. */
	public static final int AES_128 = 128;
	/** Number of bits for AES 256 bit key. */
	public static final int AES_256 = 256;

	/** Algorithm used by DSA keys. */
	public static final String DSA_ALGORITHM = "DSA";
	/** Algorithm used for signature with DSA key. */
	public static final String DSA_SIGNATURE_ALGORITHM = "SHA1withDSA";
	/** Number of bits for DSA 1024 bit key. */
	public static final int DSA_1024 = 1024;

	/** Algorithm used by RSA keys and ciphers. */
	public static final String RSA_ALGORITHM = "RSA";
	/** Algorithm used for signature with RSA key. */
	public static final String RSA_SIGNATURE_ALGORITHM = "SHA1withRSA";
	/** Number of bits for RSA 2048 bit key. */
	public static final int RSA_2048 = 2048;
	/** Number of bits for RSA 4096 bit key. */
	public static final int RSA_4096 = 4096;

	/** Signature versions. */
	public enum Version {
		/** Signature enum for v1 */
		SIGNATURE_V1("v1"),
		/** Signature enum for v2 */
		SIGNATURE_V2("v2");

		private String value;

		Version(final String value) {
			this.value = value;
		}

		public String toString() {
			return this.value;
		}

		/**
		 * @param value to get a signature from
		 * @return a version
		 * @throws IllegalArgumentException if unknown version.
		 */
		public static Version fromString(final String value) {
			if (SIGNATURE_V1.value.equals(value)) {
				return SIGNATURE_V1;
			} else if (SIGNATURE_V2.value.equals(value)) {
				return SIGNATURE_V2;
			} else {
				throw new IllegalArgumentException("Invalid signature version");
			}
		}
	}

	/** v2 Algorithm for DSA signature */
	public static final String SIGNATURE_V2_DSA_ALGORITHM = "SHA256withDSA";
	/** v2 Algorithm for RSA signature */
	public static final String SIGNATURE_V2_RSA_ALGORITHM = "RSASSA-PSS";

	/**
	 * Process a data stream using a cipher.
	 *
	 * If cipher is initialized to ENCRYPT_MODE, the input stream will be
	 * encrypted. If cipher is initialized to DECRYPT_MODE, the input stream
	 * will be decrypted.
	 *
	 * @param cipher
	 *            an initialized cipher.
	 * @param in
	 *            the data to encrypt.
	 * @param out
	 *            where encrypted data is written.
	 * @throws NoSuchAlgorithmException if invalid encrypt/decrypt algorithm
	 * @throws NoSuchPaddingException on padding error
	 * @throws InvalidKeyException if key is not RSA or DSA.
	 * @throws IOException if IO error occurs
	 */
	public static void processCipherStream(final Cipher cipher,
			final InputStream in, final OutputStream out)
			throws NoSuchAlgorithmException, NoSuchPaddingException,
			InvalidKeyException, IOException {
		CipherOutputStream cos = new CipherOutputStream(out, cipher);
		StreamUtils.transferStream(in, cos);
	}

	/**
	 * Create and initialize an encrypting cipher using key.getAlgorithm() as
	 * transformation.
	 *
	 * @param key
	 *            the key used to encrypt.
	 * @return a cipher used to encrypt.
	 * @throws NoSuchAlgorithmException on invalid algorithm
	 * @throws NoSuchPaddingException on invalid padding
	 * @throws InvalidKeyException if key is not RSA or DSA.
	 */
	public static Cipher getEncryptCipher(final Key key)
			throws NoSuchAlgorithmException, NoSuchPaddingException,
			InvalidKeyException {
		Cipher cipher = Cipher.getInstance(key.getAlgorithm());
		cipher.init(Cipher.ENCRYPT_MODE, key);
		return cipher;
	}

	/**
	 * Create and initialize a decrypting cipher using key.getAlgorithm as
	 * transformation.
	 *
	 * @param key
	 *            the key used to decrypt.
	 * @return a cipher used to decrypt.
	 * @throws NoSuchAlgorithmException on invalid algorithm
	 * @throws NoSuchPaddingException on invalid padding
	 * @throws InvalidKeyException if key is not RSA or DSA.
	 */
	public static Cipher getDecryptCipher(final Key key)
			throws NoSuchAlgorithmException, NoSuchPaddingException,
			InvalidKeyException {
		Cipher cipher = Cipher.getInstance(key.getAlgorithm());
		cipher.init(Cipher.DECRYPT_MODE, key);
		return cipher;
	}

	/**
	 * Create and configure a signature object based on key type.
	 *
	 * @param key
	 *     Key used to sign/verify.
	 * @param version
	 *     SIGNATURE_V1 or SIGNATURE_V2
	 * @return
	 *     Configured Signature object
	 * @throws InvalidKeyException
	 *     if key is not RSA or DSA.
	 * @throws NoSuchAlgorithmException
	 *     on invalid algorithm
	 * @throws SignatureException
	 *     on signature error
	 */
	public static Signature getSignature(final Key key, final Version version)
			throws InvalidKeyException, NoSuchAlgorithmException,
					SignatureException {
		Signature signature = null;
		if (version == Version.SIGNATURE_V1) {
			if (key instanceof DSAPrivateKey || key instanceof DSAPublicKey) {
				signature = Signature.getInstance(DSA_SIGNATURE_ALGORITHM);
			} else if (key instanceof RSAPrivateKey || key instanceof RSAPublicKey) {
				signature = Signature.getInstance(RSA_SIGNATURE_ALGORITHM);
			}
		} else if (version == Version.SIGNATURE_V2) {
			if (key instanceof DSAPrivateKey || key instanceof DSAPublicKey) {
				signature = Signature.getInstance(SIGNATURE_V2_DSA_ALGORITHM);
			} else if (key instanceof RSAPrivateKey || key instanceof RSAPublicKey) {
				signature = Signature.getInstance(SIGNATURE_V2_RSA_ALGORITHM);
			}
		} else {
			throw new IllegalArgumentException("Unexpected signature version " + version);
		}
		if (signature == null) {
			throw new InvalidKeyException("Expected DSA or RSA key");
		}
		return signature;
	}

	/**
	 *
	 * @param key Key used to sign/verify.
	 * @param version SIGNATURE_V1 or SIGNATURE_V2
	 * @param signature A signature
	 * @throws InvalidAlgorithmParameterException
	 *     on invalid or inappropriate algorithm parameters
	 */
	public static void configureSignature(final Key key, final Version version,
			final Signature signature) throws InvalidAlgorithmParameterException {
		if (version == Version.SIGNATURE_V2
				&& (key instanceof RSAPrivateKey || key instanceof RSAPublicKey)) {
			int keySize;
			if (key instanceof RSAPrivateKey) {
				keySize = ((RSAPrivateKey)key).getModulus().bitLength();
			} else {
				keySize = ((RSAPublicKey)key).getModulus().bitLength();
			}
			// match python cryptography calculation:
			// https://github.com/pyca/cryptography/blob/b16561670320c65a18cce41d0db0c42ab68350a9/src/cryptography/hazmat/primitives/asymmetric/padding.py#L73
			// 32 = (sha)256 / 8
			int maxSaltLength = ((keySize + 6) / 8) - 32 - 2;
			signature.setParameter(
					new PSSParameterSpec("SHA-256", "MGF1",
							MGF1ParameterSpec.SHA256, maxSaltLength, 1));
		}
	}

	/**
	 * A convenience method that chooses a signature algorithm based on the key
	 * type. Works with DSA and RSA keys.
	 *
	 * @param privateKey a private key
	 * @param data data to sign
	 * @return signature as hex encoded string
	 * @throws InvalidAlgorithmParameterException
	 *     on invalid or inappropriate algorithm parameters
	 * @throws InvalidKeyException
	 *     if key is not RSA or DSA.
	 * @throws NoSuchAlgorithmException
	 *     on invalid algorithm
	 * @throws SignatureException
	 *     on signature error
	 */
	public static String sign(final PrivateKey privateKey, final byte[] data)
			throws InvalidAlgorithmParameterException, InvalidKeyException,
			NoSuchAlgorithmException, SignatureException {
		// use v1 by default
		return sign(privateKey, data, Version.SIGNATURE_V1);
	}

	/**
	 * Generate a signature.
	 *
	 * @param privateKey
	 *            private key to use, should be acceptable by signature
	 *            instance.
	 * @param data
	 *            data/hash to sign.
	 * @param version
	 *            the signature version.
	 * @return signature as hex encoded string.
	 * @throws InvalidAlgorithmParameterException
	 *            on invalid or inappropriate algorithm parameters
	 * @throws NoSuchAlgorithmException
	 *            on invalid algorithm
	 * @throws InvalidKeyException
	 *            if key is not RSA or DSA.
	 * @throws SignatureException
	 *            on signature error
	 */
	public static String sign(final PrivateKey privateKey, final byte[] data,
			final Version version) throws InvalidAlgorithmParameterException,
			InvalidKeyException, NoSuchAlgorithmException, SignatureException {
		final Signature signature = getSignature(privateKey, version);
		signature.initSign(privateKey);
		configureSignature(privateKey, version, signature);
		signature.update(data);
		return Base64.getEncoder().encodeToString(signature.sign());
	}

	/**
	 * A convenience method that chooses a signature algorithm based on the key
	 * type. Works with DSA and RSA keys.
	 *
	 * @param publicKey
	 *            public key corresponding to private key that generated
	 *            signature.
	 * @param data
	 *            data/hash to verify
	 * @param allegedSignature
	 *            to try and verify with
	 * @return boolean
	 * @throws InvalidAlgorithmParameterException
	 *            on invalid or inappropriate algorithm parameters
	 * @throws InvalidKeyException
	 *            if key is not RSA or DSA.
	 * @throws NoSuchAlgorithmException
	 *            on invalid algorithm
	 * @throws SignatureException
	 *            on signature error
	 */
	public static boolean verify(final PublicKey publicKey, final byte[] data,
			final String allegedSignature)
			throws InvalidAlgorithmParameterException, InvalidKeyException,
			NoSuchAlgorithmException, SignatureException {
		return verify(publicKey, data, allegedSignature, Version.SIGNATURE_V1);
	}

	/**
	 * Verify a signature.
	 *
	 * @param publicKey
	 *            public key corresponding to private key that generated
	 *            signature.
	 * @param data
	 *            the data/hash that was signed.
	 * @param allegedSignature
	 *            the signature being verified.
	 * @param version
	 *            signature version.
	 * @return true if computed signature matches allegedSignature.
	 * @throws InvalidAlgorithmParameterException
	 *            on invalid or inappropriate algorithm parameters
	 * @throws NoSuchAlgorithmException
	 *            on invalid algorithm
	 * @throws InvalidKeyException
	 *            if key is not RSA or DSA.
	 * @throws SignatureException
	 *            on signature error
	 */
	public static boolean verify(final PublicKey publicKey, final byte[] data,
			final String allegedSignature, final Version version)
			throws InvalidAlgorithmParameterException, InvalidKeyException,
			NoSuchAlgorithmException, SignatureException {
		final Signature signature = getSignature(publicKey, version);
		signature.initVerify(publicKey);
		configureSignature(publicKey, version, signature);
		signature.update(data);
		return signature.verify(Base64.getDecoder().decode(allegedSignature));
	}

	/**
	 * A convenience method to encrypt a byte array.
	 *
	 * @param key
	 *            a key that can be used to encrypt.
	 * @param toEncrypt
	 *            the data to encrypt.
	 * @return encrypted byte array.
	 * @throws InvalidKeyException
	 *            if key is not RSA or DSA.
	 * @throws NoSuchAlgorithmException
	 *            on invalid algorithm
	 * @throws NoSuchPaddingException
	 *            on invalid padding
	 * @throws IllegalArgumentException
	 *            on illegal args passed to function
	 * @throws IOException
	 *            on IO error
	 */
	public static byte[] encrypt(final Key key, final byte[] toEncrypt)
			throws InvalidKeyException, NoSuchAlgorithmException,
			NoSuchPaddingException, IllegalArgumentException, IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		processCipherStream(getEncryptCipher(key),
				StreamUtils.getInputStream(toEncrypt), baos);
		return baos.toByteArray();
	}

	/**
	 * A convenience method to decrypt a byte array.
	 *
	 * @param key
	 *            a key that can be used to decrypt.
	 * @param toDecrypt
	 *            the data to decrypt.
	 * @return decrypted byte array.
	 * @throws InvalidKeyException
	 *            if key is not RSA or DSA.
	 * @throws NoSuchAlgorithmException
	 *            on invalid algorithm
	 * @throws NoSuchPaddingException
	 *            on invalid padding
	 * @throws IllegalArgumentException
	 *            on illegal args passed to function
	 * @throws IOException
	 *            on IO error
	 */
	public static byte[] decrypt(final Key key, final byte[] toDecrypt)
			throws InvalidKeyException, NoSuchAlgorithmException,
			NoSuchPaddingException, IllegalArgumentException, IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		processCipherStream(getDecryptCipher(key),
				StreamUtils.getInputStream(toDecrypt), baos);
		return baos.toByteArray();
	}

	/**
	 * Generate a new symmetric encryption key.
	 *
	 * @param bits
	 *            how many bits. This should be AES_128 or AES256.
	 * @return generated AES key.
	 * @throws NoSuchAlgorithmException
	 *            on invalid algorithm
	 */
	public static Key generateAESKey(final int bits)
			throws NoSuchAlgorithmException {
		KeyGenerator gen = KeyGenerator.getInstance(AES_ALGORITHM);
		gen.init(bits);
		return gen.generateKey();
	}

	/**
	 * Generate a new asymmetric encryption key pair.
	 *
	 * @param bits
	 *            how many bits. Must be a valid RSA size.
	 * @return generated RSA key pair.
	 * @throws NoSuchAlgorithmException
	 *            on invalid algorithm
	 */
	public static KeyPair generateRSAKeyPair(final int bits)
			throws NoSuchAlgorithmException {
		KeyPairGenerator gen = KeyPairGenerator.getInstance(RSA_ALGORITHM);
		gen.initialize(bits);
		return gen.generateKeyPair();
	}

	/**
	 * Generate a new asymmetric signature key pair.
	 *
	 * @param bits
	 *            how many bits. Must be a valid DSA size.
	 * @return generated DSA key pair.
	 * @throws NoSuchAlgorithmException
	 *            on invalid algorithm
	 */
	public static KeyPair generateDSAKeyPair(final int bits)
			throws NoSuchAlgorithmException {
		KeyPairGenerator gen = KeyPairGenerator.getInstance(DSA_ALGORITHM);
		gen.initialize(bits);
		return gen.generateKeyPair();
	}

	/**
	 * Read a X509 encoded certificate. May be DER or PEM encoded.
	 *
	 * @param bytes
	 *            the certificate data as a byte array.
	 * @return parsed certificate.
	 * @throws CertificateException
	 *            on certificate issue
	 * @throws IOException
	 *            on IO error
	 */
	public static Certificate readCertificate(final byte[] bytes)
			throws CertificateException, IOException {
		byte[] data = bytes;
		if (((char) data[0]) == '-') {
			data = convertPEMToDER(new String(data));
		}
		Certificate certificate = CertificateFactory.getInstance("X.509")
				.generateCertificate(new ByteArrayInputStream(data));
		return certificate;
	}

	/**
	 * Read a X509 encoded public key. May be DER or PEM encoded.
	 *
	 * @param bytes
	 *            the key data as a byte array.
	 * @return parsed public key.
	 * @throws IOException
	 *            on IO error
	 * @throws NoSuchAlgorithmException
	 *            on invalid algorithm
	 */
	public static PublicKey readPublicKey(final byte[] bytes)
			throws IOException, NoSuchAlgorithmException {
		byte[] data = bytes;
		// decode from PEM format
		if (((char) data[0]) == '-') {
			data = convertPEMToDER(new String(data));
		}
		X509EncodedKeySpec spec = new X509EncodedKeySpec(data);

		try {
			return KeyFactory.getInstance(DSA_ALGORITHM).generatePublic(spec);
		} catch (InvalidKeySpecException e) {
			try {
				return KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(
						spec);
			} catch (InvalidKeySpecException e2) {
				// ignore
			}
		}

		return null;
	}

	/**
	 * Read a PKCS#8 encoded private key. May be DER or PEM encoded.
	 *
	 * @param bytes
	 *            the key data as a byte array.
	 * @return parsed private key.
	 * @throws IOException
	 *            on IO error
	 * @throws NoSuchAlgorithmException
	 *            on invalid algorithm
	 */
	public static PrivateKey readPrivateKey(final byte[] bytes)
			throws IOException, NoSuchAlgorithmException {
		byte[] data = bytes;
		// decode from PEM format
		if (((char) data[0]) == '-') {
			data = convertPEMToDER(new String(data));
		}
		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(data);

		try {
			return KeyFactory.getInstance(DSA_ALGORITHM).generatePrivate(spec);
		} catch (InvalidKeySpecException e) {
			try {
				return KeyFactory.getInstance(RSA_ALGORITHM).generatePrivate(
						spec);
			} catch (InvalidKeySpecException e2) {
				// ignore
			}
		}

		return null;
	}

	/**
	 * Read an OpenSSH private key from a stream.
	 *
	 * @param bytes
	 *            the byte array containing an OpenSSH private key.
	 * @param password
	 *            password if the key is encrypted.
	 * @return decoded PrivateKey.
	 * @throws IOException
	 *            on IO error
	 * @throws InvalidKeySpecException
	 *            when key has invalid specifications
	 * @throws NoSuchAlgorithmException
	 *            on invalid algorithm
	 */
	public static PrivateKey readOpenSSHPrivateKey(final byte[] bytes,
			final String password) throws IOException,
			NoSuchAlgorithmException, InvalidKeySpecException {
		PrivateKey key = null;

		// this returns an ethz DSAPrivateKey or RSAPrivateKey
		Object obj = PEMDecoder.decode(new String(bytes).toCharArray(),
				password);

		if (obj instanceof ch.ethz.ssh2.signature.DSAPrivateKey) {
			ch.ethz.ssh2.signature.DSAPrivateKey ethzDSAKey = (ch.ethz.ssh2.signature.DSAPrivateKey) obj;
			key = (DSAPrivateKey) KeyFactory.getInstance("DSA")
					.generatePrivate(
							new DSAPrivateKeySpec(ethzDSAKey.getX(), ethzDSAKey
									.getP(), ethzDSAKey.getQ(), ethzDSAKey
									.getG()));
		} else if (obj instanceof ch.ethz.ssh2.signature.RSAPrivateKey) {
			ch.ethz.ssh2.signature.RSAPrivateKey ethzRSAKey = (ch.ethz.ssh2.signature.RSAPrivateKey) obj;
			key = (RSAPrivateKey) KeyFactory.getInstance("RSA")
					.generatePrivate(
							new RSAPrivateKeySpec(ethzRSAKey.getN(), ethzRSAKey
									.getD()));
		}

		return key;
	}

	/**
	 * Read an OpenSSH PublicKey from a stream.
	 *
	 * @param bytes
	 *            bytes to read.
	 * @return a publicKey
	 * @throws IOException
	 *            on IO error
	 * @throws NoSuchAlgorithmException
	 *            on invalid algorithm
	 * @throws InvalidKeySpecException
	 *            when key has invalid specifications
	 */
	public static PublicKey readOpenSSHPublicKey(final byte[] bytes)
			throws IOException, InvalidKeySpecException,
			NoSuchAlgorithmException {

		// format is <type><space><base64data><space><comment>
		String[] line = new String(bytes).trim().split(" ", 3);
		String type = line[0];
		String content = line[1];
		// String comment = line[2];

		ByteBuffer buf = ByteBuffer.wrap(Base64.getDecoder().decode(content));

		// format of decoded content is: <type><keyparams>
		// where type and each param is a DER string
		String decodedType = new String(readDERString(buf));
		if (!decodedType.equals(type)) {
			throw new IllegalArgumentException("expected " + type + ", got "
					+ decodedType);
		}
		if (type.equals("ssh-dss")) {
			// dsa key params are p, q, g, y
			BigInteger p = new BigInteger(readDERString(buf));
			BigInteger q = new BigInteger(readDERString(buf));
			BigInteger g = new BigInteger(readDERString(buf));
			BigInteger y = new BigInteger(readDERString(buf));
			return KeyFactory.getInstance(DSA_ALGORITHM).generatePublic(
					new DSAPublicKeySpec(y, p, q, g));
		} else if (type.equals("ssh-rsa")) {
			// rsa key params are e, y
			BigInteger e = new BigInteger(readDERString(buf));
			BigInteger y = new BigInteger(readDERString(buf));
			return KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(
					new RSAPublicKeySpec(y, e));
		} else {
			throw new InvalidKeySpecException("Unknown key type '" + type + "'");
		}
	}

	/**
	 * This method reads a DER encoded byte string from a ByteBuffer.
	 *
	 * A DER encoded string has
	 *
	 * length = 4 bytes big-endian integer<br>
	 * string = length bytes
	 *
	 * @param buf
	 *            buffer containing DER encoded bytes.
	 * @return bytes the decoded bytes.
	 */
	public static byte[] readDERString(ByteBuffer buf) {
		int length = buf.getInt();
		if (length > 8192) {
			throw new IllegalArgumentException("DER String Length " + length
					+ " > 8192");
		}
		byte[] bytes = new byte[length];
		buf.get(bytes);
		return bytes;
	}

	/**
	 * Read a PEM format.
	 *
	 * This does not currently support encrypted PEM formats.
	 *
	 * @param string
	 *            string containing PEM formatted data.
	 * @return DER formatted data.
	 * @throws IOException
	 *            on IO error
	 */
	public static byte[] convertPEMToDER(final String string)
			throws IOException {
		List<String> lines = StringUtils.split(string, "\n");
		String header = lines.remove(0);
		String footer = lines.remove(lines.size() - 1);
		String type;

		if (header.startsWith("-----BEGIN ") && header.endsWith("-----")) {
			type = header;
			type = type.replace("-----BEGIN ", "");
			type = type.replace("-----", "");

			if (type.contains("ENCRYPTED")) {
				throw new IllegalArgumentException(
						"Encrypted keys are not supported.");
			}

			if (footer.equals("-----END " + type + "-----")) {
				// expected match
				return Base64.getMimeDecoder().decode(
						StringUtils.join(new LinkedList<Object>(lines), "\n"));
			} else {
				throw new IllegalArgumentException("Unexpected PEM footer '"
						+ footer + "'");
			}
		} else {
			throw new IllegalArgumentException("Unexpected PEM header '"
					+ header + "'");
		}
	}

}

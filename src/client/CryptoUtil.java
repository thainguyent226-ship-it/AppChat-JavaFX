package src.client;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Ma hoa/giai ma AES-128 (CBC + PKCS5Padding) cho toan bo du lieu trao doi giua Client va Server.
 * Ca 2 phia dung chung 1 khoa bi mat (SECRET_KEY) - phu hop pham vi do an mon hoc.
 * Moi lan ma hoa sinh 1 IV ngau nhien moi, ghep IV + du lieu ma hoa roi encode Base64
 * de van gui duoc qua Socket dang text (PrintWriter.println / BufferedReader.readLine).
 */
public class CryptoUtil {
    private static final String SECRET_KEY = "AppChat2026Secret"; // dung dung 16 hoac 32 ky tu
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;

    private static SecretKeySpec getKeySpec() {
        byte[] keyBytes = new byte[16];
        byte[] rawKey = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(rawKey, 0, keyBytes, 0, Math.min(rawKey.length, keyBytes.length));
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static String encrypt(String plainText) {
        try {
            byte[] ivBytes = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(ivBytes);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getKeySpec(), new IvParameterSpec(ivBytes));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[ivBytes.length + encrypted.length];
            System.arraycopy(ivBytes, 0, combined, 0, ivBytes.length);
            System.arraycopy(encrypted, 0, combined, ivBytes.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            System.out.println("[CRYPTO ERROR] Loi ma hoa: " + e.getMessage());
            return plainText;
        }
    }

    public static String decrypt(String encryptedBase64) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);
            byte[] ivBytes = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, ivBytes, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getKeySpec(), new IvParameterSpec(ivBytes));
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[CRYPTO ERROR] Loi giai ma: " + e.getMessage());
            return encryptedBase64;
        }
    }
}
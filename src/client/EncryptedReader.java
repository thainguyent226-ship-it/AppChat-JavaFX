package src.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * BufferedReader tuy bien: moi lan goi readLine() se doc dong du lieu da ma hoa tu Socket,
 * roi TU DONG giai ma AES truoc khi tra ve. Cac cho goi in.readLine() trong toan bo code
 * khong can sua gi - chi can doi noi khoi tao thanh lop nay.
 */
public class EncryptedReader extends BufferedReader {
    public EncryptedReader(InputStream in) {
        super(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    @Override
    public String readLine() throws IOException {
        String line = super.readLine();
        if (line == null) return null;
        return CryptoUtil.decrypt(line);
    }
}
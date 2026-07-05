package src.client;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * PrintWriter tuy bien: moi lan goi println(String) se TU DONG ma hoa AES noi dung
 * truoc khi thuc su ghi ra Socket. Cac cho goi out.println("...") trong toan bo
 * code (Server lan Client) khong can sua gi ca - chi can doi noi khoi tao thanh lop nay.
 */
public class EncryptedWriter extends PrintWriter {
    public EncryptedWriter(OutputStream out) {
        super(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
    }

    @Override
    public void println(String x) {
        super.println(CryptoUtil.encrypt(x == null ? "" : x));
    }
}
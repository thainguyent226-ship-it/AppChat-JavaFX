package src.client;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;


public class EncryptedWriter extends PrintWriter {
    public EncryptedWriter(OutputStream out) {
        super(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
    }

    @Override
    public void println(String x) {
        super.println(CryptoUtil.encrypt(x == null ? "" : x));
    }
}
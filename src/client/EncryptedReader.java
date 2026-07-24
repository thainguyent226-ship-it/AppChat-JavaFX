package src.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;


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
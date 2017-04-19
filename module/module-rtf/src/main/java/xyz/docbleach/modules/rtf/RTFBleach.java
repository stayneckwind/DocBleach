package xyz.docbleach.modules.rtf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.docbleach.api.BleachException;
import xyz.docbleach.api.BleachSession;
import xyz.docbleach.api.bleach.Bleach;
import xyz.docbleach.api.threats.Threat;
import xyz.docbleach.api.threats.ThreatAction;
import xyz.docbleach.api.threats.ThreatSeverity;
import xyz.docbleach.api.threats.ThreatType;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * RTF files are not that common nowadays, but it is a simple format and is a perfect bleach
 * example: short but complete <p> An RTF file looks like a bunch of <code>{\tag content}</code>
 * elements, where tag describes what kind of thing is there. For instance, {\p Hello, World}
 * creates a paragraph with the text "Hello, World" inside. Easy. To my knowledge, apart from parser
 * exploits, the only way to have a malicious software into an RTF is the obj tag: it embeds an OLE2
 * binary content (think, VBA macro, but unreadable) that may be an executable, an image, ... To
 * sanitize the RTF, we just replace every instance of "\obj" with "\0bj" (a zero instead of the
 * letter o). An RTF parser will skip that tag (unknown), and the exploit will likely fail.
 */
public class RTFBleach implements Bleach {

    private static final Logger LOGGER = LoggerFactory.getLogger(RTFBleach.class);
    private static final byte[] rtfHeader = new byte[]{123, 92, 114, 116, 102};

    @Override
    public boolean handlesMagic(InputStream stream) {
        byte[] header = new byte[5];
        stream.mark(0);
        int length;

        try {
            length = stream.read(header);
            stream.reset();
        } catch (IOException e) {
            LOGGER.warn("An exception occured", e);
            return false;
        }

        return length == 5 && Arrays.equals(rtfHeader, header);
    }

    @Override
    public String getName() {
        return "RTF Bleach";
    }

    @Override
    public void sanitize(InputStream inputStream, OutputStream outFile, BleachSession session) throws BleachException {
        LOGGER.debug("This is a RTF file, I'll rename object to 0bject, and hope for it to be enough.");

        Charset cs = Charset.isSupported("UTF-8") ? Charset.forName("UTF-8") : Charset.defaultCharset();

        try (
                BufferedReader inStream = new BufferedReader(new InputStreamReader(inputStream, cs));
                BufferedWriter outStream = new BufferedWriter(new OutputStreamWriter(outFile, cs));
        ) {
            String l;
            while ((l = inStream.readLine()) != null) {
                if (l.toLowerCase().contains("\\obj")) {
                    LOGGER.debug("OLE Object found and removed!");

                    Threat threat = new Threat(ThreatType.BINARY_CONTENT,
                            ThreatSeverity.HIGH,
                            "?",
                            "Embedded OLE Object",
                            ThreatAction.REMOVE
                    );
                    session.recordThreat(threat);
                }
                String sanitizedLine = sanitizeLine(l);
                outStream.write(sanitizedLine);
            }
        } catch (IOException e) {
            throw new BleachException(e);
        }
    }

    private String sanitizeLine(String l) {
        // "\*" tells the parser to "ignore tags it doesn't know about".
        return l.replace("\\obj", "\\*\\0bj").replace("\\*\\*", "\\*");
    }
}
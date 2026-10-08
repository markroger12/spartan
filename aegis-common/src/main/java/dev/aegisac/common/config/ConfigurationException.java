package dev.aegisac.common.config;
/** Safe diagnostic: never includes raw YAML scalar values, which may contain secrets. */
public final class ConfigurationException extends Exception {
    private static final long serialVersionUID = 1L;
    private final String file;
    private final String path;
    private final int line;
    public ConfigurationException(String file, String path, int line, String reason) {
        super(file + (line > 0 ? ":" + line : "") + " [" + path + "]: " + reason);
        this.file = file; this.path = path; this.line = line;
    }
    public String file() { return file; }
    public String path() { return path; }
    public int line() { return line; }
}
